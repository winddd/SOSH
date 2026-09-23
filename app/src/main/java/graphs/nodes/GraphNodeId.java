package graphs.nodes;

import java.util.Objects;
import java.util.Optional;

/**
 * Identifier for graph nodes, optionally pointing to a specific operation inside a transaction.
 */
public final class GraphNodeId {
  public static final int OP_INDEX_NONE = -1;

  private final NodeKind kind;
  private final int txnId;
  private final int opIndex;
  private final ActionType actionType;

  private GraphNodeId(NodeKind kind, int txnId, int opIndex, ActionType actionType) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.txnId = txnId;
    this.opIndex = opIndex;
    this.actionType = actionType;
  }

  public static GraphNodeId txn(int txnId) {
    return new GraphNodeId(NodeKind.TXN, txnId, OP_INDEX_NONE, null);
  }

  public static GraphNodeId action(int txnId, int opIndex) {
    return new GraphNodeId(NodeKind.ACTION, txnId, opIndex, ActionType.UNKNOWN);
  }

  public static GraphNodeId action(int txnId, int opIndex, ActionType actionType) {
    return new GraphNodeId(NodeKind.ACTION, txnId, opIndex,
        Objects.requireNonNull(actionType, "actionType"));
  }

  public NodeKind kind() {
    return kind;
  }

  public int txnId() {
    return txnId;
  }

  public int opIndex() {
    return opIndex;
  }

  public Optional<ActionType> actionType() {
    return Optional.ofNullable(actionType);
  }

  public boolean isTxn() {
    return kind == NodeKind.TXN;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GraphNodeId other)) return false;
    return kind == other.kind && txnId == other.txnId && opIndex == other.opIndex
        && Objects.equals(actionType, other.actionType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, txnId, opIndex, actionType);
  }

  @Override
  public String toString() {
    if (isTxn()) {
      return Integer.toString(txnId);
    }
    return kind + "(" + txnId + "," + opIndex + "," + actionType + ")";
  }
}
