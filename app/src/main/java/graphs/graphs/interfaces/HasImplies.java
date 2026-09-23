package graphs.graphs.interfaces;

import graphs.constraints.Imply;
import java.util.Collection;

public interface HasImplies {
  void addImply(Imply imply);
  void addAllImplies(Collection<Imply> implies);
  Collection<Imply> getImplies();
  void removeImply(Imply imply);
}
