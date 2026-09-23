package encoders;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.interfaces.HasGeneralConstraints;
import graphs.graphs.interfaces.HasImplies;
import graphs.graphs.interfaces.HasSuperpositions;
import graphs.nodes.GraphNodeId;
import lombok.extern.slf4j.Slf4j;
import solvers.MonoSatNativeSolver2;
import solvers.SMTSolver;
import solvers.SMTSolverFactory;
import solvers.Solver;
import util.Config;
import util.Profiler;
import util.enumtypes.MODE;

/**
 * Encoder implementation for MonoSAT, Boomslang's primary SMT solver backend.
 *
 * <p>MonoSATEncoder transforms {@link InCompleteGraph} instances into MonoSAT solver configurations
 * by encoding nodes, edges, and constraints (superpositions, implications, generalized formulas) as
 * SMT assertions. MonoSAT is a specialized SMT solver that combines SAT solving with graph algorithms,
 * enabling efficient cycle detection and reachability queries on large dependency graphs.
 *
 * <p><b>Encoding Process:</b> The {@link #encode()} method follows this pipeline:
 * <ol>
 *   <li><b>Node Registration:</b> Add all graph nodes to the solver as variables</li>
 *   <li><b>Edge Encoding:</b> Add known edges (deterministic dependencies) to the solver</li>
 *   <li><b>Generalized Constraints:</b> Encode arbitrary boolean formulas over edge variables
 *       (if graph implements {@link HasGeneralConstraints})</li>
 *   <li><b>Superpositions:</b> Encode disjunctive edge choices (1-out-of-n selection)
 *       (if graph implements {@link HasSuperpositions})</li>
 *   <li><b>Implications:</b> Encode conditional dependencies (A ∧ B ⇒ C)
 *       (if graph implements {@link HasImplies})</li>
 *   <li><b>Acyclicity:</b> Add acyclicity constraint to detect cycles</li>
 * </ol>
 *
 * <p><b>Solver Backend Selection:</b> The actual solver instance is created by
 * {@link solvers.SMTSolverFactory} based on configuration, supporting:
 * <ul>
 *   <li>{@link solvers.MonoSatNativeSolver} - JNI-based native MonoSAT integration</li>
 *   <li>{@link solvers.MonoSatNativeSolver2} - Optimized MonoSAT variant with edge deduplication</li>
 * </ul>
 *
 * <p><b>Edge Encoding Optimization:</b> For {@link solvers.MonoSatNativeSolver2}, multiple edges
 * between the same node pair are deduplicated (only endpoint connectivity matters, edge types/keys
 * are ignored). For other solvers, each edge label (type, key) is encoded separately.
 *
 * <p><b>PL-FCV Mode:</b> When running in PL-FCV mode ({@code cfg.RUNMODE == MODE.B_PLFCV}), the
 * encoder returns the solver immediately without encoding (PL-FCV uses a specialized encoding
 * handled externally).
 *
 * <p><b>Profiling:</b> The encoder tracks encoding time via {@link util.Profiler}, logging metrics
 * for nodes, edges, superpositions, and implications separately. This enables performance analysis
 * and bottleneck identification.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * InCompleteGraph graph = compiler.compile(asg);
 * MonoSATEncoder encoder = new MonoSATEncoder(graph, "instance42-", config);
 * Solver solver = encoder.encode();
 * boolean hasCycle = solver.solve(); // true = violation found
 * }</pre>
 *
 * @see Encoder
 * @see solvers.SMTSolver
 * @see solvers.SMTSolverFactory
 * @see InCompleteGraph
 */
@Slf4j
public class MonoSATEncoder implements Encoder {
  private SMTSolver solver;
  private InCompleteGraph graph;
  private Profiler profiler;
  private String tagPrefix;
  private Config cfg;

  /**
   * Constructs a MonoSATEncoder for a single incomplete graph.
   *
   * @param graph the dependency graph to encode; must not be null
   * @param tagPrefix prefix for logging and profiling tags (e.g., "instance42-"); must not be null
   * @param cfg configuration controlling solver selection and encoding options; must not be null
   */
  public MonoSATEncoder(InCompleteGraph graph, String tagPrefix, Config cfg) {
    this.cfg = cfg;
    this.solver = SMTSolverFactory.getSMTSolver(graph, tagPrefix, cfg);
    this.graph = graph;
    this.profiler = Profiler.getInstance();
    this.tagPrefix = tagPrefix;
  }

