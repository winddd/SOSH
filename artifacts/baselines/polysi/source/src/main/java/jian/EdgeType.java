package jian;

import java.util.HashSet;
import java.util.List;

// CB: commit before; RT: real-time edges
// BB: begin to begin timestamp edges;
// BC: begin to commit timestamp edges;
// CC: commit to commit timestamp edges
// INVALID means, don't use the type info of this edge, only use the source and target node in the TypeEdge object.
// PRW: predicate anti
// RMW: read-modify-write, implies both WR and WW.
public enum EdgeType {
  WR, WW, RW, BC, CB, PRW, PWR;

  private static HashSet<EdgeType> RUEDGETYPES = new HashSet<>(List.of(WW, CB));
  private static HashSet<EdgeType> RCEDGETYPES = new HashSet<>(List.of(WW, WR, CB));
  private static HashSet<EdgeType> SIEDGETYPES = new HashSet<>(List.of(WW, WR, RW, BC, CB));
  private static HashSet<EdgeType> SEREDGETYPES = new HashSet<>(List.of(WW, WR, RW, CB));
  private static HashSet<EdgeType> ANTIEDGETYPES = new HashSet<>(List.of(RW, PRW));

  public static boolean isRuEdgeType(EdgeType edgeType) {
    return RUEDGETYPES.contains(edgeType);
  }

  public static boolean isRcEdgeType(EdgeType edgeType) {
    return RCEDGETYPES.contains(edgeType);
  }

  public static boolean isSiEdgeType(EdgeType edgeType) {
    return SIEDGETYPES.contains(edgeType);
  }

  public static boolean isSerEdgeType(EdgeType edgeType) {
    return SEREDGETYPES.contains(edgeType);
  }

  public static boolean isAntiEdgeType(EdgeType edgeType) {
    return ANTIEDGETYPES.contains(edgeType);
  }
}
