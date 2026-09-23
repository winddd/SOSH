package util.isolation;

import graphs.edges.EdgeType;

/**
 * A pruning spec that only includes dependency edges (WR, WW, PWR, CB).
 *
 * <p>This excludes anti-dependency edges (RW, PRW) from pruning calculations,
 * which is a common conservative choice that works for most isolation levels.
 */
public class DependencyOnlyPruningSpec implements PruningSpec {

  public static final DependencyOnlyPruningSpec INSTANCE = new DependencyOnlyPruningSpec();

  private DependencyOnlyPruningSpec() {}

  @Override
  public boolean includeForPruning(EdgeType edgeType) {
    return EdgeType.isDependencyType(edgeType);
  }
}
