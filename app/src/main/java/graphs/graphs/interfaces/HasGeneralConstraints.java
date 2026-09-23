package graphs.graphs.interfaces;

import graphs.constraints.GeneralizedConstraint;
import java.util.Collection;

public interface HasGeneralConstraints {
  void addGeneralizedConstraint(GeneralizedConstraint con);
  Collection<GeneralizedConstraint> getGeneralCons();
  void removeGeneralizedConstraint(GeneralizedConstraint con);
}
