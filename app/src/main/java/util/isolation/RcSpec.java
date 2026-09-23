package util.isolation;

import graphs.edges.EdgeType;

public class RcSpec implements IsolationSpec {
  @Override
  public boolean resprectRealtimeOrder() {
    return false;
  }

  @Override
  public RYOWPolicy getReadOwnWritePolicy() {
    return RYOWPolicy.EITHER;
  }

  @Override
  public boolean allowNonRepeatableReads() {
    return true;
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
    return true;
  }

  @Override
  public boolean ignoreRwEdges() {
    return true;
  }

  @Override
  public boolean ignoreWrEdges() {
    return false;
  }

  @Override
  public boolean ignoreEdgeType(EdgeType edgeType) {
    return edgeType == EdgeType.PRW || edgeType == EdgeType.RW;
  }

  @Override
  public PruningSpec getPruningSpec() {
    return DependencyOnlyPruningSpec.INSTANCE;
  }
}
