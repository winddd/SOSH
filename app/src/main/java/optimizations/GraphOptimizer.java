package optimizations;

import graphs.graphs.InCompleteGraph;

/**
 * Strategy interface for applying optimization transformations to dependency graphs.
 *
 * <p>Graph optimization is a critical phase in Boomslang's verification pipeline that occurs
 * between graph compilation and SMT encoding. Optimizers analyze and transform incomplete
 * graphs to reduce solver complexity while preserving correctness of the verification result.
 *
 * <p><b>Common Optimization Strategies:</b>
 * <ul>
 *   <li><b>Reachability Pruning:</b> Remove transitive edges that are redundant for cycle
 *       detection (e.g., if A→B and B→C exist, A→C can be omitted)</li>
 *   <li><b>Weight-Guided Search:</b> Prioritize edges based on likelihood of participating
 *       in violations to accelerate solver convergence</li>
 *   <li><b>Reverse Search:</b> Use unsat-core feedback to iteratively refine graph constraints
 *       and eliminate provably unnecessary edges</li>
 *   <li><b>Normal Search:</b> Baseline strategy that applies no transformations (identity
 *       optimization)</li>
 * </ul>
 *
 * <p><b>Correctness Guarantee:</b> All optimizations must be sound—they may reduce graph
 * size but cannot change the satisfiability result. An optimized graph is SAT if and only
 * if the original graph is SAT.
 *
 * <p><b>Implementation Pattern:</b>
 * <pre>{@code
 * public class MyOptimizer implements GraphOptimizer {
 *   private final InCompleteGraph graph;
 *
 *   public MyOptimizer(InCompleteGraph graph) {
 *     this.graph = graph;
 *   }
 *
 *   @Override
 *   public InCompleteGraph optimize() {
 *     // Apply transformations (edge removal, constraint simplification, etc.)
 *     return optimizedGraph;
 *   }
 * }
 * }</pre>
 *
 * <p><b>Integration:</b> Optimizers are typically selected via {@link compile.Config}
 * optimization flags and invoked in the main verification pipeline between graph compilation
 * and encoder construction.
 *
 * @see optimizations.NormalSearch
 * @see graphs.graphs.InCompleteGraph
 * @see compile.Config
 */
public interface GraphOptimizer {
  /**
   * Applies optimization transformations to the dependency graph.
   *
   * <p>This method analyzes the graph structure and returns an optimized version that is
   * logically equivalent (same satisfiability) but potentially more efficient to encode
   * and solve. Common transformations include edge reduction, constraint simplification,
   * and ordering heuristics.
   *
   * <p><b>Soundness Contract:</b> The returned graph must satisfy:
   * <pre>
   * optimize().isSatisfiable() ⟺ originalGraph.isSatisfiable()
   * </pre>
   *
   * <p><b>Implementation Notes:</b>
   * <ul>
   *   <li>May return the original graph unchanged (no-op optimization)</li>
   *   <li>May modify the graph in-place or return a new instance</li>
   *   <li>Should preserve node identifiers to maintain traceability</li>
   *   <li>May add metadata or hints to guide subsequent encoding</li>
   * </ul>
   *
   * @return optimized graph with equivalent satisfiability but potentially reduced complexity
   */
  InCompleteGraph optimize();
}
