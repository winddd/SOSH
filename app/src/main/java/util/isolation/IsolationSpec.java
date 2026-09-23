package util.isolation;

import graphs.edges.EdgeType;

/**
 * Specification for an isolation level's behavior and constraints.
 */
public interface IsolationSpec {

  /**
   * Returns the pruning specification for this isolation level.
   *
   * <p>The pruning spec determines which edge types are considered for
   * reachability-based optimizations. The returned spec's included edges
   * must be a subset of the edges included by this isolation spec.
   *
   * @return the pruning spec associated with this isolation level
   */
  PruningSpec getPruningSpec();
  /** Returns true only for levels that enforce real-time order. */
  boolean resprectRealtimeOrder();

  /** Indicates whether reads must see their own writes. */
  RYOWPolicy getReadOwnWritePolicy();

  /** Returns true for levels that permit non-repeatable reads. */
  boolean allowNonRepeatableReads();

  /** Whether we can derive version order from RMW edges under this isolation. */
  boolean canInferVersionOrderFromRMW();

  /** Only READ_UNCOMMITTED may surface intermediate (non-final) writes. */
  boolean allowIntermediateReads();

  boolean ignorePrwEdges();

  boolean ignoreRwEdges();

  boolean ignoreWrEdges();

  boolean ignoreEdgeType(EdgeType edgeType);

  /**
   * Returns true if this isolation level uses BC (begin-commit) edges.
   * Typically only Snapshot Isolation and variants use BC edges.
   */
  default boolean usesBcEdges() {
    return false;  // Most isolation levels don't use BC
  }
}
