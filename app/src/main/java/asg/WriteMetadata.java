package asg;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import common.Key;
import history.KVHistory;
import history.KVTxn;
import history.op.KvOpType;
import history.op.KvOperation;
import lombok.Getter;
import util.Config;
import util.MapFactory;

/**
 * Immutable view of write-related indexes produced during ASG construction.
 *
 * All maps share the same basic conventions:
 * - Outer key is the logical key in the history.
 * - Values capture either the value written (for WR edges) or the txn/op pair
 * that wrote it.
 * - "External" == final write that survives to commit, "Intermediate" ==
 * overwritten later
 * within the same txn, and "All" == union of external + intermediate.
 */
public final class WriteMetadata {

  /** key -> value -> final (external) write events */
  @Getter
  private final Map<Key, Map<Key, Set<WriteEvent>>> extWrites;
  /** key -> value -> intermediate write events overwritten later in the txn */
  @Getter
  private final Map<Key, Map<Key, Set<WriteEvent>>> intWrites;
  /** key -> value -> all (external + intermediate) write events */
  private final Map<Key, Map<Key, Set<WriteEvent>>> allWrites;
  /** opType -> key -> txn -> write events for external writes of that type */
  private final Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> extWritesByCategory;
  /** opType -> key -> txn -> write events for intermediate writes of that type */
  private final Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> intWritesByCategory;
  /** opType -> key -> txn -> write events for all writes of that type */
  private final Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> allWritesByCategory;
  /** key -> txn ids that perform a final delete */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2ExtDels;
  /** key -> delete events that are intermediate */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2IntDels;
  /** key -> all delete events */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2AllDels;
  /** key -> final put events */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2ExtPuts;
  /** key -> intermediate put events */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2IntPuts;
  /** key -> all put events */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2AllPuts;
  /** key -> write events whose final writes touch the key (put or delete) */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2ExtWriteTxns;
  /** key -> write events whose intermediate writes touch the key */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2IntWriteTxns;
  /** key -> write events for any write to the key */
  @Getter
  private final Map<Key, Set<WriteEvent>> k2AllWriteTxns;
  /** txn ids that successfully execute at least one write (put/delete) */
  @Getter
  private final Set<Integer> updateTxns;
  private final boolean debug;

  private Map<Key, Set<Integer>> k2ExtWriteTxnIdCache;
  private Map<Key, Set<Integer>> k2IntWriteTxnIdCache;
  private Map<Key, Set<Integer>> k2AllWriteTxnIdCache;

  public WriteMetadata(
      Map<Key, Map<Key, Set<WriteEvent>>> extWrites,
      Map<Key, Map<Key, Set<WriteEvent>>> intWrites,
      Map<Key, Map<Key, Set<WriteEvent>>> allWrites,
      Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> extWritesByCategory,
      Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> intWritesByCategory,
      Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> allWritesByCategory,
      Map<Key, Set<WriteEvent>> k2ExtDels,
      Map<Key, Set<WriteEvent>> k2IntDels,
      Map<Key, Set<WriteEvent>> k2AllDels,
      Map<Key, Set<WriteEvent>> k2ExtPuts,
      Map<Key, Set<WriteEvent>> k2IntPuts,
      Map<Key, Set<WriteEvent>> k2AllPuts,
      Map<Key, Set<WriteEvent>> k2ExtWriteTxns,
      Map<Key, Set<WriteEvent>> k2IntWriteTxns,
      Map<Key, Set<WriteEvent>> k2AllWriteTxns,
      Set<Integer> updateTxns,
      boolean debug) {
    this.extWrites = extWrites;
    this.intWrites = intWrites;
    this.allWrites = allWrites;
    this.extWritesByCategory = extWritesByCategory;
    this.intWritesByCategory = intWritesByCategory;
    this.allWritesByCategory = allWritesByCategory;
    this.k2ExtDels = k2ExtDels;
    this.k2IntDels = k2IntDels;
    this.k2AllDels = k2AllDels;
    this.k2ExtPuts = k2ExtPuts;
    this.k2IntPuts = k2IntPuts;
    this.k2AllPuts = k2AllPuts;
    this.k2ExtWriteTxns = k2ExtWriteTxns;
    this.k2IntWriteTxns = k2IntWriteTxns;
    this.k2AllWriteTxns = k2AllWriteTxns;
    this.updateTxns = updateTxns;
    this.debug = debug;
  }

