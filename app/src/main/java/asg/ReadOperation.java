package asg;

import graphs.edges.EdgeType;

/**
 * Represents a read operation with both transaction ID and operation index.
 * This provides more precise tracking of read operations for fine-grained
 * dependency analysis.
 */
public record ReadOperation(int txnId, int opIndex, EdgeType edgeType) {

  // /**
  // * Creates a ReadOperation from just a transaction ID (for backward
  // * compatibility).
  // * Operation index defaults to -1 to indicate transaction-level read.
  // */
  // public static ReadOperation fromTxn(int txnId) {
  // return new ReadOperation(txnId, -1);
  // }

  // /**
  // * Creates a ReadOperation with both transaction ID and operation index.
  // */
  // public static ReadOperation of(int txnId, int opIndex) {
  // return new ReadOperation(txnId, opIndex);
  // }

  /**
   * Returns true if this represents a transaction-level read (no specific
   * operation index).
   */
  // public boolean isTxnLevel() {
  // return opIndex == -1;
  // }

  /**
   * Returns true if this represents an action-level read (has specific operation
   * index).
   */
  // public boolean isActionLevel() {
  // return opIndex >= 0;
  // }
}