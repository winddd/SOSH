package compile.v2.compilers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import compile.v2.inputs.GraphInputs;
import compile.v2.modules.GenDepModule;
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
 * Compiler for Read Uncommitted (RU) isolation level verification.
 *
 * <p>
 * Read Uncommitted is the weakest standard isolation level, providing minimal
 * guarantees.
 * Under RU, transactions can read uncommitted (dirty) data from other
 * transactions, leading
 * to various anomalies including dirty reads, non-repeatable reads, and
 * phantoms.
 * </p>
 *
 * <h2>Read Uncommitted Semantics</h2>
 * <p>
 * RU provides only one guarantee:
 * </p>
 * <ul>
 * <li><b>No Dirty Writes (G0-write)</b>: Concurrent writes to the same key are
 * ordered
 * by version/commit order. This prevents lost updates on individual keys.</li>
 * </ul>
 *
 * <p>
 * RU <b>permits</b> all other anomalies:
 * </p>
 * <ul>
 * <li><b>Dirty Reads (G0)</b>: Transactions can read uncommitted writes</li>
 * <li><b>Non-Repeatable Reads (G1b)</b>: Re-reading a key may return different
 * values</li>
 * <li><b>Phantom Reads (G1c)</b>: Range query results can change during
 * execution</li>
 * <li><b>Write Skew (G2)</b>: Integrity constraints can be violated</li>
 * <li><b>Read Skew</b>: Inconsistent snapshots can be observed</li>
 * </ul>
 *
 * <h2>Dependency Graph Construction</h2>
 * <p>
 * The RU compiler builds the most minimal dependency graph, containing only:
 * </p>
 * <ul>
 * <li><b>Write-Write (WW)</b> edges: Enforces version order on conflicting
 * writes</li>
 * </ul>
 *
 * <p>
 * Notably, RU excludes both:
 * </p>
 * <ul>
 * <li>Write-Read (WR) edges - permits dirty reads</li>
 * <li>Anti-dependency (RW) edges - permits read/write skew</li>
 * </ul>
 *
 * <h2>Edge Filtering</h2>
 * <p>
 * Uses {@link EdgeType#isRuEdgeType(EdgeType)} to filter edges, accepting only
 * WW
 * dependencies. All read-related dependencies are excluded.
 * </p>
 *
 * <h2>Practical Use</h2>
 * <p>
 * While RU offers poor isolation guarantees, it has minimal overhead and is
 * occasionally
 * used for read-only analytical queries where approximate results are
 * acceptable. Most
 * production systems default to at least Read Committed.
 * </p>
 *
 * <h2>Theoretical Foundation</h2>
 * <p>
 * In Adya's anomaly hierarchy, RU is characterized solely by the absence of
 * G0-write.
 * The dependency graph for RU is essentially a write serialization graph that
 * ignores reads.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Config cfg = Config.builder().runMode(MODE.B_RU).build();
 * RuCompiler compiler = new RuCompiler(cfg);
 * List<InCompleteGraph> graphs = compiler.compile(asg);
 * // Cycle detection checks only for lost updates (G0-write)
 * }</pre>
 *
 * @see GraphCompiler
 * @see RcCompiler
 * @see util.enumtypes.MODE#B_RU
 * @since 2.0
 */
public class RuCompiler implements GraphCompiler {
  private final Profiler profiler;
  private final MODE mode;
  private final Config cfg;

  /**
   * Constructs a Read Uncommitted compiler with the given configuration.
   *
   * @param cfg the configuration object containing verification settings
   */
  public RuCompiler(Config cfg) {
    this.profiler = Profiler.getInstance();
    this.mode = cfg.RUNMODE;
    this.cfg = cfg;
  }

  /**
   * Compiles the transaction history into an RU dependency graph.
   *
   * <p>
   * The compilation process:
   * </p>
   * <ol>
   * <li>Creates a {@link BoomslangGraph} with transaction-level nodes</li>
   * <li>Filters edges to include only RU-relevant types (WW only)</li>
   * <li>Applies {@link GenWriteDepModule} to generate write-write
   * dependencies</li>
   * <li>Marks the graph type as {@link AdyaGraphType#DSG}</li>
   * </ol>
   *
   * <p>
   * No read dependency modules are applied, as RU permits dirty reads.
   * </p>
   *
   * @param inputs the transaction metadata and pre-computed dependencies
   * @return a singleton list containing the RU dependency graph
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
      if (EdgeType.isRuEdgeType(edge.edgeType)) {
        graph.addEdge(edge);
      }
    }

    GenDepModule writeModule = new GenWriteDepModule(profiler, mode, cfg);
    var modules = new ArrayList<GenDepModule>();
    modules.add(writeModule);
    InCompleteGraph current = ModuleUtility.generate(inputs, graph, modules);
    current.setGraphType(AdyaGraphType.DSG);
    profiler.endTick("graphcompile");
    return List.of(current);
  }
}