  /**
   * Creates an empty WriteMetadata instance with no write events.
   * Useful for testing or initializing ASG without any write operations.
   *
   * @return An immutable empty WriteMetadata instance
   */
  public static WriteMetadata empty() {
    return emptyWithUpdateTxns(Set.of());
  }

  /**
   * Creates an empty WriteMetadata instance with specified update transactions.
   * Useful for testing scenarios where you need to mark certain transactions as updates
   * without populating actual write events.
   *
   * @param updateTxns Set of transaction IDs to mark as update transactions
   * @return An immutable WriteMetadata instance with no write events but specified update txns
   */
  public static WriteMetadata emptyWithUpdateTxns(Set<Integer> updateTxns) {
    // Create separate empty maps for each logically distinct collection
    Map<Key, Map<Key, Set<WriteEvent>>> emptyExtWrites = Map.of();
    Map<Key, Map<Key, Set<WriteEvent>>> emptyIntWrites = Map.of();
    Map<Key, Map<Key, Set<WriteEvent>>> emptyAllWrites = Map.of();

    // Helper to create empty category map
    Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> emptyExtCategory = emptyCategoryMap();
    Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> emptyIntCategory = emptyCategoryMap();
    Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> emptyAllCategory = emptyCategoryMap();

    Map<Key, Set<WriteEvent>> emptyExtDels = Map.of();
    Map<Key, Set<WriteEvent>> emptyIntDels = Map.of();
    Map<Key, Set<WriteEvent>> emptyAllDels = Map.of();
    Map<Key, Set<WriteEvent>> emptyExtPuts = Map.of();
    Map<Key, Set<WriteEvent>> emptyIntPuts = Map.of();
    Map<Key, Set<WriteEvent>> emptyAllPuts = Map.of();
    Map<Key, Set<WriteEvent>> emptyExtWriteTxns = Map.of();
    Map<Key, Set<WriteEvent>> emptyIntWriteTxns = Map.of();
    Map<Key, Set<WriteEvent>> emptyAllWriteTxns = Map.of();

    return new WriteMetadata(
        emptyExtWrites, emptyIntWrites, emptyAllWrites,
        emptyExtCategory, emptyIntCategory, emptyAllCategory,
        emptyExtDels, emptyIntDels, emptyAllDels,
        emptyExtPuts, emptyIntPuts, emptyAllPuts,
        emptyExtWriteTxns, emptyIntWriteTxns, emptyAllWriteTxns,
        Set.copyOf(updateTxns),
        false);
  }

  /**
   * Helper method to create an empty category map with PUT and DELETE entries.
   */
  private static Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> emptyCategoryMap() {
    return Map.of(
        KvOpType.PUT, Map.of(),
        KvOpType.DELETE, Map.of());
  }

