package compile.v2.compilers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import compile.v2.inputs.GraphInputs;
import compile.v2.modules.GenAntiDepModule;
import compile.v2.modules.GenDepModule;
import compile.v2.modules.GenReadDepModule;
import compile.v2.modules.GenSessionDepModule;
import compile.v2.modules.GenWriteDepModule;
import compile.v2.modules.ModuleUtility;
import graphs.edges.EdgeType;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import util.Config;
import util.Profiler;
import util.enumtypes.AdyaGraphType;
import util.enumtypes.MODE;

/**
 * Compiler for Predicate Lock 2.99 (PL-2.99 / Repeatable Read) isolation level
 * verification.
 *
 * <p>
 * PL-2.99, also known as Repeatable Read in ANSI SQL terminology, provides a
 * middle ground
 * between Snapshot Isolation and full Serializability. It prevents lost updates
 * and ensures
 * that individual item reads are repeatable (re-reading a key yields the same
 * value), but
 * permits phantoms in range queries.
 * </p>
 *
 * <h2>PL-2.99 Semantics</h2>
 * <p>
 * PL-2.99 provides the following guarantees:
 * </p>
 * <ul>
 * <li><b>No Dirty Reads (G0)</b>: Cannot read uncommitted writes</li>
 * <li><b>No Dirty Writes (G0-write)</b>: Writes are ordered by commit
 * order</li>
 * <li><b>No Lost Updates (G1a)</b>: Concurrent updates to the same item are
 * properly ordered</li>
 * <li><b>Repeatable Reads for Items (G1b-item)</b>: Re-reading a specific key
 * returns the same value</li>
 * <li><b>No Write Skew on Items (G2-item)</b>: Write skew is prevented for
 * single-key conflicts</li>
 * </ul>
 *
 * <p>
 * PL-2.99 <b>permits</b> the following phenomena:
 * </p>
 * <ul>
 * <li><b>Phantoms (G1c / A3)</b>: Range query results can change (new rows can
 * appear/disappear)</li>
 * <li><b>Write Skew on Predicates (G2-predicate)</b>: Constraint violations
 * involving range predicates</li>
 * <li><b>Predicate Anti-Dependencies (PRW)</b>: Read-write conflicts on range
 * predicates are allowed</li>
 * </ul>
 *
 * <h2>Dependency Graph Construction</h2>
 * <p>
 * The PL-2.99 graph includes item-level dependencies but excludes predicate
 * anti-dependencies:
 * </p>
 * <ul>
 * <li><b>Write-Read (WR)</b> edges: Both item and predicate writes to
 * reads</li>
 * <li><b>Write-Write (WW)</b> edges: Version order on both items and
 * predicates</li>
 * <li><b>Read-Write (RW)</b> edges: <i>Item-level only</i> - prevents item
 * write skew</li>
 * <li><b>Predicate Read-Write (PRW)</b> edges: <i>Excluded</i> - permits
 * phantom reads</li>
 * <li><b>Session Order (SO)</b>: Intra-session ordering if configured</li>
 * </ul>
 *
 * <h2>Edge Filtering Logic</h2>
 * <p>
 * The {@code isPl299Edge} method accepts all edge types <i>except</i> PRW
 * (predicate anti-dependencies).
 * This selective exclusion is what distinguishes PL-2.99 from full
 * serializability:
 * </p>
 *
 * <pre>{@code
 * isPl299Edge(WR)  → true   // Item and predicate WR allowed
 * isPl299Edge(PWR) → true   // Predicate WR allowed
 * isPl299Edge(RW)  → true   // Item anti-deps required
 * isPl299Edge(PRW) → false  // Predicate anti-deps excluded (phantoms allowed)
 * isPl299Edge(WW)  → true   // All WW edges required
 * }</pre>
 *
 * <h2>Comparison to Other Levels</h2>
 * <ul>
 * <li><b>vs RC</b>: PL-2.99 adds item-level anti-dependencies (RW) to prevent
 * write skew on items</li>
 * <li><b>vs SI</b>: SI prevents all write skew; PL-2.99 only prevents
 * item-level write skew</li>
 * <li><b>vs SER</b>: SER includes PRW edges; PL-2.99 excludes them to permit
 * phantoms</li>
 * </ul>
 *
 * <h2>Theoretical Foundation</h2>
 * <p>
 * Based on the predicate lock hierarchy from Berenson et al.'s "A Critique of
 * ANSI SQL Isolation
 * Levels" (SIGMOD 1995). The ".99" suffix indicates it's almost PL-3
 * (serializability) but stops
 * short of phantom prevention.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Config cfg = Config.builder().runMode(MODE.B_PL299).build();
 * Pl299Compiler compiler = new Pl299Compiler(cfg);
 * List<InCompleteGraph> graphs = compiler.compile(asg);
 * // Detects item-level anomalies but permits phantoms
 * }</pre>
 *
 * @see GraphCompiler
 * @see SerCompiler
 * @see util.enumtypes.MODE#B_PL299
 * @since 2.0
 */
