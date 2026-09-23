package util.isolation;

import graphs.edges.EdgeType;

public class PlFcvSpec implements IsolationSpec {
  @Override
  public boolean resprectRealtimeOrder() {
    return false;
  }

  @Override
  public RYOWPolicy getReadOwnWritePolicy() {
    return RYOWPolicy.MUST_RYOW;
  }

  @Override
  public boolean allowNonRepeatableReads() {
    return false;
  }

  @Override
  public boolean canInferVersionOrderFromRMW() {
    return false;
  }

  @Override
  public boolean allowIntermediateReads() {
    return false;
  }

  @Override
  public boolean ignorePrwEdges() {
    return false;
  }

  @Override
  public boolean ignoreRwEdges() {
    return false;
  }

  @Override
  public boolean ignoreWrEdges() {
    return false;
  }

  @Override
  public boolean ignoreEdgeType(EdgeType edgeType) {
    return false;
  }

  @Override
  public PruningSpec getPruningSpec() {
    return DependencyOnlyPruningSpec.INSTANCE;
  }
}
