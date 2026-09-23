package util.isolation;

import graphs.edges.EdgeType;

/**
 * A pruning spec that only includes WW (write-write) edges.
 *
 * <p>This is the most conservative pruning configuration, only considering
 * version order dependencies for reachability calculations.
 */
public class WwOnlyPruningSpec implements PruningSpec {

  public static final WwOnlyPruningSpec INSTANCE = new WwOnlyPruningSpec();

  private WwOnlyPruningSpec() {}

  @Override
  public boolean includeForPruning(EdgeType edgeType) {
    return edgeType == EdgeType.WW;
  }
}
