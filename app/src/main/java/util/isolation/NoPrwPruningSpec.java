package util.isolation;

import graphs.edges.EdgeType;

/**
 * A pruning spec that includes all edge types except PRW (predicate anti-dependency).
 *
 * <p>This is used for isolation levels like PL-2.99 where predicate anti-dependencies
 * should be excluded from pruning calculations.
 */
public class NoPrwPruningSpec implements PruningSpec {

  public static final NoPrwPruningSpec INSTANCE = new NoPrwPruningSpec();

  private NoPrwPruningSpec() {}

  @Override
  public boolean includeForPruning(EdgeType edgeType) {
    return edgeType != EdgeType.PRW;
  }
}
