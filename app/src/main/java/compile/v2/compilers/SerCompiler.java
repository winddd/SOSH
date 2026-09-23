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
 * Compiler for Serializability (SER) isolation level verification.
 *
 * <p>
 * Serializability is the gold standard of transaction isolation, guaranteeing
 * that the
 * execution of concurrent transactions is equivalent to some serial
 * (one-at-a-time) execution.
 * This compiler constructs a Direct Serialization Graph (DSG) whose acyclicity
 * proves
 * serializability.
 * </p>
 *
 * <h2>Direct Serialization Graph (DSG)</h2>
 * <p>
 * The DSG is Adya's characterization of serializability based on direct
 * dependencies.
 * A history is serializable if and only if the DSG is acyclic. The DSG
 * includes:
 * </p>
 * <ul>
 * <li><b>Write-Read (WR)</b> edges: T<sub>i</sub> → T<sub>j</sub> if
 * T<sub>j</sub> reads
 * a value written by T<sub>i</sub></li>
 * <li><b>Write-Write (WW)</b> edges: T<sub>i</sub> → T<sub>j</sub> if both
 * write the same key
 * and T<sub>i</sub> commits before T<sub>j</sub> in version order</li>
 * <li><b>Read-Write (RW)</b> anti-dependencies: T<sub>i</sub> → T<sub>j</sub>
 * if T<sub>i</sub>
 * reads a key that T<sub>j</sub> later overwrites</li>
 * <li><b>Session Order (SO)</b>: Intra-session ordering constraints if
 * enabled</li>
 * </ul>
 *
 * <h2>Modular Construction</h2>
 * <p>
 * The SerCompiler implements the v2 modular architecture by composing
 * dependency modules:
 * </p>
 *
 * <pre>{@code
 * modules = [
 *   GenReadDepModule,     // Generates WR edges
 *   GenWriteDepModule,    // Generates WW edges
 *   GenAntiDepModule,     // Generates RW anti-dependencies
 *   GenSessionDepModule   // Adds session order constraints
 * ]
 * }</pre>
 *
 * <h2>Edge Filtering</h2>
 * <p>
 * The compiler filters edges by type using
 * {@link EdgeType#isSerEdgeType(EdgeType)}, which
 * accepts WR, WW, RW, CB (commit-before), and session-order edges. Predicate
 * edges (PWR, PRW)
 * are excluded from the base SER graph.
 * </p>
 *
 * <h2>Theoretical Foundation</h2>
 * <p>
 * This implementation is based on Adya's PhD thesis "Weak Consistency: A
 * Generalized Theory
 * and Optimistic Implementations for Distributed Transactions" (1999),
 * specifically the DSG
 * acyclicity theorem (Theorem 3.1).
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Config cfg = Config.builder().runMode(MODE.B_SER).build();
 * SerCompiler compiler = new SerCompiler(cfg);
 * ASG asg = ASGConstructor.build(history, cfg);
 * List<InCompleteGraph> graphs = compiler.compile(asg);
 * assert graphs.size() == 1;
 * assert graphs.get(0).getGraphType() == AdyaGraphType.DSG;
 * }</pre>
 *
 * @see GraphCompiler
 * @see graphs.graphs.BoomslangGraph
 * @see util.enumtypes.AdyaGraphType#DSG
 * @since 2.0
 */
public class SerCompiler implements GraphCompiler {
  private final Profiler profiler;
  private final MODE mode;
  private final Config cfg;

  /**
   * Constructs a Serializability compiler with the given configuration.
   *
   * @param cfg the configuration object containing verification settings
   */
  public SerCompiler(Config cfg) {
    this.profiler = Profiler.getInstance();
    this.mode = cfg.RUNMODE;
    this.cfg = cfg;
  }

  /**
   * Compiles the transaction history into a Direct Serialization Graph (DSG).
   *
   * <p>
   * The compilation process:
   * </p>
   * <ol>
   * <li>Creates a {@link BoomslangGraph} with transaction-level nodes</li>
   * <li>Filters input edges to include only SER-relevant types (WR, WW, RW, CB,
   * SO)</li>
   * <li>Applies dependency generation modules to build the complete DSG</li>
   * <li>Marks the graph type as {@link AdyaGraphType#DSG}</li>
   * </ol>
   *
   * @param inputs the transaction metadata and pre-computed dependencies
   * @return a singleton list containing the DSG
   */
  public List<InCompleteGraph> compile(GraphInputs inputs) {
    profiler.startTick("graphcompile");
    var nodeIds = new HashSet<GraphNodeId>();
    for (int i = 0; i < inputs.numTxns(); i++) {
      nodeIds.add(GraphNodeId.txn(i));
    }

    BoomslangGraph graph = new BoomslangGraph(nodeIds, cfg);
    for (var edge : inputs.edges()) {
      if (EdgeType.isSerEdgeType(edge.edgeType)) {
        graph.addEdge(edge);
      }
    }

    var modules = new ArrayList<GenDepModule>();
    modules.add(new GenReadDepModule(profiler, mode, cfg));
    modules.add(new GenWriteDepModule(profiler, mode, cfg));
    modules.add(new GenAntiDepModule(profiler, mode, cfg));
    modules.add(new GenSessionDepModule(profiler, mode, cfg));

    InCompleteGraph current = ModuleUtility.generate(inputs, graph, modules);
    current.setGraphType(AdyaGraphType.DSG);
    profiler.endTick("graphcompile");
    return List.of(current);
  }
}
