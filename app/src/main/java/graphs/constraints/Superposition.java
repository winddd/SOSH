package graphs.constraints;

import graphs.edges.TypeEdge;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import util.Config;

import java.io.Serializable;
import java.sql.Array;
import java.util.*;

@EqualsAndHashCode(of={"edgeSets"})
public class Superposition implements Serializable {
  @Getter
  @Setter
  private List<Set<TypeEdge>> edgeSets;
  @Getter
  private boolean isBCNTuple;

  public Superposition(List<Set<TypeEdge>> edgeSets) {
    this(edgeSets, false);
  }

  public Superposition(List<Set<TypeEdge>> edgeSets, boolean isBCNTuple) {
    // if there is only one edge set, you should downgrade it to a set of known edges for efficiency.
    assert edgeSets.size() >= 2;
//    if (Config.get().DEBUG) {
//      if (edgeSets.size() < 2) {
//        System.out.printf("");
//      }
//    }

    this.edgeSets = edgeSets;
    this.isBCNTuple = isBCNTuple;
  }

  public Superposition(Set<TypeEdge>... es) {
    this.edgeSets = new ArrayList<>(Arrays.asList(es));
  }

//  public void addEdgeSet(Set<TypeEdge> edge) {
//    edgeSets.add(edge);
//  }

  public boolean isEmpty() {
    return edgeSets.isEmpty();
  }

//  public boolean isTrivial() {
//    return edgeSets.size() <= 1;
//  }

  public boolean isAnEdge() {
    return edgeSets.size() == 1 && edgeSets.iterator().next().size() == 1;
  }

  public TypeEdge toTypeEdge() {
    assert isAnEdge();
    return edgeSets.iterator().next().iterator().next();
  }

  public Superposition toBCSuperposition() {
    assert !isBCNTuple;
    List<Set<TypeEdge>> newEdgeSets = new ArrayList<>();
    for (var edgeSet : this.edgeSets) {
      var newEdgeSet = new HashSet<TypeEdge>();
      for (var edge : edgeSet) {
        newEdgeSet.add(edge.toBCEdge());
      }
      newEdgeSets.add(newEdgeSet);
    }
    return new Superposition(newEdgeSets, true);
  }

  public String toString() {
    StringBuilder stringBuilder = new StringBuilder("Superposition: ");
    boolean isFirst = true;
    for (var es : edgeSets) {
      stringBuilder.append((isFirst? "": "; ") + "Edges: ");
      stringBuilder.append(es);
      isFirst = false;
    }
//    stringBuilder.append(")");
    return stringBuilder.toString();
  }
}
