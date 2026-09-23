package buildASG.builder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import asg.ASG;
import asg.ReadEvent;
import asg.WriteEvent;
import asg.WriteMetadata;
import common.Key;
import history.KVTxn;
import history.op.KvOperation;
import history.op.RangeOp;
import util.exception.InvalidInputException;

/** Utility helpers for deriving per-read write provenance. */
public final class WriteReadUtils {
  /**
   * Returns the most recent write in the same transaction that touches the read's
   * key.
   * The returned {@link WriteEvent} captures the value and op index; empty
   * means no prior write.
   */
  static Optional<WriteEvent> findMostRecentLocalWrite(ASG asg, ReadEvent event) {
    int readTxnId = event.getTxnId();
    int readOpIndex = event.getOpIndex();
    Key readKey = event.getKey();

    if (readTxnId >= asg.getHistory().length()) {
      return Optional.empty();
    }

    KVTxn txn = asg.getHistory().getKthTxn(readTxnId);

    for (int i = readOpIndex - 1; i >= 0; i--) {
      KvOperation op = txn.getMops().get(i);
      if (!op.isWrite()) {
        continue;
      }
      if (!op.involvesKey(readKey)) {
        continue;
      }

      Key writtenValue = op.getValueForKey(readKey);
      if (writtenValue == null) {
        writtenValue = Key.getNullKey();
      }

      // Check if this write is external (no later writes to the same key)
      boolean isExternal = !txn.hasLaterWriteToKey(readKey, i);

      return Optional.of(new WriteEvent(readTxnId, readKey, writtenValue, i, isExternal));
    }

    return Optional.empty();
  }

  /**
   * Collects all candidate write events that could have produced the read value.
   *
   * Algorithm:
   * 1. Start with all writes (external + intermediate) that match the read value
   * 2. Remove any writes from the same transaction (we'll handle local writes separately)
   * 3. Add back the most recent local write if it exists and matches the read value
   *
   * This approach ensures:
   * - External writes from other transactions are always included
   * - Only the most recent local write is included (if it matches the value)
   * - For RYOW isolation levels, the local write is guaranteed to be a candidate
   *
   * @param asg   The Abstract Syntax Graph containing history and metadata
   * @param event The read event for which to find candidate writes
   * @return List of WriteEvents that could have produced the read value
   */
  static List<WriteEvent> collectCandidateWrites(ASG asg, ReadEvent event) {
    // Step 1: Get all writes (from any transaction) that match the read value
    var allWritesMatchingValue = asg.getWriteMetadata().getAllWritesByValue(event.getKey(), event.getValue());

    // Step 2: Filter out writes from the same transaction (we'll add back the most recent one)
    var externalWrites = allWritesMatchingValue.stream()
        .filter(write -> write.getTxnId() != event.getTxnId())
        .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));

    // Step 3: Add the most recent local write if it exists and matches the read value
    findMostRecentLocalWrite(asg, event)
        .filter(write -> write.getValue().equals(event.getValue()))
        .ifPresent(externalWrites::add);

    return List.copyOf(externalWrites);
  }

  /**
   * Returns true when the read exhibits a true Read-Modify-Write pattern:
   * 1. The read is an EXTERNAL read (reads from another transaction, not own prior write)
   * 2. The transaction has an EXTERNAL write to the same key (final write that survives to commit)
   *
   * Note: We don't need to explicitly check temporal ordering (write after read) because:
   * - If there's a write BEFORE the read, Requirement 1 fails (txn.hasPriorWriteToKey)
   * - If there's an external write to the key (Req 2) and no prior write (Req 1),
   *   the write MUST come after the read
   *
   * Note: The candidateWrites parameter is kept for API compatibility but is not used
   * in the RMW detection logic. RMW detection only requires checking if the transaction
   * has both an external read and an external write to the same key.
   *
   * @param txn                      The transaction containing the read
   * @param readTxnId                The transaction ID
   * @param readOpIndex              The operation index of the read within the transaction
   * @param key                      The key being read
   * @param candidateWrites          The list of writes that could have produced the read value (unused)
   * @param allExternalWritersForKey All transaction IDs with external writes to this key
   * @return true if this read is part of a valid RMW pattern
   */
  public static boolean isReadModifyWritePattern(KVTxn txn, int readTxnId, int readOpIndex, Key key,
      List<WriteEvent> candidateWrites, Set<Integer> allExternalWritersForKey) {

    // Requirement 1: This read must be EXTERNAL (not reading own prior write)
    if (txn.hasPriorWriteToKey(key, readOpIndex)) {
      return false; // Reading own write - not external read
    }

  // Requirement 2: Transaction must have an EXTERNAL write to this key
  // Combined with Req 1, this guarantees the write comes after the read
    return allExternalWritersForKey.contains(readTxnId);
  }

  /**
   * Finds the highest operation index recorded for a txn/key using write
   * metadata.
   */
  static int latestWriteOpIndex(WriteMetadata metadata, Key key, int txnId) {
    if (metadata == null) {
      return -1;
    }
    Set<WriteEvent> events = metadata.getExternalWrites(key);
    return latestOpIndex(events, txnId);
  }


  /**
   * Finds the highest operation index among write events for a specific transaction.
   *
   * @param events set of write events to search
   * @param txnId the transaction ID to filter by
   * @return the maximum operation index for the transaction, or -1 if none found
   */
  private static int latestOpIndex(Set<WriteEvent> events, int txnId) {
    if (events == null || events.isEmpty()) {
      return -1;
    }
    return events.stream()
        .filter(event -> event.getTxnId() == txnId)
        .map(WriteEvent::getOpIndex)
        .max(Integer::compareTo)
        .orElse(-1);
  }
}
