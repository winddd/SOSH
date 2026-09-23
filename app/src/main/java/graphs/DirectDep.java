package graphs;

import asg.WriteEvent;
import common.Key;
import graphs.edges.EdgeType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents a direct read dependency with its candidate source writes.
 *
 * <p>DirectDep captures all the information needed to generate write-read (WR) or predicate
 * write-read (PWR) edges for a single read operation in the transaction history. It encapsulates
 * the reading transaction, the key being read, and the set of candidate writes that could have
 * been the source of the read value.</p>
 *
 * <h2>Read Dependency Model</h2>
 * <p>In isolation level verification, reads create dependencies on the writes they observe.
 * A DirectDep represents:</p>
 * <ul>
 *   <li>A specific read operation (identified by transaction, operation index, and key)</li>
 *   <li>The candidate writes that could have produced the observed value</li>
 *   <li>All writes to the same key (for generating exclusion constraints)</li>
 *   <li>Whether the read has a local write in the same transaction</li>
 * </ul>
 *
 * <h2>Candidate Writes</h2>
 * <p>The {@code writeEvents} field contains all writes that match the read's observed value.
 * During graph compilation, these candidates are encoded as a disjunction: the read must observe
 * exactly one of the candidate writes. This is typically encoded as:</p>
 * <pre>
 * WR(W1, R) OR WR(W2, R) OR ... OR WR(Wn, R)
 * </pre>
 *
 * <h2>Local vs External Writes</h2>
 * <p>The {@code hasLocalWrite} flag indicates whether the reading transaction has a preceding
 * write to the same key. This is important for isolation levels like Read Committed, where
 * transactions can read their own uncommitted writes.</p>
 *
 * <h2>Read-Modify-Write Operations</h2>
 * <p>The {@code isRMW} flag marks reads that are part of true read-modify-write operations.
 * A read qualifies as RMW when ALL of the following conditions are met:</p>
 * <ul>
 *   <li>The read is EXTERNAL (reads from another transaction, not own prior write)</li>
 *   <li>The transaction has an EXTERNAL write to the same key (final write survives to commit)</li>
 * </ul>
 * <p>Note: Temporal ordering (write after read) is implicitly guaranteed by the above conditions.
 * If the transaction has an external write to the key and no prior write before the read, the
 * write must come after the read.</p>
 * <p>Note: Unlike earlier versions, we no longer require that the candidate write be unique.
 * RMW detection focuses on the transaction's behavior (external read + external write to same key)
 * rather than on value provenance uniqueness.</p>
 * <p>RMW operations have special semantics that enable version order inference and require
 * additional WW (write-write) edges in certain isolation levels.</p>
 *
 * <h2>Usage in Compilation</h2>
 * <p>DirectDeps are typically generated during ASG construction and consumed by dependency
 * constraint graph edges.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // T1: write(x, 1)
 * // T2: write(x, 2)
 * // T3: read(x) = 2
 *
 * DirectDep dep = new DirectDep(
 *     3,                          // reading transaction T3
 *     0,                          // operation index
 *     Key.of("x"),                // key
 *     EdgeType.WR,                // edge type
 *     List.of(writeEvent_T2),     // only T2's write matches value 2
 *     Set.of(1, 2),               // all writers of x
 *     false,                      // not RMW
 *     false                       // no local write
 * );
 * }</pre>
 *
 * @since 1.0
 */
@EqualsAndHashCode(of = { "txnId", "opIndex", "key", "edgeType", "allWriteTxnIds", "candidateWriteEvents",
    "hasLocalWrite" })
public class DirectDep {
  public final Integer txnId;
  public final Integer opIndex;
  /* All the write transactions of `key`. */
  @Getter
  public final Set<Integer> allWriteTxnIds;
  public final Key key;
  public final EdgeType edgeType;
  /* Whether the read operation has a most-recent write in the same txn. */
  public final boolean hasLocalWrite;
  /* A list of condidate writes based on value matching. */
  @Getter
  @Setter
  public List<WriteEvent> candidateWriteEvents;
  /* If this read is an external read, and its txn produces an external write of the same key. */
  public boolean isRMW;

  /**
   * Constructs a direct read dependency with all associated metadata.
   *
   * <p>This constructor validates that candidate writes are provided and that the edge type
   * is appropriate for read dependencies (WR or PWR).</p>
   *
   * @param txnId the transaction ID performing the read
   * @param opIndex the operation index of the read within the transaction
   * @param key the key being read
   * @param edgeType the type of read dependency edge (must be WR or PWR)
   * @param candidateWriteEvents the list of candidate writes that match the observed value (must not be empty)
   * @param allWriteTxns all transaction IDs that write to this key (used for exclusion constraints)
   * @param isRMW whether this read is part of a read-modify-write operation
   * @param hasLocalWrite whether the reading transaction has a preceding write to this key
   * @throws IllegalArgumentException if writeEvents is null or empty
   * @throws AssertionError if edgeType is not WR or PWR (when assertions enabled)
   */
  public DirectDep(Integer txnId, Integer opIndex, Key key, EdgeType edgeType,
                   List<WriteEvent> candidateWriteEvents, Set<Integer> allWriteTxns,
                   boolean isRMW, boolean hasLocalWrite) {
//    if (txnId.equals(4930) || txnId.equals(1158) || txnId.equals(4461)) {
//      System.out.println();
//    }
    if (candidateWriteEvents == null || candidateWriteEvents.isEmpty()) {
      throw new IllegalArgumentException("Candidate writes must not be empty");
    }
    assert edgeType == EdgeType.WR || edgeType == EdgeType.PWR;
    this.txnId = txnId;
    this.opIndex = opIndex;
    this.allWriteTxnIds = allWriteTxns;
    this.key = key;
    this.edgeType = edgeType;
    this.isRMW = isRMW;
    this.candidateWriteEvents = List.copyOf(candidateWriteEvents);
    this.hasLocalWrite = hasLocalWrite;
  }

  /**
   * Returns candidate writes from transactions other than the reader.
   *
   * <p>This method filters out writes from the same transaction as the read, leaving only
   * external writes that create inter-transaction dependencies. This is useful for isolation
   * levels that treat local and external reads differently.</p>
   *
   * @return a list of candidate writes from other transactions
   */
  public List<WriteEvent> externalCandidateWrites() {
    List<WriteEvent> externals = new ArrayList<>();
    for (WriteEvent candidate : candidateWriteEvents) {
      if (!candidate.isFromTransaction(txnId)) {
        externals.add(candidate);
      }
    }
    return externals;
  }

  /**
   * Indicates whether this read has a preceding write to the same key in the same
   * transaction.
   */
  public boolean hasLocalWrite() {
    return hasLocalWrite;
  }
}
