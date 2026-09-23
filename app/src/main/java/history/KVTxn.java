package history;

import asg.WriteEvent;
import common.Key;
import common.binaryhistory.LOG_TXN;
import common.binaryhistory.Mop;
import common.binaryhistory.OP_TYPE;
import history.op.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import util.MapFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@EqualsAndHashCode(callSuper = false, of = { "txnId", "threadId", "mops" })
public class KVTxn {
  public static final int T0_SESSION_ID = -1;
  public static final int TF_SESSION_ID = -2;
  @Setter
  @Getter
  int txnId;
  // which thread id the txn belongs to,
  // T0 doesn't belong to any txn, so its sessionId = -1.
  // Tf's sessionId is -2.
  @Getter
  @Setter
  long threadId;
  @Getter
  List<KvOperation> mops;
  @Setter
  Map<Key, Key> state;
  boolean isInitial;
  boolean isFinal;
  @Getter
  @Setter
  long beginTS; // realtime
  @Getter
  @Setter
  long commitTS; // realtime
  @Getter
  @Setter
  long txnTS; // allocated time for transaction commit
  // currently only for EDN logs
  // i-th transaction of the current thread
  int index;
  TransactionStatus status;
  private boolean debug;

  public KVTxn(int txnId, long sessionId, boolean isInitial, boolean isFinal, boolean debug) {
    this.txnId = txnId;
    this.threadId = sessionId;
    this.isInitial = isInitial;
    this.isFinal = isFinal;
    this.mops = new ArrayList<>();
    this.debug = debug;
  }

  /**
   * Static factory method for creating a KVTxn from a LOG_TXN object.
   *
   * @param sessionId
   * @param txn       the LOG_TXN object containing transaction data
   * @param debug     debug mode flag
   * @return a new KVTxn instance
   */
  public static KVTxn fromLogTxn(int sessionId, LOG_TXN txn, boolean debug) {
    KVTxn kvtxn = new KVTxn(0, sessionId, txn.isInitial(), txn.isFinal(), debug);

    if (!kvtxn.isInitial) {
      List<Mop> mops = txn.getMops();
      for (int i = 0; i < mops.size(); i++) {
        Mop mop = mops.get(i);
        // System.out.println(String.format("processing mop %d", i));
        kvtxn.addOperations(KvOperation.createKVOperation(mop, OpUtil.getInitialTxnCtx()));
      }
    } else {
      // convert initial txn into a collection of put operations.
      // and set the initial state.
      List<Mop> originalMops = txn.getMops();
      List<KvOperation> putOps = new ArrayList<>();
      Map<Key, Key> kvPairs = MapFactory.getEmptyMap(debug);

      // NOTE: we only convert those items in `state` into writes here.
      // For those items that don't exist, you still need to create a NULL write for
      // them later
      // in the construction of graphIR.
      for (Mop originalMop : originalMops) {
        if (originalMop.op_type == OP_TYPE.RANGE) {
          for (var kv : originalMop.kvPairs.entrySet()) {
            Key key = kv.getKey(), value = kv.getValue();
            putOps.add(new PutOp(key, value));
          }

          kvPairs.putAll(originalMop.kvPairs);
        }
      }

      kvtxn.addOperations(putOps);
      kvtxn.setState(kvPairs);
    }

    return kvtxn;
  }

  /**
   * Static factory method for creating a regular KVTxn (not initial or final).
   *
   * @param sessionId the thread ID this transaction belongs to
   * @param debug     debug mode flag
   * @return a new KVTxn instance
   */
  public static KVTxn createRegular(long sessionId, boolean debug) {
    return new KVTxn(0, sessionId, false, false, debug);
  }

  public static KVTxn createRegular(int txnId, long sessionId, boolean debug) {
    return new KVTxn(txnId, sessionId, false, false, debug);
  }

  public static KVTxn createInitial(long sessionId, boolean debug) {
    var t0 = new KVTxn(0, sessionId, true, false, debug);
    t0.setState(MapFactory.getEmptyMap(debug)); // Initialize with empty state
    return t0;
  }

  public static KVTxn createFinal(int txnId, long sessionId, boolean debug) {
    return new KVTxn(txnId, sessionId, false, true, debug);
  }

