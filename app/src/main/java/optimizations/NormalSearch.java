package optimizations;

import encoders.Encoder;
import encoders.EncoderFactory;
import graphs.graphs.InCompleteGraph;
import solvers.Solver;
import util.Config;
import util.Profiler;
import util.enumtypes.ISOLATION_LEVEL;

import java.util.List;

/**
 * Standard forward search for cycle detection in dependency graphs.
 *
 * <p>NormalSearch implements the primary verification algorithm for checking isolation
 * level compliance by:
 * <ol>
 *   <li>Encoding constraint graphs into SMT formulas</li>
 *   <li>Invoking an SMT solver to detect cycles</li>
 *   <li>Returning SAT (compliant) or UNSAT (violation detected)</li>
 * </ol>
 *
 * <p><b>Algorithm:</b> Forward cycle detection using MonoSAT's graph reachability solver.
 * The solver determines if there exists an assignment of superposition constraints that
 * creates a cycle in the dependency graph. A cycle indicates a serializability violation.
 *
 * <p><b>Concurrency:</b> NormalSearch is designed to run in parallel with {@link unsat.ReverseSearch}
 * in a race configuration where the first solver to return wins. This provides both:
 * <ul>
 *   <li>Fast UNSAT detection via reverse search (unsat-core-based)</li>
 *   <li>Definitive SAT results via normal search (complete forward checking)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * NormalSearch normalSearch = new NormalSearch(graphs, 1, ISOLATION_LEVEL.SERIALIZABLE, cfg);
 * SearchResult result = normalSearch.search();
 * if (result.isSat()) {
 *   System.out.println("History satisfies " + isolationLevel);
 * } else {
 *   System.out.println("Violation detected");
 * }
 * }</pre>
 *
 * @see SearchResult for result wrapper
 * @see encoders.MonoSATEncoder for SMT encoding
 */
public class NormalSearch {
  private final List<InCompleteGraph> graphs;
  private final int id;
  private final Config cfg;

  public NormalSearch(List<InCompleteGraph> graphs,
      int id,
      Config cfg) {
    if (graphs == null || graphs.isEmpty()) {
      throw new IllegalArgumentException("NormalSearch requires at least one graph");
    }
    this.graphs = graphs;
    this.id = id;
    this.cfg = cfg;
  }

  /**
   * Performs forward cycle detection on the constraint graphs.
   *
   * <p>This method:
   * <ol>
   *   <li>Creates an encoder for the constraint graphs</li>
   *   <li>Encodes graphs into SMT formulas</li>
   *   <li>Invokes the SMT solver</li>
   *   <li>Returns the result wrapped in {@link SearchResult}</li>
   * </ol>
   *
   * <p><b>Result Interpretation:</b>
   * <ul>
   *   <li><b>SAT (true):</b> No cycles exist - history satisfies the isolation level</li>
   *   <li><b>UNSAT (false):</b> A cycle exists - isolation level is violated</li>
   * </ul>
   *
   * <p><b>Performance:</b> Execution time is recorded in the profiler and included
   * in the returned SearchResult.
   *
   * @return SearchResult containing SAT/UNSAT status, search ID, and timing information
   */
  public SearchResult search() {
    Profiler profiler = Profiler.getInstance();
    Encoder encoder = EncoderFactory.getEncoder(graphs, "normal search: ", cfg);
    Solver solver = encoder.encode();
    boolean sat = solver != null && solver.solve();

    // Print conflict analysis if UNSAT
    if (!sat && solver != null) {
      System.out.println("\n[Normal Search] Result: UNSAT - Violation detected");
      solver.printConflictClauses();
    }

    profiler.endAll();
    profiler.recordResults();
    // profiler.startTick("normal search: e2e");
    // wrap SAT/UNSAT as a SearchResult
    SearchResult r = new SearchResult(sat, id, profiler.getTAG2RUNTIME());
    // profiler.endTick("normal search: e2e");
    return r;
  }
}
