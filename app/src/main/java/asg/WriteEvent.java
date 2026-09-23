package asg;

import common.Key;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a candidate write that could be the source of a read operation.
 * Contains all relevant information about the write for dependency analysis.
 *
 * <p>External vs Intermediate Writes:</p>
 * <ul>
 *   <li><b>External Write</b>: The final write to a key in a transaction that survives to commit.
 *       This write is visible to other transactions and creates inter-transaction dependencies.</li>
 *   <li><b>Intermediate Write</b>: A write that is later overwritten by another write to the same
 *       key within the same transaction. Only visible within the transaction itself.</li>
 * </ul>
 */
@Getter
@EqualsAndHashCode
@ToString
public class WriteEvent {
  /** The transaction ID that performed the write */
  private final int txnId;

  /** The key that was written */
  private final Key key;

  /** The value that was written */
  private final Key value;

  /** The operation index within the transaction (if known, -1 otherwise) */
  private final int opIndex;

  /** True if this is an external write (final write to this key), false if intermediate */
  private final boolean isExternal;

  /**
   * Creates a new candidate write.
   *
   * @param txnId      The transaction ID that performed the write
   * @param key        The key that was written
   * @param value      The value that was written
   * @param opIndex    The operation index (-1 if unknown)
   * @param isExternal True if this is the final write to this key (external), false if intermediate
   */
  public WriteEvent(int txnId, Key key, Key value, int opIndex, boolean isExternal) {
    this.txnId = txnId;
    this.key = key;
    this.value = value;
    this.opIndex = opIndex;
    this.isExternal = isExternal;
  }

  /**
   * Checks if this write is from the same transaction as the given read.
   */
  public boolean isFromTransaction(int readTxnId) {
    return txnId == readTxnId;
  }

  /**
   * Checks if this write could be a future write relative to the given read.
   * A future write occurs when a transaction reads a value it will write later.
   */
  public boolean isPotentialFutureWrite(int readTxnId, int readOpIndex) {
    return isFromTransaction(readTxnId) && opIndex > readOpIndex;
  }
}
