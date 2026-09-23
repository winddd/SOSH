package util.isolation;

import graphs.edges.EdgeType;

public class RuSpec implements IsolationSpec {
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
    return true;
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
    return true;
  }

  @Override
  public boolean ignoreEdgeType(EdgeType edgeType) {
    return edgeType == EdgeType.PRW || edgeType == EdgeType.RW || edgeType == EdgeType.WR || edgeType == EdgeType.PWR;
  }

  @Override
  public PruningSpec getPruningSpec() {
    return WwOnlyPruningSpec.INSTANCE;
  }
}
