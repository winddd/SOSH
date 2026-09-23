package util.isolation;

import graphs.edges.EdgeType;

/**
 * Specifies which edge types to include for reachability-based pruning optimizations.
 *
 * <p>This is separate from {@link IsolationSpec} because pruning may use a more
 * conservative (smaller) set of edge types than the full graph construction requires.
 * The invariant is: edges included for pruning ⊆ edges included by IsolationSpec.
 *
 * <p>Using fewer edge types for pruning is always sound (preserves correctness) but
 * may reduce the effectiveness of the optimization. Using more edge types than the
 * isolation spec would be unsound.
 */
public interface PruningSpec {

  /**
   * Returns true if the given edge type should be considered for pruning.
   *
   * @param edgeType the edge type to check
   * @return true if this edge type should be included in pruning calculations
   */
  boolean includeForPruning(EdgeType edgeType);
}