  /**
   * Constructs a MonoSATEncoder from a list of graphs (currently supports single-graph only).
   *
   * <p><b>Limitation:</b> Multi-graph support is not yet implemented. This constructor exists
   * for future extensibility but currently throws {@link UnsupportedOperationException} if the
   * list contains more than one graph.
   *
   * @param graphs list of incomplete graphs; must contain exactly one graph
   * @param tagPrefix prefix for logging and profiling tags; must not be null
   * @param cfg configuration controlling solver selection and encoding options; must not be null
   * @throws UnsupportedOperationException if graphs.size() != 1
   */
  public MonoSATEncoder(java.util.List<InCompleteGraph> graphs, String tagPrefix, Config cfg) {
    this(graphs.get(0), tagPrefix, cfg);
    if (graphs.size() != 1) {
      throw new UnsupportedOperationException("MonoSATEncoder currently supports exactly one graph; multi-graph support pending");
    }
  }

  /**
   * Encodes the graph as a MonoSAT solver instance ready for satisfiability checking.
   *
   * <p>This method implements the complete encoding pipeline, delegating to the underlying
   * {@link SMTSolver} for low-level encoding:
   * <ol>
   *   <li><b>PL-FCV Early Exit:</b> If in PL-FCV mode, return solver immediately (specialized encoding)</li>
   *   <li><b>Node Registration:</b> Add all graph nodes to solver</li>
   *   <li><b>Edge Encoding:</b> Add known edges, with optimization for MonoSatNativeSolver2</li>
   *   <li><b>Generalized Constraints:</b> Encode arbitrary boolean formulas (if present)</li>
   *   <li><b>Superpositions:</b> Encode disjunctive edge choices (if present)</li>
   *   <li><b>Implications:</b> Encode conditional dependencies (if present)</li>
   *   <li><b>Acyclicity:</b> Add acyclicity constraint for cycle detection</li>
   * </ol>
   *
   * <p><b>Profiling:</b> Tracks encoding time for nodes, edges, superpositions, and implications
   * separately, logged via {@link util.Profiler}.
   *
   * <p><b>Edge Encoding Variants:</b>
   * <ul>
   *   <li><b>MonoSatNativeSolver2:</b> Adds a single deduplicated edge per (u, v) pair (edge
   *       type/key ignored)</li>
   *   <li><b>Other solvers:</b> Adds separate edges for each (u, v, type, key) tuple</li>
   * </ul>
   *
   * @return configured MonoSAT solver instance ready for {@link Solver#solve()}; never null
   */
  @Override
  public Solver encode() {
    log.info(tagPrefix + "Start encoding...");

    if (cfg.RUNMODE == MODE.B_PLFCV) {
      return solver;
    }

    profiler.startTick(tagPrefix + "encode");
    // add nodes
    for (GraphNodeId nodeId : graph.getNodeIds()) {
      solver.addNode(nodeId);
    }

    // add edges
    var edges = graph.getAdjList();
    log.info("Encoding known edges...");
    profiler.startTick("encoding known edges");

    for (var u : edges.keySet()) {
      for (var v : edges.get(u).keySet()) {
        if (solver instanceof MonoSatNativeSolver2) {
          var edge = new TypeEdge(u, v, EdgeType.CB, null); // any edge type and key, they won't be used
          solver.addEdge(edge);
        } else {
          for (var p : edges.get(u).get(v)) {
            var edge = new TypeEdge(u, v, p.getLeft(), p.getRight());
            solver.addEdge(edge);
          }
        }
      }
    }
    profiler.endTick("encoding known edges");

    // GeneralizedConstraints
    log.info("Encoding GeneralConstraints...");
    if (graph instanceof HasGeneralConstraints) {
      var cons = ((HasGeneralConstraints) graph).getGeneralCons();
      for (var con : cons) {
        encode(con);
      }
    }

    // Superpositions
    log.info("Encoding Superpositions...");
    profiler.startTick("encoding superpositions");
    if (graph instanceof HasSuperpositions) {
      for (var superposition : ((HasSuperpositions) graph).getSuperpositions()) {
        encode(superposition);
      }
    }
    profiler.endTick("encoding superpositions");

    // Imply
    log.info("Encoding Implies...");
    profiler.startTick("encoding implies");
    if (graph instanceof HasImplies) {
      for (var imply : ((HasImplies) graph).getImplies()) {
        encode(imply);
      }
    }
    profiler.endTick("encoding implies");

    solver.enforceAcyclic();
    profiler.endTick(tagPrefix + "encode");

    return solver;
  }

  /**
   * Encodes a generalized constraint by delegating to the solver.
   *
   * @param con the generalized constraint to encode
   */
  private void encode(GeneralizedConstraint con) {
    solver.addConstraint(con);
  }

  /**
   * Encodes a superposition constraint by delegating to the solver.
   *
   * @param superposition the superposition (disjunctive edge choice) to encode
   */
  private void encode(Superposition superposition) {
    solver.addSuperposition(superposition);
  }

  /**
   * Encodes an implication constraint by delegating to the solver.
   *
   * @param imply the implication (conditional dependency) to encode
   */
  private void encode(Imply imply) {
    solver.addImply(imply);
  }
}
