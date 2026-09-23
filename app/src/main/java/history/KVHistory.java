package history;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import common.Key;
import common.binaryhistory.SessionHistory;
import history.op.KvOpType;
import history.op.KvOperation;
import history.op.RangeOp;
import lombok.Getter;
import lombok.Setter;
import util.Config;
import util.MapFactory;
import util.exception.InvalidInputException;

/**
 * Represents a complete transaction history for a key-value store.
 *
 * <p>
 * KVHistory is the primary data structure for storing and accessing a sequence
 * of transactions
 * ({@link KVTxn}) that were executed against a key-value store. It provides
 * both sequential
 * (by transaction ID) and associative (by thread ID) access to transactions.
 *
 * <p>
 * <b>Structure:</b>
 * <ul>
 * <li><b>T0 (Initial Transaction):</b> Special transaction at index 0
 * representing the initial state</li>
 * <li><b>Regular Transactions:</b> User transactions indexed sequentially (1 to
 * n-1 or n)</li>
 * <li><b>Tf (Final Transaction):</b> Optional special transaction representing
 * the final state</li>
 * </ul>
 *
 * <p>
 * <b>Indexing:</b>
 * <ul>
 * <li>{@code allTxns}: Sequential list of ALL transactions (T0 + regular +
 * optional Tf)</li>
 * <li>{@code tid2history}: Map from thread/session ID to transactions executed
 * in that thread</li>
 * <li>T0 is ONLY in {@code allTxns}, NOT in {@code tid2history}</li>
 * <li>Tf is ONLY in {@code allTxns}, NOT in {@code tid2history}</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> NOT thread-safe. Should be built sequentially during
 * parsing,
 * then accessed read-only.
 *
 * <p>
 * <b>Lifecycle:</b>
 * <ol>
 * <li>Construct: {@code new KVHistory(config)} - creates placeholder T0</li>
 * <li>Optionally replace T0: {@link #setInitialTxn(KVTxn)}</li>
 * <li>Add regular transactions: {@link #addRegularTxn(long, KVTxn)}</li>
 * <li>Optionally add Tf: {@link #setFinalTxn(KVTxn)}</li>
 * <li>Consume: Access via {@link #getKthTxn(long)},
 * {@link #getTxnsByTid(long)}, iteration</li>
 * </ol>
 *
 * @see KVTxn for individual transaction structure
 * @see parse.KVParserFactory for history construction from logs
 */
public class KVHistory implements Iterable<KVTxn>, Serializable {
  // thread id to a set of transactions
  private Map<Long, List<KVTxn>> tid2history;
  // T0 is only stored in `allTxns`, not in `tid2history`.
  // Tf is only in finalReadTxn, not in `tid2history`.
  @Getter
  private List<KVTxn> allTxns; // including T0, may or may not exclude Tf
  private int numTxn; // including T0, may or may not exclude Tf
  private Set<Key> allKeys;
  @Getter
  @Setter
  private Map<Integer, SessionHistory> tid2SessionTxns;
  private Config cfg;
  // debug
//  private Set<Integer> targetTxnIds = new HashSet<>(List.of(2046, 2047, 2048, 2049, 2050));

  public KVHistory(Config cfg) {
    this.tid2history = MapFactory.getEmptyMap(cfg.DEBUG);
    this.allTxns = new ArrayList<>();
    // implicitly creates a T0. Note: this is just a placeholder,
    // if the logs contain an actual initial txn, it
    // will replace this one.
    var t0 = KVTxn.createInitial(-1, cfg.DEBUG);
    allTxns.add(t0);
    numTxn = 1;
    this.cfg = cfg;
  }

  /**
   * Adds a regular (non-initial, non-final) transaction to the history.
   *
   * <p>
   * The transaction is:
   * <ul>
   * <li>Assigned a unique sequential transaction ID (numTxn++)</li>
   * <li>Added to the global {@code allTxns} list</li>
   * <li>Added to the thread-specific list in {@code tid2history}</li>
   * </ul>
   *
   * @param threadId the thread/session ID that executed this transaction
   * @param txn      the transaction to add (must not be initial or final)
   * @throws AssertionError if txn is initial or final
   */
  public void addRegularTxn(long threadId, KVTxn txn) {
    assert !txn.isInitial() && !txn.isFinal();
    if (!tid2history.containsKey(threadId)) {
      tid2history.put(threadId, new ArrayList<>());
    }

    this.tid2history.get(threadId).add(txn);

    // assign a txn id to the regular txn or final txn.
    txn.setTxnId(numTxn++);
//     if (targetTxnIds.contains(txn.getTxnId())) {
//     System.out.print("");
//     }
    this.allTxns.add(txn);
  }

  @Override
  public Iterator<KVTxn> iterator() {
    Iterator<KVTxn> it = new Iterator<>() {
      private int currentTxnIndex = 0;

      @Override
      public boolean hasNext() {
        return currentTxnIndex < allTxns.size();
      }

      @Override
      public KVTxn next() {
        return allTxns.get(currentTxnIndex++);
      }
    };
    return it;
  }

