package graphs.graphs;

import graphs.constraints.GeneralizedConstraint;
import graphs.graphs.interfaces.HasGeneralConstraints;
import graphs.nodes.GraphNodeId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;
import util.Config;

/**
 * For PolySI.
 */
public class PolySIGraph extends InCompleteGraph implements HasGeneralConstraints {
  protected Set<GeneralizedConstraint> generalCons;

  public PolySIGraph(Collection<GraphNodeId> txns, Config cfg) {
    super(txns, cfg);
    generalCons = new HashSet<>();
  }

  @Override
  public boolean isValidNode(GraphNodeId nodeId) {
    assert nodeId.isTxn();
    return isValidTxn(nodeId);
  }

  @Override
  public void dump(String filePath) {
    throw new NotImplementedException("");
  }

  @Override
  public void addGeneralizedConstraint(GeneralizedConstraint con) {
    generalCons.add(con);
  }

  @Override
  public Collection<GeneralizedConstraint> getGeneralCons() {
    return generalCons;
  }

  @Override
  public void removeGeneralizedConstraint(GeneralizedConstraint con) {
    generalCons.remove(con);
  }
}
