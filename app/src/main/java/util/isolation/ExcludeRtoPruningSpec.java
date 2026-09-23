package util.isolation;

import graphs.edges.EdgeType;

/**
 * A decorator pruning spec that excludes RTO (real-time order) edges
 * in addition to any exclusions from the delegate spec.
 */
public class ExcludeRtoPruningSpec implements PruningSpec {

  private final PruningSpec delegate;

  public ExcludeRtoPruningSpec(PruningSpec delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean includeForPruning(EdgeType edgeType) {
    return edgeType != EdgeType.RTO && delegate.includeForPruning(edgeType);
  }
}
