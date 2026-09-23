package jian;

import common.Key;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(of={"stWwEdge", "rwEdges1", "tsWwEdge", "rwEdges2"})
public class GeneralizedConstraint {
  /**
   * S -WW(x)-> T
   */
  private TypeEdge stWwEdge;
  /**
   * For any T' that reads x from S, we have a RW edge: T' -RW-> T
   */
  private Set<TypeEdge> rwEdges1;
  private TypeEdge tsWwEdge;
  /**
   * If T -WW(x)-> S,
   * then for any T' that reads x from T, we have a RW edge: T' -RW-> S.
   */
  private Set<TypeEdge> rwEdges2;
  @Getter
  private Key key;
  @Getter
  private boolean isBCGeneralizedConstraint;

  public GeneralizedConstraint(TypeEdge stWwEdge, Set<TypeEdge> es1, Set<TypeEdge> es2, Key key) {
    this(stWwEdge, es1, es2, key, false);
  }

  public GeneralizedConstraint(TypeEdge stWwEdge, Set<TypeEdge> es1, Set<TypeEdge> es2, Key key,
                               boolean isBcGeneralizedConstraint) {
    assert stWwEdge.u != stWwEdge.v;
    assert stWwEdge.edgeType == EdgeType.WW;

    for (var edge : es1) {
      assert edge.edgeType == EdgeType.RW;
      assert edge.v == stWwEdge.v;
    }
    for (var edge : es2) {
      assert edge.edgeType == EdgeType.RW;
      assert edge.v == stWwEdge.u;
    }

    this.stWwEdge = stWwEdge;
    this.tsWwEdge = new TypeEdge(stWwEdge.v, stWwEdge.u, stWwEdge.edgeType, stWwEdge.key);
    this.rwEdges1 = es1;
    this.rwEdges2 = es2;
    this.key = key;
    this.isBCGeneralizedConstraint = isBcGeneralizedConstraint;
  }

  public List<TypeEdge> getEdgeSet1() {
    List<TypeEdge> es1List = new ArrayList<>(rwEdges1);
    es1List.add(0, stWwEdge);
    return es1List;
  }

  public List<TypeEdge> getEdgeSet2() {
    List<TypeEdge> es2List = new ArrayList<>(rwEdges2);
    es2List.add(0, tsWwEdge);
    return es2List;
  }
}