  public static WriteMetadata collect(KVHistory history, Config cfg) {
    boolean debug = cfg.DEBUG;
    Map<Key, Map<Key, Set<WriteEvent>>> extWrites = MapFactory.getEmptyMap(debug);
    Map<Key, Map<Key, Set<WriteEvent>>> intWrites = MapFactory.getEmptyMap(debug);
    Map<Key, Map<Key, Set<WriteEvent>>> allWrites = MapFactory.getEmptyMap(debug);
    Set<Integer> updateTxnIds = new HashSet<>();

    // Build lazily so each index (ext/int/all) gets its own pair of op-type maps.
    Function<Boolean, Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>>> categoryFactory = dbg -> Map.of(
        KvOpType.PUT, MapFactory.getEmptyMap(dbg),
        KvOpType.DELETE, MapFactory.getEmptyMap(dbg));
    Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> extByCategory = categoryFactory.apply(debug);
    Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> intByCategory = categoryFactory.apply(debug);
    Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> allByCategory = categoryFactory.apply(debug);

    for (KVTxn txn : history) {
      Map<Key, KvOperation> finalWrites = MapFactory.getEmptyMap(debug);
      boolean txnIsUpdate = false;

      for (int i = txn.getMops().size() - 1; i >= 0; i--) {
        KvOperation mop = txn.getKthOperation(i);

        if (mop.isWrite()) {
          Key key = mop.getKey();
          Key value = mop.getValue();

          if (mop.isSucc()) {
            txnIsUpdate = true;
            if (finalWrites.containsKey(key)) {
              // Intermediate write: this write is later overwritten
              addWrite(intWrites, intByCategory, allByCategory, allWrites,
                  key, value, txn.getTxnId(), i, mop.getOpType(), false, debug);
            } else {
              // External write: this is the final write to this key
              finalWrites.put(key, mop);
              addWrite(extWrites, extByCategory, allByCategory, allWrites,
                  key, value, txn.getTxnId(), i, mop.getOpType(), true, debug);
            }
          }
        }
      }

      if (txnIsUpdate) {
        updateTxnIds.add(txn.getTxnId());
      }
    }

    Map<Key, Set<WriteEvent>> k2ExtPuts = collectWritesByKey(extByCategory.get(KvOpType.PUT), debug);
    Map<Key, Set<WriteEvent>> k2ExtDels = collectWritesByKey(extByCategory.get(KvOpType.DELETE), debug);
    Map<Key, Set<WriteEvent>> k2IntPuts = collectWritesByKey(intByCategory.get(KvOpType.PUT), debug);
    Map<Key, Set<WriteEvent>> k2IntDels = collectWritesByKey(intByCategory.get(KvOpType.DELETE), debug);

    Map<Key, Set<WriteEvent>> k2AllPuts = collectWritesByKey(allByCategory.get(KvOpType.PUT), debug);
    Map<Key, Set<WriteEvent>> k2AllDels = collectWritesByKey(allByCategory.get(KvOpType.DELETE), debug);

    Map<Key, Set<WriteEvent>> k2ExtWriteTxns = mergeWriteEventsByKeys(List.of(k2ExtDels, k2ExtPuts), debug);
    Map<Key, Set<WriteEvent>> k2IntWriteTxns = mergeWriteEventsByKeys(List.of(k2IntDels, k2IntPuts), debug);
    Map<Key, Set<WriteEvent>> k2AllWriteTxns = mergeWriteEventsByKeys(List.of(k2AllDels, k2AllPuts), debug);

    Set<Integer> updateTxns = Set.copyOf(updateTxnIds);

    return new WriteMetadata(extWrites, intWrites, allWrites, extByCategory, intByCategory, allByCategory,
        k2ExtDels, k2IntDels, k2AllDels,
        k2ExtPuts, k2IntPuts, k2AllPuts,
        k2ExtWriteTxns, k2IntWriteTxns, k2AllWriteTxns,
        updateTxns,
        debug);
  }

  public Map<Key, Map<Integer, Set<WriteEvent>>> writesByCategory(KvOpType type, boolean external) {
    return external ? extWritesByCategory.get(type) : intWritesByCategory.get(type);
  }

  public Map<Key, Map<Integer, Set<WriteEvent>>> allWritesByCategory(KvOpType type) {
    return allWritesByCategory.get(type);
  }

  public Map<Key, Set<Integer>> k2ExtWriteTxnIds() {
    if (k2ExtWriteTxnIdCache == null) {
      k2ExtWriteTxnIdCache = mergeTxnIdsFromEventMaps(List.of(k2ExtWriteTxns), debug);
    }
    return k2ExtWriteTxnIdCache;
  }

  public Map<Key, Set<Integer>> k2IntWriteTxnIds() {
    if (k2IntWriteTxnIdCache == null) {
      k2IntWriteTxnIdCache = mergeTxnIdsFromEventMaps(List.of(k2IntWriteTxns), debug);
    }
    return k2IntWriteTxnIdCache;
  }

  public Map<Key, Set<Integer>> k2AllWriteTxnIds() {
    if (k2AllWriteTxnIdCache == null) {
      k2AllWriteTxnIdCache = mergeTxnIdsFromEventMaps(List.of(k2AllWriteTxns), debug);
    }
    return k2AllWriteTxnIdCache;
  }

