package compile.v2.compilers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import compile.v2.inputs.GraphInputs;
import compile.v2.modules.GenDepModule;
import compile.v2.modules.GenReadDepModule;
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
 * Compiler for Read Committed (RC) isolation level verification.
 *
 * <p>
 * Read Committed is a widely-used weak isolation level that prevents dirty
 * reads but
 * allows non-repeatable reads and phantom reads. A transaction under RC is
 * guaranteed to
 * read only committed data, but different reads of the same key within a
 * transaction may
 * observe different values.
 * </p>
 *
 * <h2>Read Committed Semantics</h2>
 * <p>
 * RC satisfies two key properties:
 * </p>
 * <ul>
 * <li><b>No Dirty Reads (G0)</b>: A transaction cannot read uncommitted writes
 * from other
 * transactions. All observed writes must have committed.</li>
 * <li><b>No Dirty Writes (G0-write)</b>: Writes to the same key by different
 * transactions
 * are ordered by their commit order.</li>
 * </ul>
 *
 * <p>
 * RC explicitly <i>allows</i> the following phenomena that stronger levels
 * prohibit:
 * </p>
 * <ul>
 * <li><b>Non-Repeatable Reads (G1b)</b>: Re-reading a key may return a
 * different value</li>
 * <li><b>Phantom Reads (G1c/A3)</b>: Range queries may return different results
 * when repeated</li>
 * <li><b>Write Skew (G2)</b>: Concurrent transactions can introduce constraint
 * violations</li>
 * </ul>
 *
 * <h2>Dependency Graph Construction</h2>
 * <p>
 * The RC compiler builds a restricted form of the Direct Serialization Graph
 * that includes:
 * </p>
 * <ul>
 * <li><b>Write-Read (WR)</b> edges: Captures reads from committed writes</li>
 * <li><b>Write-Write (WW)</b> edges: Enforces commit-order on conflicting
 * writes</li>
 * </ul>
 *
 * <p>
 * Notably, RC <b>excludes</b> anti-dependency (RW) edges, which are required
 * for snapshot
 * isolation and serializability. This exclusion permits read skew and other
 * anomalies.
 * </p>
 *
 * <h2>Edge Filtering</h2>
 * <p>
 * Uses {@link EdgeType#isRcEdgeType(EdgeType)} to filter edges, accepting only
 * WR and WW
 * dependencies. Session-order and anti-dependencies are excluded.
 * </p>
 *
 * <h2>Theoretical Foundation</h2>
 * <p>
 * Based on Adya's anomaly hierarchy, RC is characterized by the absence of G0
 * (dirty reads)
 * and G0-write (dirty writes). The DSG acyclicity for RC prevents these
 * phenomena while
 * permitting weaker anomalies.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Config cfg = Config.builder().runMode(MODE.B_RC).build();
 * RcCompiler compiler = new RcCompiler(cfg);
 * List<InCompleteGraph> graphs = compiler.compile(asg);
 * // Cycle detection on graphs[0] checks for G0/G0-write violations
 * }</pre>
 *
 * @see GraphCompiler
 * @see SerCompiler
 * @see util.enumtypes.MODE#B_RC
 * @since 2.0
 */
public class RcCompiler implements GraphCompiler {
  private final Profiler profiler;
  private final MODE mode;
  private final Config cfg;

  /**
   * Constructs a Read Committed compiler with the given configuration.
   *
   * @param cfg the configuration object containing verification settings
   */
  public RcCompiler(Config cfg) {
    this.profiler = Profiler.getInstance();
    this.mode = cfg.RUNMODE;
    this.cfg = cfg;
  }

  /**
   * Compiles the transaction history into an RC dependency graph.
   *
   * <p>
   * The compilation process:
   * </p>
   * <ol>
   * <li>Creates a {@link BoomslangGraph} with transaction-level nodes</li>
   * <li>Filters edges to include only RC-relevant types (WR, WW)</li>
   * <li>Applies {@link GenReadDepModule} to generate write-read dependencies</li>
   * <li>Applies {@link GenWriteDepModule} to generate write-write
   * dependencies</li>
   * <li>Marks the graph type as {@link AdyaGraphType#DSG}</li>
   * </ol>
   *
   * @param inputs the transaction metadata and pre-computed dependencies
   * @return a singleton list containing the RC dependency graph
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
      if (EdgeType.isRcEdgeType(edge.edgeType)) {
        graph.addEdge(edge);
      }
    }

    var modules = new ArrayList<GenDepModule>();
    modules.add(new GenReadDepModule(profiler, mode, cfg));
    modules.add(new GenWriteDepModule(profiler, mode, cfg));

    InCompleteGraph current = ModuleUtility.generate(inputs, graph, modules);
    current.setGraphType(AdyaGraphType.DSG);
    profiler.endTick("graphcompile");
    return List.of(current);
  }
}
