package graphs.graphs.interfaces;

import graphs.constraints.Superposition;
import java.util.Set;

public interface HasSuperpositions {
  public Set<Superposition> getSuperpositions();

  public void addSuperposition(Superposition superposition);
  void removeSuperposition(Superposition superposition);
}