  private static void addWrite(Map<Key, Map<Key, Set<WriteEvent>>> target,
      Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> byCategory,
      Map<KvOpType, Map<Key, Map<Integer, Set<WriteEvent>>>> allByCategory,
      Map<Key, Map<Key, Set<WriteEvent>>> allWrites,
      Key key,
      Key value,
      int txnId,
      int mopIndex,
      KvOpType type,
      boolean isExternal,
      boolean debug) {
    assert type == KvOpType.DELETE || type == KvOpType.PUT;

    if (value == null) {
      value = Key.getNullKey();
    }

    WriteEvent event = new WriteEvent(txnId, key, value, mopIndex, isExternal);

    target.computeIfAbsent(key, ignored -> MapFactory.getEmptyMap(debug))
        .computeIfAbsent(value, ignored -> MapFactory.getEmptySet(debug))
        .add(event);

    allWrites.computeIfAbsent(key, ignored -> MapFactory.getEmptyMap(debug))
        .computeIfAbsent(value, ignored -> MapFactory.getEmptySet(debug))
        .add(event);

    addTxnToWritesByCategory(byCategory.get(type), key, txnId, event, debug);
    addTxnToWritesByCategory(allByCategory.get(type), key, txnId, event, debug);
  }

  private static void addTxnToWritesByCategory(Map<Key, Map<Integer, Set<WriteEvent>>> writes,
      Key key,
      int txnId,
      WriteEvent event,
      boolean debug) {
    writes.computeIfAbsent(key, ignored -> MapFactory.getEmptyMap(debug))
        .computeIfAbsent(txnId, ignored -> new HashSet<>())
        .add(event);
  }

  private static Map<Key, Set<WriteEvent>> mergeWriteEventsByKeys(List<Map<Key, Set<WriteEvent>>> inputs,
      boolean debug) {
    Map<Key, Set<WriteEvent>> output = MapFactory.getEmptyMap(debug);

    for (var k2Writes : inputs) {
      for (var k : k2Writes.keySet()) {
        output.computeIfAbsent(k, ignored -> MapFactory.getEmptySet(debug))
            .addAll(k2Writes.get(k));
      }
    }

    return output;
  }

  /**
   * Flattens txn->event indexes into key->events for quick membership lookups.
   */
  private static Map<Key, Set<WriteEvent>> collectWritesByKey(
      Map<Key, Map<Integer, Set<WriteEvent>>> category,
      boolean debug) {
    Map<Key, Set<WriteEvent>> output = MapFactory.getEmptyMap(debug);
    for (var entry : category.entrySet()) {
      var flat = output.computeIfAbsent(entry.getKey(), ignored -> MapFactory.getEmptySet(debug));
      for (var events : entry.getValue().values()) {
        flat.addAll(events);
      }
    }
    return output;
  }

  private static Map<Key, Set<Integer>> mergeTxnIdsFromEventMaps(List<Map<Key, Set<WriteEvent>>> inputs,
      boolean debug) {
    Map<Key, Set<Integer>> output = MapFactory.getEmptyMap(debug);

    for (var k2Writes : inputs) {
      for (var entry : k2Writes.entrySet()) {
        var txnSet = output.computeIfAbsent(entry.getKey(), ignored -> MapFactory.getEmptySet(debug));
        for (WriteEvent event : entry.getValue()) {
          txnSet.add(event.getTxnId());
        }
      }
    }

    return output;
  }

  public Set<Key> writtenKeys() {
    return extWrites.keySet();
  }

  public boolean isUpdateTxn(int txnId) {
    return updateTxns.contains(txnId);
  }

  // ========== Query Methods for Specific Write Events ==========

  /**
   * Generic helper to check if a transaction exists in a write event set.
   */
  private boolean hasWriteEvent(Map<Key, Set<WriteEvent>> writeMap, Key key, int txnId) {
    Set<WriteEvent> events = writeMap.get(key);
    return events != null && events.stream().anyMatch(e -> e.getTxnId() == txnId);
  }

  /**
   * Generic helper to check if any write events exist for a key.
   */
  private boolean hasAnyWriteEvent(Map<Key, Set<WriteEvent>> writeMap, Key key) {
    Set<WriteEvent> events = writeMap.get(key);
    return events != null && !events.isEmpty();
  }

