package optimizations;

import java.util.Map;
import lombok.Getter;

/**
 * Represents the result of a satisfiability search operation in the verification process.
 *
 * <p>This class encapsulates the outcome of checking whether a transaction history satisfies
 * a given isolation level by encoding dependency graphs as SMT constraints and querying a
 * solver. The result includes:
 * <ul>
 *   <li>Whether the instance is satisfiable (sat/unsat)</li>
 *   <li>A unique identifier for the search instance</li>
 *   <li>Profiling data tracking runtime of different search phases</li>
 * </ul>
 *
 * <p><b>Satisfiability Interpretation:</b>
 * <ul>
 *   <li><b>SAT (true):</b> The dependency graph contains a cycle, indicating an isolation
 *       violation. The transaction history does NOT satisfy the required isolation level.</li>
 *   <li><b>UNSAT (false):</b> The dependency graph is acyclic, confirming that the transaction
 *       history satisfies the required isolation level (no violations detected).</li>
 * </ul>
 *
 * <p><b>Runtime Profiling:</b> The TAG2RUNTIME map associates string tags (e.g., "parse",
 * "compile", "encode", "solve") with elapsed times in seconds, enabling performance analysis
 * of the verification pipeline.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * SearchResult result = new SearchResult(
 *     true,  // SAT = isolation violation found
 *     42,
 *     Map.of("parse", 0.5, "compile", 1.2, "solve", 3.8)
 * );
 * if (result.isSat()) {
 *     System.out.println("Violation detected in instance " + result.getId());
 * }
 * }</pre>
 *
 * @see optimizations.NormalSearch
 * @see optimizations.GraphOptimizer
 */
@Getter
public class SearchResult {
  private final boolean sat;
  private final int id;
  private final Map<String, Double> TAG2RUNTIME;

  /**
   * Constructs a new SearchResult with satisfiability status, instance ID, and profiling data.
   *
   * @param sat true if the dependency graph is satisfiable (cycle found = violation detected),
   *            false if unsatisfiable (acyclic = no violation)
   * @param id unique identifier for this search instance (typically from input file or index)
   * @param TAG2RUNTIME map from phase tag names to runtime in seconds; must not be null
   */
  public SearchResult(boolean sat, int id,  Map<String, Double> TAG2RUNTIME) {
    this.sat = sat;
    this.id = id;
    this.TAG2RUNTIME = TAG2RUNTIME;
  }

  /**
   * Returns the profiling data mapping phase tags to elapsed runtime in seconds.
   *
   * <p>Common tags include:
   * <ul>
   *   <li>"parse" - time to parse input transaction log</li>
   *   <li>"compile" - time to construct dependency graphs</li>
   *   <li>"encode" - time to encode graphs as SMT constraints</li>
   *   <li>"solve" - time spent in SMT solver</li>
   * </ul>
   *
   * <p>Note: This method name differs from the generated {@code getTAG2RUNTIME()} because
   * it provides a more readable API by using camelCase.
   *
   * @return immutable map from tag name to runtime in seconds
   */
  public  Map<String, Double> getTag2Time() {
    return this.TAG2RUNTIME;
  }
}