  /**
   * Returns the transaction at the specified index.
   *
   * @param k the transaction index (0-based, where 0 is T0)
   * @return the transaction at index k
   * @throws InvalidInputException if k is out of bounds
   */
  public KVTxn getKthTxn(long k) {
    if (k < allTxns.size()) {
      return allTxns.get((int) k);
    } else {
      throw new InvalidInputException();
    }
  }

  /**
   * Returns a specific operation from a transaction.
   *
   * @param i the transaction index
   * @param j the operation index within the transaction
   * @return the operation at position (i, j)
   */
  public KvOperation getOperation(long i, int j) {
    return this.getKthTxn(i).getKthOperation(j);
  }

  /**
   * Returns the operation at the specified position.
   *
   * @param pos the position (txnId, mopIndex) identifying the operation
   * @return the operation at the specified position
   */
  public KvOperation getOperationByPos(Pos pos) {
    return getOperation(pos.getTxnId(), pos.getMopIndex());
  }

  /**
   * Returns the total number of transactions including T0 and optional Tf.
   *
   * @return the transaction count
   */
  public int length() {
    return numTxn;
  }

  /**
   * Returns the number of distinct threads/sessions in the history.
   *
   * <p>
   * Note: This does NOT include T0 or Tf, only regular transactions.
   *
   * @return the number of threads
   */
  public int numThreads() {
    return tid2history.size();
  }

  /**
   * Returns all transactions executed by a specific thread/session.
   *
   * @param tid the thread/session ID
   * @return list of transactions from that thread, or null if thread doesn't
   *         exist
   */
  public List<KVTxn> getTxnsByTid(long tid) {
    return this.tid2history.get(tid);
  }

  public List<Long> getThreadIds() {
    return new ArrayList<>(tid2history.keySet());
  }

  /**
   * Replaces the placeholder T0 with an actual initial transaction from the log.
   *
   * <p>
   * By default, KVHistory creates a placeholder T0 during construction.
   * If the transaction log contains an explicit initial transaction, this method
   * replaces the placeholder.
   *
   * @param initialTxn the initial transaction (must have txnId=0 and be marked as
   *                   initial)
   * @throws AssertionError if txn is not properly marked as initial with ID 0
   */
  public void setInitialTxn(KVTxn initialTxn) {
    assert initialTxn.getTxnId() == 0 && initialTxn.isInitial();
    this.allTxns.set(0, initialTxn);
  }

  /**
   * Returns the initial state (key-value mappings from T0).
   *
   * @return map of initial key-value pairs
   */
  public Map<Key, Key> getInitialState() {
    return this.getKthTxn(0).getState();
  }

  /**
   * Returns the final transaction if one exists.
   *
   * @return Optional containing the final transaction, or empty if no final
   *         transaction exists
   */
  public java.util.Optional<KVTxn> getFinalTxn() {
    if (numTxn > 0) {
      var lastTxn = this.getKthTxn(numTxn - 1);
      if (lastTxn.isFinal()) {
        return java.util.Optional.of(lastTxn);
      }
    }
    return java.util.Optional.empty();
  }

  /**
   * Adds a final transaction representing the expected final state.
   *
   * <p>
   * The final transaction (Tf) is assigned the next sequential transaction ID
   * and appended to {@code allTxns}. It is NOT added to {@code tid2history}.
   *
   * @param finalTxn the final transaction (must be marked as final)
   * @throws AssertionError if txn is not marked as final
   */
  public void setFinalTxn(KVTxn finalTxn) {
    assert finalTxn.isFinal();
    finalTxn.setTxnId(numTxn++);
    this.allTxns.add(finalTxn);
  }

  /**
   * Returns the final state (key-value mappings from Tf).
   *
   * @return map of final key-value pairs
   * @throws AssertionError if the last transaction is not marked as final
   */
  public Map<Key, Key> getFinalState() {
    var finalTxn = this.getKthTxn(numTxn - 1);
    assert finalTxn.isFinal();
    return finalTxn.getState();
  }

  /**
   * Returns all keys accessed (read or written) in the transaction history.
   *
   * <p>
   * This includes:
   * <ul>
   * <li>Keys from unary operations (get, put, delete, etc.)</li>
   * <li>Keys from range/iterator operations (all returned key-value pairs)</li>
   * </ul>
   *
   * <p>
   * The result is computed lazily on first call and cached for subsequent calls.
   *
   * @return set of all keys accessed in the history
   */
  @SuppressWarnings("checkstyle:LocalVariableName")
  public Set<Key> allKeys() {
    if (this.allKeys == null) {
      Set<Key> K = MapFactory.getEmptySet(cfg.DEBUG);

      for (KVTxn txn : this) {
        for (KvOperation op : txn.getMops()) {
          if (KvOpType.getUnaryTypes().contains(op.getOpType())) {
            K.add(op.getKey());
          } else if (KvOpType.getBinaryTypes().contains(op.getOpType())) {
            K.addAll(((RangeOp) op).getKvPairs().keySet());
          }
        }
      }

      this.allKeys = K;
    }

    return this.allKeys;
  }
}