  /**
   * Generic helper to get write events excluding a specific transaction.
   */
  private Set<WriteEvent> getWriteEventsExcluding(Map<Key, Set<WriteEvent>> writeMap, Key key, int excludeTxnId) {
    Set<WriteEvent> events = writeMap.get(key);
    if (events == null || events.isEmpty()) {
      return Set.of();
    }
    return events.stream()
        .filter(e -> e.getTxnId() != excludeTxnId)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Generic helper to count write events for a key.
   */
  private int getWriteEventCount(Map<Key, Set<WriteEvent>> writeMap, Key key) {
    Set<WriteEvent> events = writeMap.get(key);
    return events == null ? 0 : events.size();
  }

  /**
   * Generic helper to get transaction IDs from write events.
   */
  private Set<Integer> getWriteEventTxnIds(Map<Key, Set<WriteEvent>> writeMap, Key key) {
    Set<WriteEvent> events = writeMap.get(key);
    if (events == null || events.isEmpty()) {
      return Set.of();
    }
    return events.stream()
        .map(WriteEvent::getTxnId)
        .collect(Collectors.toUnmodifiableSet());
  }

  // ========== Public API Using Generic Helpers ==========

  /**
   * Checks if a specific transaction has an external delete for the given key.
   */
  public boolean hasExternalDelete(Key key, int txnId) {
    return hasWriteEvent(k2ExtDels, key, txnId);
  }

  /**
   * Checks if a specific transaction has an external put for the given key.
   */
  public boolean hasExternalPut(Key key, int txnId) {
    return hasWriteEvent(k2ExtPuts, key, txnId);
  }

  /**
   * Checks if a specific transaction has any external write for the given key.
   */
  public boolean hasExternalWrite(Key key, int txnId) {
    return hasWriteEvent(k2ExtWriteTxns, key, txnId);
  }

  /**
   * Checks if a specific transaction has an intermediate delete for the given
   * key.
   */
  public boolean hasIntermediateDelete(Key key, int txnId) {
    return hasWriteEvent(k2IntDels, key, txnId);
  }

  /**
   * Checks if a specific transaction has an intermediate put for the given key.
   */
  public boolean hasIntermediatePut(Key key, int txnId) {
    return hasWriteEvent(k2IntPuts, key, txnId);
  }

  /**
   * Gets external deletes for a key, excluding a specific transaction.
   */
  public Set<WriteEvent> getExternalDeletesExcluding(Key key, int excludeTxnId) {
    return getWriteEventsExcluding(k2ExtDels, key, excludeTxnId);
  }

  /**
   * Gets external puts for a key, excluding a specific transaction.
   */
  public Set<WriteEvent> getExternalPutsExcluding(Key key, int excludeTxnId) {
    return getWriteEventsExcluding(k2ExtPuts, key, excludeTxnId);
  }

  /**
   * Gets external writes for a key, excluding a specific transaction.
   */
  public Set<WriteEvent> getExternalWritesExcluding(Key key, int excludeTxnId) {
    return getWriteEventsExcluding(k2ExtWriteTxns, key, excludeTxnId);
  }

  /**
   * Checks if there are any external deletes for a key.
   */
  public boolean hasAnyExternalDelete(Key key) {
    return hasAnyWriteEvent(k2ExtDels, key);
  }

  /**
   * Checks if there are any external puts for a key.
   */
  public boolean hasAnyExternalPut(Key key) {
    return hasAnyWriteEvent(k2ExtPuts, key);
  }

  /**
   * Checks if there are any external writes for a key.
   */
  public boolean hasAnyExternalWrite(Key key) {
    return hasAnyWriteEvent(k2ExtWriteTxns, key);
  }

  /**
   * Gets the count of external deletes for a key.
   */
  public int getExternalDeleteCount(Key key) {
    return getWriteEventCount(k2ExtDels, key);
  }

  /**
   * Gets the count of external puts for a key.
   */
  public int getExternalPutCount(Key key) {
    return getWriteEventCount(k2ExtPuts, key);
  }

  /**
   * Gets the count of all external writes for a key.
   */
  public int getExternalWriteCount(Key key) {
    return getWriteEventCount(k2ExtWriteTxns, key);
  }

  /**
   * Gets all transaction IDs that have external deletes for a key.
   */
  public Set<Integer> getExternalDeleteTxnIds(Key key) {
    return getWriteEventTxnIds(k2ExtDels, key);
  }

  /**
   * Gets all transaction IDs that have external puts for a key.
   */
  public Set<Integer> getExternalPutTxnIds(Key key) {
    return getWriteEventTxnIds(k2ExtPuts, key);
  }

  /**
   * Gets all transaction IDs that have external writes for a key.
   */
  public Set<Integer> getExternalWriteTxnIds(Key key) {
    return getWriteEventTxnIds(k2ExtWriteTxns, key);
  }

  /**
   * Gets external writes for a specific key-value pair by a specific transaction.
   *
   * @param key   The key
   * @param value The value written
   * @param txnId The transaction ID
   * @return The WriteEvent if found, null otherwise
   */
  public WriteEvent getExternalWrite(Key key, Key value, int txnId) {
    Map<Key, Set<WriteEvent>> valueMap = extWrites.get(key);
    if (valueMap == null) {
      return null;
    }
    Set<WriteEvent> events = valueMap.get(value);
    if (events == null) {
      return null;
    }
    return events.stream()
        .filter(event -> event.getTxnId() == txnId)
        .findFirst() // there should be only one
        .orElse(null);
  }

  /**
   * Gets all external write events for a specific key-value pair.
   * Returns empty set if no external writes found for the key-value combination.
   *
   * @param key The key to query
   * @param value The value to match
   * @return Unmodifiable set of external write events, never null
   */
  public Set<WriteEvent> getExternalWritesByValue(Key key, Key value) {
    return getWriteEventsByValue(extWrites, key, value);
  }

  /**
   * Gets all intermediate write events for a specific key-value pair.
   * Returns empty set if no intermediate writes found for the key-value combination.
   *
   * @param key The key to query
   * @param value The value to match
   * @return Unmodifiable set of intermediate write events, never null
   */
  public Set<WriteEvent> getIntermediateWritesByValue(Key key, Key value) {
    return getWriteEventsByValue(intWrites, key, value);
  }

  /**
   * Gets all write events (external + intermediate) for a specific key-value pair.
   * Returns empty set if no writes found for the key-value combination.
   *
   * @param key The key to query
   * @param value The value to match
   * @return Unmodifiable set of all write events, never null
   */
  public Set<WriteEvent> getAllWritesByValue(Key key, Key value) {
    return getWriteEventsByValue(allWrites, key, value);
  }

  // ========== Methods for Retrieving Complete Collections ==========

  /**
   * Generic helper to retrieve an unmodifiable set of write events from a map.
   * Returns empty set if no events found for the key.
   *
   * @param writeMap The map to query
   * @param key The key to look up
   * @return Unmodifiable set of write events, never null
   */
  private Set<WriteEvent> getWriteEventsForKey(Map<Key, Set<WriteEvent>> writeMap, Key key) {
    Set<WriteEvent> events = writeMap.get(key);
    return events == null ? Set.of() : Set.copyOf(events);
  }

  /**
   * Generic helper to retrieve write events from a nested map by key and value.
   * Returns empty set if no events found for the key-value pair.
   *
   * @param nestedWriteMap The nested map (key -> value -> events) to query
   * @param key The key to look up
   * @param value The value to match
   * @return Unmodifiable set of write events, never null
   */
  private Set<WriteEvent> getWriteEventsByValue(Map<Key, Map<Key, Set<WriteEvent>>> nestedWriteMap,
      Key key, Key value) {
    Map<Key, Set<WriteEvent>> valueMap = nestedWriteMap.get(key);
    if (valueMap == null) {
      return Set.of();
    }
    Set<WriteEvent> events = valueMap.get(value);
    return events == null ? Set.of() : Set.copyOf(events);
  }

  /**
   * Gets all external deletes for a key as an unmodifiable set.
   * Returns empty set if no deletes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of external delete events, never null
   */
  public Set<WriteEvent> getExternalDeletes(Key key) {
    return getWriteEventsForKey(k2ExtDels, key);
  }

  /**
   * Gets all external puts for a key as an unmodifiable set.
   * Returns empty set if no puts found.
   *
   * @param key The key to query
   * @return Unmodifiable set of external put events, never null
   */
  public Set<WriteEvent> getExternalPuts(Key key) {
    return getWriteEventsForKey(k2ExtPuts, key);
  }

  /**
   * Gets all external writes (puts + deletes) for a key as an unmodifiable set.
   * Returns empty set if no writes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of external write events, never null
   */
  public Set<WriteEvent> getExternalWrites(Key key) {
    return getWriteEventsForKey(k2ExtWriteTxns, key);
  }

  /**
   * Gets all intermediate deletes for a key as an unmodifiable set.
   * Returns empty set if no deletes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of intermediate delete events, never null
   */
  public Set<WriteEvent> getIntermediateDeletes(Key key) {
    return getWriteEventsForKey(k2IntDels, key);
  }

  /**
   * Gets all intermediate puts for a key as an unmodifiable set.
   * Returns empty set if no puts found.
   *
   * @param key The key to query
   * @return Unmodifiable set of intermediate put events, never null
   */
  public Set<WriteEvent> getIntermediatePuts(Key key) {
    return getWriteEventsForKey(k2IntPuts, key);
  }

  /**
   * Gets all writes (external + intermediate) for a key as an unmodifiable set.
   * Returns empty set if no writes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of all write events, never null
   */
  public Set<WriteEvent> getAllWrites(Key key) {
    return getWriteEventsForKey(k2AllWriteTxns, key);
  }

  /**
   * Gets all deletes (external + intermediate) for a key as an unmodifiable set.
   * Returns empty set if no deletes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of all delete events, never null
   */
  public Set<WriteEvent> getAllDeletes(Key key) {
    return getWriteEventsForKey(k2AllDels, key);
  }

  /**
   * Gets all puts (external + intermediate) for a key as an unmodifiable set.
   * Returns empty set if no puts found.
   *
   * @param key The key to query
   * @return Unmodifiable set of all put events, never null
   */
  public Set<WriteEvent> getAllPuts(Key key) {
    return getWriteEventsForKey(k2AllPuts, key);
  }

  // ========== Methods for Retrieving Transaction IDs Only ==========

  /**
   * Gets all transaction IDs (external + intermediate) that have any write for a key.
   * Returns empty set if no writes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of transaction IDs, never null
   */
  public Set<Integer> getAllWriteTxnIds(Key key) {
    return getWriteEventTxnIds(k2AllWriteTxns, key);
  }

  /**
   * Gets all transaction IDs that have intermediate writes for a key.
   * Returns empty set if no writes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of transaction IDs, never null
   */
  public Set<Integer> getIntermediateWriteTxnIds(Key key) {
    return getWriteEventTxnIds(k2IntWriteTxns, key);
  }

  /**
   * Gets all transaction IDs that have intermediate deletes for a key.
   * Returns empty set if no deletes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of transaction IDs, never null
   */
  public Set<Integer> getIntermediateDeleteTxnIds(Key key) {
    return getWriteEventTxnIds(k2IntDels, key);
  }

  /**
   * Gets all transaction IDs that have intermediate puts for a key.
   * Returns empty set if no puts found.
   *
   * @param key The key to query
   * @return Unmodifiable set of transaction IDs, never null
   */
  public Set<Integer> getIntermediatePutTxnIds(Key key) {
    return getWriteEventTxnIds(k2IntPuts, key);
  }

  /**
   * Gets all transaction IDs (external + intermediate) that have any delete for a key.
   * Returns empty set if no deletes found.
   *
   * @param key The key to query
   * @return Unmodifiable set of transaction IDs, never null
   */
  public Set<Integer> getAllDeleteTxnIds(Key key) {
    return getWriteEventTxnIds(k2AllDels, key);
  }

  /**
   * Gets all transaction IDs (external + intermediate) that have any put for a key.
   * Returns empty set if no puts found.
   *
   * @param key The key to query
   * @return Unmodifiable set of transaction IDs, never null
   */
  public Set<Integer> getAllPutTxnIds(Key key) {
    return getWriteEventTxnIds(k2AllPuts, key);
  }
}
