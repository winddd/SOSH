package asg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import common.Key;
import graphs.DirectDep;
import graphs.edges.TypeEdge;
import graphs.nodes.GraphNodeId;

/**
 * Read index that provides WR edges and readFrom mappings derived from direct dependencies.
 * Used by v1 compilers and certain v2 modules for computing WW/RW edges.
 */
public final class ReadIndex {
  /**
   * Concrete WR edges (writer txn -> reader txn/action) derived from direct deps.
   */
  private final List<TypeEdge> wrEdges;
  /**
   * Map of (key, writerTxn) -> logical read operations that consumed that writer.
   * Should be only used by Cobra/Viper/PolySI.
   * Not suggested for Boomslang compilers to use them.
   * Boomslang needs to consider the RYOW policy and not only external writes.
   */
  private final Map<Pair<Key, Integer>, Set<ReadOperation>> readFrom;

  /** Internal constructor that snapshots the edge lists/maps. */
  private ReadIndex(List<TypeEdge> wrEdges,
      Map<Pair<Key, Integer>, Set<ReadOperation>> readFrom) {
    this.wrEdges = List.copyOf(wrEdges);
    this.readFrom = wrapReadFrom(readFrom);
  }

  /**
   * Builds an index from direct dependencies.
   * For each DirectDep with a unique external candidate write, produces a WR edge
   * and readFrom entry. This is used by Cobra/Viper/PolySI which assume unique values.
   */
  public static ReadIndex from(Collection<DirectDep> directDeps) {
    List<TypeEdge> edges = new ArrayList<>();
    Map<Pair<Key, Integer>, Set<ReadOperation>> readMap = new HashMap<>();

    for (DirectDep dep : directDeps) {
      // produce known wr edges by unique value assumption.
      // This will be used later to build anti dependencies in GenWRiteDepModule.
      List<WriteEvent> candidateWrites = dep.getCandidateWriteEvents();
      if (candidateWrites.size() != 1) {
        continue;
      }
      int writeTxn = candidateWrites.get(0).getTxnId();
      edges.add(new TypeEdge(GraphNodeId.txn(writeTxn), GraphNodeId.txn(dep.txnId), dep.edgeType, dep.key));

//      if(dep.key.toString().equals("gAAAAAAARRw=") && writeTxn == 4461) {
//        System.out.println();
//      }
//
//      if(dep.key.toString().equals("gAAAAAAARR4=") && writeTxn == 0) {
//        System.out.println();
//      }
      var mapKey = Pair.of(dep.key, writeTxn);
      ReadOperation readOp = new ReadOperation(dep.txnId, dep.opIndex, dep.edgeType);
      readMap.computeIfAbsent(mapKey, k -> new HashSet<>()).add(readOp);
    }

    return new ReadIndex(edges, readMap);
  }

  /** Wraps the read-from map in unmodifiable copies for callers. */
  private static Map<Pair<Key, Integer>, Set<ReadOperation>> wrapReadFrom(
      Map<Pair<Key, Integer>, Set<ReadOperation>> readFrom) {
    Map<Pair<Key, Integer>, Set<ReadOperation>> copy = new HashMap<>(readFrom.size());
    readFrom.forEach((key, value) -> copy.put(key, Collections.unmodifiableSet(new HashSet<>(value))));
    return Collections.unmodifiableMap(copy);
  }

  public List<TypeEdge> wrEdges() {
    return wrEdges;
  }

  /** Returns the immutable map of (key, writer) -> reader operations. */
  public Map<Pair<Key, Integer>, Set<ReadOperation>> readFrom() {
    return readFrom;
  }

  /**
   * Legacy view that only exposes writer txn ids instead of full read operations.
   */
  public Map<Pair<Key, Integer>, Set<Integer>> toLegacyReadFrom() {
    Map<Pair<Key, Integer>, Set<Integer>> legacy = new HashMap<>(readFrom.size());
    readFrom.forEach((key, value) -> legacy.put(key,
        Collections.unmodifiableSet(value.stream().map(ReadOperation::txnId).collect(Collectors.toSet()))));
    return Collections.unmodifiableMap(legacy);
  }
}
