package util.isolation;

public class StrictSerSpec extends SerSpec implements IsolationSpec {
  @Override
  public boolean resprectRealtimeOrder() {
    return true;
  }

  @Override
  public PruningSpec getPruningSpec() {
    return new AllEdgesPruningSpec(this);
  }
}
