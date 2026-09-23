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
import graphs.edges.EdgeType;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import util.Config;
import util.Profiler;
import util.enumtypes.AdyaGraphType;
import util.enumtypes.MODE;

/**
 * Compiler for Strict Serializability isolation level verification.
 *
 * <p>
 * Strict Serializability (also called strong one-copy serializability or
 * linearizability
 * for transactions) extends serializability by requiring that the serialization
 * order respect
 * real-time ordering. If transaction T<sub>i</sub> commits before T<sub>j</sub>
 * begins in
 * real time, then T<sub>i</sub> must appear before T<sub>j</sub> in any
 * equivalent serial
 * execution.
 * </p>
 *
 * <h2>Strict Serializability Semantics</h2>
 * <p>
 * Strict Serializability provides two guarantees:
 * </p>
 * <ul>
 * <li><b>Serializability</b>: Execution is equivalent to some serial schedule
 * (as in SER)</li>
 * <li><b>Real-Time Ordering</b>: The serialization order respects commit-before
 * relationships
 * in real time</li>
 * </ul>
 *
 * <p>
 * This means that if a transaction commits and then a client starts a new
 * transaction, the
 * second transaction <i>must</i> observe all effects of the first. This
 * eliminates "time travel"
 * anomalies where committed data appears to vanish.
 * </p>
 *
 * <h2>Dependency Graph Construction</h2>
 * <p>
 * The Strict Serialization Graph extends the DSG with additional constraints:
 * </p>
 * <ul>
 * <li><b>Write-Read (WR)</b> edges: As in SER</li>
 * <li><b>Write-Write (WW)</b> edges: As in SER</li>
 * <li><b>Read-Write (RW)</b> anti-dependencies: As in SER</li>
 * <li><b>Session Order (SO)</b>: Intra-session constraints</li>
 * <li><b>Real-Time (RT)</b> edges: T<sub>i</sub> → T<sub>j</sub> if
 * T<sub>i</sub> commits
 * before T<sub>j</sub> begins in wall-clock time</li>
 * <li><b>Expected Order (EO)</b> edges: Optional user-specified ordering
 * constraints</li>
 * </ul>
 *
 * <h2>Real-Time Edge Encoding</h2>
 * <p>
 * Real-time edges are identified by {@link EdgeType#isSSerEdgeType(EdgeType)},
 * which
 * accepts all SER edge types plus real-time ordering edges. These edges are
 * typically
 * derived from commit and begin timestamps in the transaction log.
 * </p>
 *
 * <h2>Expected Order</h2>
 * <p>
 * When {@code cfg.EXPECTED_ORDER} is enabled, the compiler also incorporates
 * EXPECTED
 * edges that represent application-level ordering requirements. This allows
 * testing whether
 * the observed history respects externally imposed constraints.
 * </p>
 *
 * <h2>Theoretical Foundation</h2>
 * <p>
 * Strict Serializability is formalized in classical concurrency theory (e.g.,
 * Bernstein
 * and Goodman's "Concurrency Control in Database Systems"). The real-time
 * constraint is
 * essential for systems requiring strong external consistency, such as Google
 * Spanner.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Config cfg = Config.builder()
 *     .runMode(MODE.B_SSER)
 *     .expectedOrder(true)
 *     .build();
 * StrictSerCompiler compiler = new StrictSerCompiler(cfg);
 * List<InCompleteGraph> graphs = compiler.compile(asg);
 * // Cycles indicate violations of strict serializability
 * }</pre>
 *
 * @see GraphCompiler
 * @see SerCompiler
 * @see util.enumtypes.MODE#B_SSER
 * @since 2.0
 */
public class StrictSerCompiler implements GraphCompiler {
  private final Profiler profiler;
  private final MODE mode;
  private final Config cfg;

  /**
   * Constructs a Strict Serializability compiler with the given configuration.
   *
   * @param cfg the configuration object containing verification settings
   */
  public StrictSerCompiler(Config cfg) {
    this.profiler = Profiler.getInstance();
    this.mode = cfg.RUNMODE;
    this.cfg = cfg;
  }

  /**
   * Compiles the transaction history into a Strict Serialization Graph.
   *
   * <p>
   * The compilation process:
   * </p>
   * <ol>
   * <li>Creates a {@link BoomslangGraph} with transaction-level nodes</li>
   * <li>Filters edges to include SSER types (all SER types + real-time + optional
   * expected order)</li>
   * <li>Applies all standard dependency modules (read, write, anti, session)</li>
   * <li>Marks the graph type as {@link AdyaGraphType#DSG}</li>
   * </ol>
   *
   * @param inputs the transaction metadata and pre-computed dependencies
   * @return a singleton list containing the Strict Serialization Graph
   */
  @Override
  public List<InCompleteGraph> compile(GraphInputs inputs) {
    profiler.startTick("graphcompile");
    var nodeIds = new HashSet<GraphNodeId>();
    for (int i = 0; i < inputs.numTxns(); i++) {
      nodeIds.add(GraphNodeId.txn(i));
    }

    BoomslangGraph graph = new BoomslangGraph(nodeIds, cfg);
    for (var edge : inputs.edges()) {
      if (EdgeType.isSSerEdgeType(edge.edgeType)
          || (cfg.EXPECTED_ORDER && edge.edgeType == EdgeType.EXPECTED)) {
        graph.addEdge(edge);
      }
    }

    var modules = new ArrayList<GenDepModule>();
    modules.add(new GenReadDepModule(profiler, mode, cfg));
    modules.add(new GenWriteDepModule(profiler, mode, cfg));
    modules.add(new GenAntiDepModule(profiler, mode, cfg));
    modules.add(new GenSessionDepModule(profiler, mode, cfg));

    InCompleteGraph current = graph;
    for (var module : modules) {
      current = module.generate(inputs, current);
    }
    current.setGraphType(AdyaGraphType.DSG);
    profiler.endTick("graphcompile");
    return List.of(current);
  }
}
