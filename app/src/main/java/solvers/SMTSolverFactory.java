package solvers;

import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.interfaces.PolySIABGraph;
import util.Config;
import util.enumtypes.MODE;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for creating SMT solver instances based on graph type and verification mode.
 *
 * <p>This factory encapsulates the logic for selecting the appropriate SMT solver implementation
 * based on the constraint graph structure and the isolation level being checked. Different
 * isolation levels and graph representations require specialized solver strategies.</p>
 *
 * <h2>Solver Selection Strategy</h2>
 * <p>The factory selects solvers based on the following criteria:</p>
 * <ol>
 *   <li><b>Graph Type</b>: Polymorphic SI graphs ({@link PolySIGraph}) use specialized
 *       {@link PolySISolver}, while standard Boomslang graphs use mode-specific solvers.</li>
 *   <li><b>Verification Mode</b>: Predicate lock levels (PL-2+, PL-CS, PL-3U, PL-FCV) require
 *       custom solvers that handle additional temporal and predicate constraints.</li>
 *   <li><b>Performance Tuning</b>: Configuration options like {@code DISABLE_HASHMAP} control
 *       whether to use optimized native solver implementations.</li>
 * </ol>
 *
 * <h2>Specialized Solvers</h2>
 * <ul>
 *   <li>{@link PolySISolver} - Handles polymorphic snapshot isolation graphs</li>
 *   <li>{@link PL2PlusSolver} - Verifies PL-2+ (causal consistency) constraints</li>
 *   <li>{@link CursorStabilitySolver} - Checks PL-CS (cursor stability) invariants</li>
 *   <li>{@link Pl3USolver} - Enforces PL-3U (update serializability) guarantees</li>
 *   <li>{@link PlFcvSolver} - Validates PL-FCV (forward-consistent view) properties</li>
 *   <li>{@link MonoSatNativeSolver} - Default solver using MonoSAT's native graph API</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * BoomslangGraph graph = compiler.compile(asg);
 * SMTSolver solver = SMTSolverFactory.getSMTSolver(graph, "check", cfg);
 * boolean hasCycle = solver.solve();
 * if (hasCycle) {
 *   List<Integer> violationCycle = solver.getViolationCycle();
 *   // Process violation...
 * }
 * }</pre>
 *
 * @see SMTSolver
 * @see graphs.graphs.InCompleteGraph
 * @see util.enumtypes.MODE
 * @since 1.0
 */
public class SMTSolverFactory {
  /**
   * Creates an SMT solver for multiple constraint graphs.
   *
   * <p>The factory analyzes the graph types and verification mode to determine the optimal
   * solver implementation. Multi-graph solving is typically used for per-key verification
   * in predicate lock modes.</p>
   *
   * @param graphs the list of constraint graphs to solve
   * @param tagPrefix prefix for naming SMT variables to avoid collisions
   * @param cfg the configuration object specifying the verification mode and solver options
   * @return an SMT solver instance appropriate for the graph types and mode
   * @throws IllegalArgumentException if the graphs list is null or empty
   * @see MODE
   */
  public static SMTSolver getSMTSolver(List<InCompleteGraph> graphs, String tagPrefix, Config cfg) {
    if (graphs == null || graphs.isEmpty()) {
      throw new IllegalArgumentException("SMTSolverFactory requires at least one graph");
    }

    InCompleteGraph first = graphs.get(0);
    if (first instanceof PolySIABGraph polySIABGraph) {
      return new PolySISolver(polySIABGraph, tagPrefix, cfg);
    }

    if (first instanceof BoomslangGraph) {
      List<BoomslangGraph> boomGraphs = graphs.stream()
          .map(g -> (BoomslangGraph) g)
          .collect(Collectors.toList());
      return switch (cfg.RUNMODE) {
        case B_PL2P -> new PL2PlusSolver(tagPrefix, cfg, boomGraphs);
        case B_PLCS -> new CursorStabilitySolver(tagPrefix, cfg, boomGraphs);
        case B_PLFCV -> new PlFcvSolver(tagPrefix, cfg, boomGraphs);
        default -> cfg.DISABLE_HASHMAP
            ? new MonoSatNativeSolver2(tagPrefix, cfg)
            : new MonoSatNativeSolver(tagPrefix, cfg);
      };
    }

    return cfg.DISABLE_HASHMAP ? new MonoSatNativeSolver2(tagPrefix, cfg) : new MonoSatNativeSolver(tagPrefix, cfg);
  }

  /**
   * Creates an SMT solver for a single constraint graph.
   *
   * <p>This is the standard interface for most isolation level checks, where verification
   * operates on a single global dependency graph.</p>
   *
   * @param graph the constraint graph to solve
   * @param tagPrefix prefix for naming SMT variables
   * @param cfg the configuration object specifying the verification mode
   * @return an SMT solver instance appropriate for the graph type and mode
   * @throws IllegalArgumentException if the graph is null (wrapped in list check)
   */
  public static SMTSolver getSMTSolver(InCompleteGraph graph, String tagPrefix, Config cfg) {
    return getSMTSolver(List.of(graph), tagPrefix, cfg);
  }
}
