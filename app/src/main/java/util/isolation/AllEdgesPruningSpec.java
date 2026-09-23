package util.isolation;

import graphs.edges.EdgeType;

/**
 * A pruning spec that includes all edge types not ignored by the graph construction.
 *
 * <p>This delegates to an {@link IsolationSpec} to determine which edges to include,
 * effectively using the same edge set for pruning as for graph construction.
 * This maximizes pruning effectiveness but requires the full edge set.
 */
public class AllEdgesPruningSpec implements PruningSpec {

  private final IsolationSpec isolationSpec;

  public AllEdgesPruningSpec(IsolationSpec isolationSpec) {
    this.isolationSpec = isolationSpec;
  }

  @Override
  public boolean includeForPruning(EdgeType edgeType) {
    return !isolationSpec.ignoreEdgeType(edgeType);
  }
}
