package jian;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(of = {"u", "v"})
public class Edge {
  public int u;
  public int v;

  /**
   * u, v are begin/commit ids
   */
  public Edge(int u, int v) {
    if (u == v) {
      throw new RuntimeException("self-loop");
    }
    assert u != v;
    this.u = u;
    this.v = v;
  }

//    /**
//     * u, v are txn ids.
//     */
//    public Edge(int u, int v, EdgeType type){
//
//    }

//  public boolean equals(Object obj) {
//    if (obj == this) {
//      return true;
//    }
//    if (obj == null || obj.getClass() != this.getClass()) {
//      return false;
//    }
//
//    Edge objEdge = (Edge) obj;
//    return this.u == (objEdge).u && this.v == objEdge.v;
//  }

  public int compareTo(Edge o) {
    if (this.u > o.u) {
      // if current object is greater --> return 1
      return 1;
    } else if (this.u < o.u) {
      // if current object is greater --> return -1
      return -1;
    } else {
      // if current object is equal to o --> return 0
      return this.v - o.v;
    }
  }
}

