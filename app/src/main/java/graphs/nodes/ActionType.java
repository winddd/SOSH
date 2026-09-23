package graphs.nodes;

import history.op.KvOpType;

/**
 * Describes the concrete operation represented by an action-level graph node.
 */
public enum ActionType {
  READ,
  WRITE,
  RANGE,
  ITER,
  DELETE,
  UNKNOWN;

  public static ActionType from(KvOpType opType) {
    if (opType == null) {
      return UNKNOWN;
    }
    return switch (opType) {
      case READ -> READ;
      case PUT -> WRITE;
      case DELETE -> DELETE;
      case RANGE -> RANGE;
      case ITER -> ITER;
      default -> UNKNOWN;
    };
  }
}