  public Map<Key, Key> getState() {
    assert isInitial || isFinal;
    return state;
  }

  public boolean isCommitted() {
    if (this.mops.get(this.mops.size() - 1).getOpType() == KvOpType.ABORT) {
      return false;
    }
    return true;
  }

  public boolean isInitial() {
    return isInitial;
  }

  public boolean isFinal() {
    return isFinal;
  }

  public void addOperations(KvOperation mop) {
    this.mops.add(mop);
  }

  public void addOperations(List<KvOperation> mops) {
    // single implementation lives here
    for (KvOperation mop : mops) {
      this.addOperations(mop);
    }
  }

  @SafeVarargs // ok because method is not static but doesn't store varargs; avoids generic
               // varargs warnings
  public final void addOperations(KvOperation... mops) {
    if (mops == null)
      return; // handle ambiguous null
    addOperations(Arrays.asList(mops)); // delegate
  }

  public ReadOp addRead(Key key, Key value) {
    ReadOp op = new ReadOp(key, value);
    addOperations(op);
    return op;
  }

  public PutOp addWrite(Key key, Key value) {
    PutOp op = new PutOp(key, value);
    addOperations(op);
    return op;
  }

  public int size() {
    return mops.size();
  }

  public KvOperation getKthOperation(int k) {
    return this.mops.get(k);
  }

  public KvOperation getFirstWriteByKey(Key key) {
    KvOperation op = null;

    for (int i = 0; i < this.size(); ++i) {
      KvOperation currOp = getKthOperation(i);

      if ((currOp.getOpType() == KvOpType.PUT || currOp.getOpType() == KvOpType.DELETE)
          && currOp.getKey().equals(key)) {
        op = currOp;
      }
    }

    assert op != null;
    return op;
  }

  /**
   * Find the last write operation before `index` (exclusive) and return it as a WriteEvent.
   * Returns empty Optional if no prior write exists.
   *
   * @param key   The key to search for
   * @param index The operation index to search before (exclusive)
   * @return Optional containing WriteEvent if found, empty otherwise
   */
  public Optional<WriteEvent> getLastWriteByKey(Key key, int index) {
    assert index >= 0 && index <= mops.size();

    for (int i = index - 1; i >= 0; i--) {
      KvOperation mop = getKthOperation(i);

      // find the last write before `index`.
      if (mop.isWrite() && mop.getKey().equals(key)) {
        Key value = mop.getValue();
        if (value == null) {
          value = Key.getNullKey();
        }

        // Check if this write is external (no later writes to the same key)
        boolean isExternal = !hasLaterWriteToKey(key, i);

        return Optional.of(new WriteEvent(txnId, key, value, i, isExternal));
      }
    }

    return Optional.empty();
  }

  /**
   * Checks if there's a write to the given key before the specified operation index.
   *
   * @param key The key to check
   * @param beforeOpIndex The operation index (exclusive upper bound)
   * @return true if there's a prior write to the same key, false otherwise
   */
  public boolean hasPriorWriteToKey(Key key, int beforeOpIndex) {
    for (int i = beforeOpIndex - 1; i >= 0; i--) {
      KvOperation op = getKthOperation(i);
      if (op.isWrite() && op.involvesKey(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if there's a write to the given key after the specified operation index.
   *
   * @param key The key to check
   * @param afterOpIndex The operation index (exclusive lower bound)
   * @return true if there's a later write to the same key, false otherwise
   */
  public boolean hasLaterWriteToKey(Key key, int afterOpIndex) {
    for (int i = afterOpIndex + 1; i < mops.size(); i++) {
      KvOperation op = getKthOperation(i);
      if (op.isWrite() && op.involvesKey(key)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder("[");
    for (KvOperation op : mops) {
      stringBuilder.append(op.toString()).append(",");
    }
    if (stringBuilder.charAt(stringBuilder.length() - 1) == ',') {
      stringBuilder.deleteCharAt(stringBuilder.length() - 1);
    }
    stringBuilder.append("]");

    return String.format("{id: %d, threadId: %d, %d mops: %s}",
        txnId, threadId, mops.size(), stringBuilder);
  }

  public enum TransactionStatus {
    ONGOING, COMMIT
  }
}
