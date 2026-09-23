package graphs.edges;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;

import util.enumtypes.ISOLATION_LEVEL;
import util.exception.InvalidInputException;

// CB: commit before; RT: real-time edges
// BB: begin to begin timestamp edges;
// BC: begin to commit timestamp edges;
// CC: commit to commit timestamp edges
// INVALID means, don't use the type info of this edge, only use the source and target node in the TypeEdge object.
// PRW: predicate anti
// RMW: read-modify-write, implies both WR and WW.
// RTO: realtime order.
// EXPECTED: expected execution order, e.g., expected serial order from timestamps
public enum EdgeType implements Serializable {
  WR, WW, RW, BC, CB, PRW, PWR, RTO, EXPECTED;

  private static HashSet<EdgeType> RUEDGETYPES = new HashSet<>(List.of(WW, CB));
  private static HashSet<EdgeType> RCEDGETYPES = new HashSet<>(List.of(PWR, WW, WR, CB));
  private static HashSet<EdgeType> SIEDGETYPES = new HashSet<>(List.of(PWR, PRW, WW, WR, RW, BC, CB));
  private static HashSet<EdgeType> SEREDGETYPES = new HashSet<>(List.of(PRW, PWR, WW, WR, RW, CB));
  private static HashSet<EdgeType> RREDGETYPES = new HashSet<>(List.of(PWR, WW, WR, RW, CB));
  private static HashSet<EdgeType> SSEREDGETYPES = new HashSet<>(List.of(PRW, PWR, WW, WR, RW, CB, RTO));
  private static HashSet<EdgeType> ANTIEDGETYPES = new HashSet<>(List.of(RW, PRW));
  private static HashSet<EdgeType> DEPENDENCYTYPES = new HashSet<>(List.of(WR, WW, PWR, CB));
  private static HashSet<EdgeType> WRITEDEPENDENCYTYPES = new HashSet<>(List.of(WW));

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

  public static boolean isSSerEdgeType(EdgeType edgeType) {
    return SSEREDGETYPES.contains(edgeType);
  }

  public static boolean isRrEdgeType(EdgeType edgeType) {
    return RREDGETYPES.contains(edgeType);
  }

  public static boolean belongToIsolation(EdgeType edgeType, ISOLATION_LEVEL isolationLevel) {
    switch (isolationLevel) {
      case SERIALIZABLE -> {
        return isSerEdgeType(edgeType);
      }
      case SNAPSHOT_ISOLATION -> {
        return isSiEdgeType(edgeType);
      }
      case PL_299 -> {
        return isRrEdgeType(edgeType);
      }
      case READ_COMMITTED -> {
        return isRcEdgeType(edgeType);
      }
      case READ_UNCOMMITTED -> {
        return isRuEdgeType(edgeType);
      }
      case STRICT_SERIALIZABLE -> {
        return isSSerEdgeType(edgeType);
      }
      default -> throw new InvalidInputException("belongToIsolation only applies to SSER, SER, SI, RC and RU.");
    }
  }

  public static boolean isAntiDependencyType(EdgeType edgeType) {
    return ANTIEDGETYPES.contains(edgeType);
  }

  public static boolean isDependencyType(EdgeType edgeType) {
    return DEPENDENCYTYPES.contains(edgeType);
  }

  public static boolean isWriteDependencyType(EdgeType edgeType) {
    return WRITEDEPENDENCYTYPES.contains(edgeType);
  }
}
