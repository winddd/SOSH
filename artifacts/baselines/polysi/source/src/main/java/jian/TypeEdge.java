package jian;

import common.Key;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(callSuper = true, of = {"edgeType", "key"})
public class TypeEdge extends Edge {
  public EdgeType edgeType;
  public Key key;
  private String template = "[%d -%s(%s)-> %d]";
  private String template2 = "[%d -%s-> %d]";
  private boolean isBCEdge = false;
  @Setter
  @Getter
  private int priority = 0;

  public TypeEdge(int u, int v, EdgeType edgeType, Key key) {
    super(u, v);
    this.u = u;
    this.v = v;
    this.edgeType = edgeType;
    this.key = key;
    this.isBCEdge = false;
  }

  public TypeEdge(int u, int v, EdgeType edgeType, Key key, boolean isBCEdge) {
    super(u, v);
    this.u = u;
    this.v = v;
    this.edgeType = edgeType;
    this.key = key;
    this.isBCEdge = isBCEdge;
  }

  public String toString() {
    return String.format(template, u, edgeType.name(), key, v);
  }

  public int compareTo(TypeEdge o) {
    if (this.u > o.u) {
      // if current object is greater --> return 1
      return 1;
    } else if (this.u < o.u) {
      // if current object is greater --> return -1
      return -1;
    } else {
      // if current object is equal to o --> return 0
      return (int) (this.v - o.v);
    }
  }
}
