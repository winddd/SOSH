package asg;

import common.Key;
import history.op.KvOpType;
import history.op.KvOperation;
import lombok.Getter;

/**
 * Records a single logical read within a transaction.
 * It only captures explicit reads from ReadOp/IterOp/RangeOp.
 * For implicit predicate reads from RangeOp/IterOp, it will be handled
 * in RangeBuilder/IterBuilder.
 *
 * <p>ReadEvents are generated during transaction history parsing and represent individual
 * read operations. Each read captures the key accessed, the value observed, and metadata
 * about the operation that generated it. This information is essential for building
 * write-read dependencies in the Abstract Syntax Graph.</p>
 *
 * <h2>Operation-Level Granularity</h2>
 * <p>Unlike coarse-grained models that treat transactions as atomic units, ReadEvents
 * maintain operation-level granularity through the {@code opIndex} field. This enables:</p>
 * <ul>
 *   <li>Fine-grained dependency tracking for PL-2L and similar isolation levels</li>
 *   <li>Detection of read-your-writes anomalies within transactions</li>
 *   <li>Accurate modeling of read-modify-write operations</li>
 * </ul>
 *
 * <h2>Source Operation Types</h2>
 * <p>Reads can originate from different operation types with distinct semantics:</p>
 * <ul>
 *   <li><b>ReadOp</b>: Single-key point reads (e.g., {@code GET key})</li>
 *   <li><b>RangeOp</b>: Range queries (e.g., {@code SCAN [k1, k2]})</li>
 *   <li><b>IterOp</b>: Iterator-based scans</li>
 * </ul>
 *
 * <p>The {@code sourceOpType} field preserves this distinction, allowing isolation checkers
 * to apply special rules for multi-key operations (e.g., phantom prevention in range queries).</p>
 *
 * <h2>Usage in ASG Construction</h2>
 * <p>During ASG construction, ReadEvents are matched against {@link WriteEvent}s based on
 * key-value pairs to identify candidate write-read dependencies. The ASG builder uses the
 * operation index to determine read ordering within transactions.</p>
 *
 * <h2>Immutability</h2>
 * <p>ReadEvents are immutable value objects. All fields are final and set during construction.</p>
 *
 * @see WriteEvent
 * @see asg.ASG
 * @see history.op.KvOperation
 * @since 1.0
 */
@Getter
public class ReadEvent {
  private final int txnId;
  private final int opIndex;
  private final Key key;
  private final Key value;
  private final KvOperation operation;
  private final KvOpType sourceOpType;

  /**
   * Constructs a read event from a transaction operation.
   *
   * @param txnId the transaction ID that performed the read
   * @param opIndex the operation index within the transaction (0-based)
   * @param key the key that was read
   * @param value the value that was observed
   * @param operation the KV operation that generated this read
   * @throws AssertionError if the operation is not a read operation (when assertions enabled)
   */
  public ReadEvent(int txnId, int opIndex, Key key, Key value, KvOperation operation) {
    assert operation.isRead();
    this.txnId = txnId;
    this.opIndex = opIndex;
    this.key = key;
    this.value = value;
    this.operation = operation;
    this.sourceOpType = operation.getOpType();
  }

  /**
   * Returns the operation type derived from the underlying operation.
   * This method is not covered by @Getter as it delegates to operation.getOpType().
   */
  public KvOpType getOpType() {
    return operation.getOpType();
  }

  /**
   * Checks if this read event was generated from a ReadOp (single-key read).
   */
  public boolean isFromReadOp() {
    return sourceOpType == KvOpType.READ;
  }

  /**
   * Checks if this read event was generated from a RangeOp (range query).
   */
  public boolean isFromRangeOp() {
    return sourceOpType == KvOpType.RANGE;
  }

  /**
   * Checks if this read event was generated from an IterOp (iteration).
   */
  public boolean isFromIterOp() {
    return sourceOpType == KvOpType.ITER;
  }

  /**
   * Checks if this read event was generated from a multi-key operation (Range or
   * Iter).
   */
  public boolean isFromMultiKeyOp() {
    return sourceOpType == KvOpType.RANGE || sourceOpType == KvOpType.ITER;
  }

  @Override
  public String toString() {
    return String.format("ReadEvent{txn=%d, op=%d, key=%s, value=%s, opType=%s}",
        txnId, opIndex, key, value, sourceOpType);
  }
}