public class Pl299Compiler implements GraphCompiler {
  private final Profiler profiler;
  private final MODE mode;
  private final Config cfg;

  /**
   * Constructs a PL-2.99 compiler with the given configuration.
   *
   * @param cfg the configuration object containing verification settings
   */
  public Pl299Compiler(Config cfg) {
    this.profiler = Profiler.getInstance();
    this.mode = cfg.RUNMODE;
    this.cfg = cfg;
  }

  /**
   * Compiles the transaction history into a PL-2.99 dependency graph.
   *
   * <p>
   * The compilation process:
   * </p>
   * <ol>
   * <li>Creates a {@link BoomslangGraph} with transaction-level nodes</li>
   * <li>Filters edges to exclude PRW (predicate anti-dependencies)</li>
   * <li>Applies all standard dependency modules (read, write, anti, session)</li>
   * <li>Marks the graph type as {@link AdyaGraphType#DSG}</li>
   * </ol>
   *
   * @param inputs the transaction metadata and pre-computed dependencies
   * @return a singleton list containing the PL-2.99 dependency graph
   */
  @Override
  public List<InCompleteGraph> compile(GraphInputs inputs) {
    profiler.startTick("graphcompile");
    var txnIds = new HashSet<GraphNodeId>();
    for (int i = 0; i < inputs.numTxns(); i++) {
      txnIds.add(GraphNodeId.txn(i));
    }

    BoomslangGraph graph = new BoomslangGraph(txnIds, cfg);
    for (var edge : inputs.edges()) {
      if (isPl299Edge(edge.edgeType)) {
        graph.addEdge(edge);
      }
    }

    var current = buildDsg(inputs, graph);
    profiler.endTick("graphcompile");
    return List.of(current);
  }

  /**
   * Builds the DSG by applying dependency modules to the filtered graph.
   *
   * @param inputs the transaction metadata and dependencies
   * @param graph  the base graph with PRW edges already filtered out
   * @return the completed PL-2.99 dependency graph
   */
  private InCompleteGraph buildDsg(GraphInputs inputs, BoomslangGraph graph) {
    var modules = new ArrayList<GenDepModule>();
    modules.add(new GenReadDepModule(profiler, mode, cfg));
    modules.add(new GenWriteDepModule(profiler, mode, cfg));
    modules.add(new GenAntiDepModule(profiler, mode, cfg));
    modules.add(new GenSessionDepModule(profiler, mode, cfg));

    InCompleteGraph current = ModuleUtility.generate(inputs, graph, modules);
    current.setGraphType(AdyaGraphType.DSG);
    return current;
  }

  /**
   * Determines whether an edge type should be included in the PL-2.99 graph.
   *
   * <p>
   * PL-2.99 accepts all edge types except predicate anti-dependencies (PRW).
   * This filter is what permits phantom reads while preventing item-level
   * anomalies.
   * </p>
   *
   * @param type the edge type to check
   * @return true if the edge should be included, false if it should be filtered
   *         out
   */
  private boolean isPl299Edge(EdgeType type) {
    return type != EdgeType.PRW;
  }
}
