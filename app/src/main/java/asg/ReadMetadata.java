package asg;

import common.Key;
import history.Pos;
import history.op.KvOpType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable bundle of read events, per-txn buckets, and range/iterator positions.
 */
public final class ReadMetadata {
  private final List<ReadEvent> readEvents;
  private final Map<Integer, List<ReadEvent>> readEventsByTxn;
  private final Set<Pos> rangeQueries;
  private final Set<Pos> iterOps;

  public ReadMetadata(List<ReadEvent> readEvents,
                      Map<Integer, List<ReadEvent>> readEventsByTxn,
                      Set<Pos> rangeQueries,
                      Set<Pos> iterOps) {
    this.readEvents = List.copyOf(readEvents);
    Map<Integer, List<ReadEvent>> copy = new HashMap<>(readEventsByTxn.size());
    readEventsByTxn.forEach((txnId, events) -> copy.put(txnId, List.copyOf(events)));
    this.readEventsByTxn = Collections.unmodifiableMap(copy);
    this.rangeQueries = Collections.unmodifiableSet(new HashSet<>(rangeQueries));
    this.iterOps = Collections.unmodifiableSet(new HashSet<>(iterOps));
  }

  public static ReadMetadata empty() {
    return new ReadMetadata(List.of(), Map.of(), Set.of(), Set.of());
  }

  public List<ReadEvent> readEvents() {
    return readEvents;
  }

  public List<ReadEvent> readEventsForTxn(int txnId) {
    return readEventsByTxn.getOrDefault(txnId, List.of());
  }

  public Map<Integer, List<ReadEvent>> byTxn() {
    return readEventsByTxn;
  }

  public Set<Pos> rangeQueries() {
    return rangeQueries;
  }

  public Set<Pos> iterOps() {
    return iterOps;
  }

  /**
   * Returns only read events from single-key ReadOp operations.
   */
  public List<ReadEvent> readOpEvents() {
    return readEvents.stream()
        .filter(ReadEvent::isFromReadOp)
        .collect(Collectors.toList());
  }

  /**
   * Returns only read events from RangeOp operations.
   */
  public List<ReadEvent> rangeOpEvents() {
    return readEvents.stream()
        .filter(ReadEvent::isFromRangeOp)
        .collect(Collectors.toList());
  }

  /**
   * Returns only read events from IterOp operations.
   */
  public List<ReadEvent> iterOpEvents() {
    return readEvents.stream()
        .filter(ReadEvent::isFromIterOp)
        .collect(Collectors.toList());
  }

  /**
   * Returns read events from multi-key operations (RangeOp and IterOp).
   */
  public List<ReadEvent> multiKeyOpEvents() {
    return readEvents.stream()
        .filter(ReadEvent::isFromMultiKeyOp)
        .collect(Collectors.toList());
  }

  /**
   * Returns read events filtered by source operation type.
   */
  public List<ReadEvent> readEventsByOpType(KvOpType opType) {
    return readEvents.stream()
        .filter(event -> event.getSourceOpType() == opType)
        .collect(Collectors.toList());
  }

  /**
   * Returns read events for a specific transaction filtered by source operation type.
   */
  public List<ReadEvent> readEventsForTxnByOpType(int txnId, KvOpType opType) {
    return readEventsForTxn(txnId).stream()
        .filter(event -> event.getSourceOpType() == opType)
        .collect(Collectors.toList());
  }

  /**
   * Returns all read events for a specific key.
   *
   * @param key the key to filter by
   * @return list of read events that read the specified key
   */
  public List<ReadEvent> readEventsForKey(Key key) {
    return readEvents.stream()
        .filter(event -> event.getKey().equals(key))
        .collect(Collectors.toList());
  }
}
