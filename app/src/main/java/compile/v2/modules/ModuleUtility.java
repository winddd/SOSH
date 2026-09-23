package compile.v2.modules;

import static graphs.edges.EdgeType.PRW;
import static graphs.edges.EdgeType.RW;
import static graphs.edges.EdgeType.WR;
import static graphs.edges.EdgeType.WW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import asg.ReadOperation;
import asg.VersionOrder;
import asg.WriteEvent;
import common.Key;
import compile.v2.inputs.GraphInputs;
import graphs.DirectDep;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import history.KVTxn;
import util.Config;
import util.Profiler;
import util.enumtypes.ISOLATION_LEVEL;
import util.enumtypes.MODE;
import util.exception.RejectException;
import util.isolation.RYOWPolicy;

public class ModuleUtility {
  protected Profiler profiler;
  protected MODE mode;
  protected Config cfg;

  public ModuleUtility(Profiler profiler, MODE mode, Config cfg) {
    this.profiler = profiler;
    this.mode = mode;
    this.cfg = cfg;
  }

  public static InCompleteGraph generate(GraphInputs inputs, InCompleteGraph txnGraph, List<GenDepModule> modules) {
    InCompleteGraph current = txnGraph;
    for (GenDepModule module : modules) {
      current = module.generate(inputs, current);
    }

    return current;
  }

  /**
   * Finds the most recent WriteEvent from a specific transaction that occurs
   * before a read operation by searching backwards through the transaction's operations.
   * This is more efficient than filtering a pre-collected list of WriteEvents.
   *
   * @param txn         The transaction to search within
   * @param key         The key to find writes for
   * @param readOpIndex The read operation index (exclusive upper bound)
   * @return An Optional containing the most recent prior WriteEvent from the
   *         transaction, or empty if none found
   */
  public static Optional<WriteEvent> findMostRecentPriorWriteFromTxn(KVTxn txn, Key key, int readOpIndex) {
    return txn.getLastWriteByKey(key, readOpIndex);
  }

  /**
   * Filters WriteEvents to exclude those whose transaction ID equals the given
   * txnId.
   * Returns a new list containing only WriteEvents from other transactions.
   *
   * @param writeEvents The list of WriteEvents to filter
   * @param txnId       The transaction ID to exclude
   * @return A new list containing only WriteEvents with non-matching transaction
   *         ID
   */
  public static List<WriteEvent> filterWriteEventsExcludingTxn(List<WriteEvent> writeEvents, int txnId) {
    return writeEvents.stream()
        .filter(event -> event.getTxnId() != txnId)
        .collect(Collectors.toList());
  }

  /**
   * Resolves which WriteEvents should be considered as valid sources for a read operation,
   * applying RYOW (Read Your Own Writes) policy filtering based on isolation level semantics.
   *
   * <p>This method performs three filtering steps:
   * <ol>
   *   <li>Filters out intermediate writes (keeps only external/committed writes)</li>
   *   <li>Checks for most recent prior write from the same transaction</li>
   *   <li>Applies RYOW policy:
   *     <ul>
   *       <li>MUST_RYOW: Returns only the transaction's own most recent write (if present)</li>
   *       <li>MUST_EXT: Returns only external writes (excludes same-transaction writes)</li>
   *       <li>EITHER: Returns all candidates (both self and external writes)</li>
   *     </ul>
   *   </li>
   * </ol>
   *
   * @param directDep The direct dependency containing candidate write events
   * @param txn The transaction containing the read operation
   * @param mode The verification mode (determines isolation level default RYOW policy)
   * @param ryowPolicyOverride Optional RYOW policy override; if null, uses mode's default
   * @return Filtered list of WriteEvents that satisfy the RYOW policy for this read
   * @throws RejectException if MUST_RYOW policy requires a self-write but none is found in candidates
   */
  public static List<WriteEvent> resolveCandidateWritesWithRyowPolicy(DirectDep directDep, KVTxn txn, MODE mode, RYOWPolicy ryowPolicyOverride) {
    List<WriteEvent> writeEvents = directDep.getCandidateWriteEvents();

    // Filter out intermediate write events - only keep external writes,
    // but keep writes from the same transaction even if intermediate
    writeEvents = writeEvents.stream()
      .filter(event -> (event.getTxnId() == directDep.txnId) || event.isExternal())
      .collect(java.util.stream.Collectors.toList());

    var mostRecentSelfWrite = ModuleUtility.findMostRecentPriorWriteFromTxn(txn, directDep.key,
      directDep.opIndex);

    // Determine RYOW policy: use override if present, otherwise use isolation level default
    RYOWPolicy ryowPolicy = (ryowPolicyOverride != null)
        ? ryowPolicyOverride
        : mode.spec().getReadOwnWritePolicy();

    // If MUST_RYOW and there is a most recent write in the read txn before the read
    // op.
    if (ryowPolicy == RYOWPolicy.MUST_RYOW
      && mostRecentSelfWrite.isPresent()) {
      // use only self write.
      if (writeEvents.contains(mostRecentSelfWrite.get())) {
        writeEvents = List.of(mostRecentSelfWrite.get());
      } else {
        throw new RejectException("No candidate writes");
      }
    } else if (ryowPolicy == RYOWPolicy.MUST_EXT) {
      // use only external writes to create a superposition.
      writeEvents = ModuleUtility.filterWriteEventsExcludingTxn(writeEvents, directDep.txnId);
    }

    // Otherwise,
    // use both self most-recent write and external writes to create a
    // superposition.
    // writeEvents remains unchanged - includes all candidates
    return writeEvents;
  }

  public Set<TypeEdge> boomslangComputeKnownWwRwEdges(Set<Key> K, VersionOrder vo,
      Map<Pair<Key, Integer>, Set<ReadOperation>> readFrom, boolean skipPrwEdges) {
    var wwRwEdges = new HashSet<TypeEdge>();
    for (Key key : K) {
      var chainsOfKey = vo.getPartialOrder(key);

      for (int i = 0; i < chainsOfKey.size(); i++) {
        var chain = chainsOfKey.get(i);
        // transitive ww edges
        for (int j = 0; j < chain.size() - 1; j++)
          for (int l = j + 1; l < chain.size(); l++) {
            var writeTxnId = chain.get(j).getTxnId();
            var laterWriteTxnId = chain.get(l).getTxnId();
            // debug
            // if (Config.get().DEBUG && targetEdges.contains(Pair.of(tj, laterWriteTxnId))) {
            // System.out.print("");
            // }
            wwRwEdges.add(new TypeEdge(GraphNodeId.txn(writeTxnId), GraphNodeId.txn(laterWriteTxnId), WW, key));
            // RW
            if (includeAntiDependencies()) {
              var readFromKey = Pair.of(key, writeTxnId);
              if (readFrom.containsKey(readFromKey) && !readFrom.get(readFromKey).isEmpty()) {
                for (var readOp : readFrom.get(readFromKey)) { // known RW edges inside chains
                  var readTxnId = readOp.txnId();
                  if (readTxnId != writeTxnId && readTxnId != laterWriteTxnId) {
                    // Use the edge type from ReadOperation (RW or PRW based on original read type)
                    var rwEdgeType = (readOp.edgeType() == WR) ? RW : PRW;
//                    if (readTxnId == 4930 && laterWriteTxnId == 1158) {
//                      System.out.println();
//                    }
                    if ((!skipPrwEdges) || rwEdgeType != PRW) {
                      wwRwEdges.add(new TypeEdge(GraphNodeId.txn(readTxnId), GraphNodeId.txn(laterWriteTxnId), rwEdgeType, key));
                    }
                  }
                }
              }
            }
          }
      }
    }
    return wwRwEdges;
  }

  /**
   * Generate WW edges across two chains of same key.
   * This is different from Cobra's GenChain2ChainEdges because it will also
   * include anti edges.
   *
   * @param chain1
   * @param chain2
   * @param key
   * @return
   */
  private Set<TypeEdge> boomslangGenChain2ChainEdges(List<KVTxn> chain1, List<KVTxn> chain2, Key key,
      Map<Pair<Key, Integer>, Set<ReadOperation>> readFrom, boolean skipPrwEdges) {
    var es = new HashSet<TypeEdge>();
    for (int i = 0; i < chain1.size(); i++)
      for (int j = 0; j < chain2.size(); j++) {
        var writeTxnId = chain1.get(i).getTxnId();
        var laterWriteTxnId = chain2.get(j).getTxnId();
        if (laterWriteTxnId == 0) { // this is impossible, so we prune this ahead of time.
          return null;
        }

        if (writeTxnId == 1158 && laterWriteTxnId == 4930) {
          System.out.println();
        }
        es.add(new TypeEdge(GraphNodeId.txn(writeTxnId), GraphNodeId.txn(laterWriteTxnId), WW, key));
        // RW edges across chains
        if (includeAntiDependencies()) {
          var readFromKey = Pair.of(key, writeTxnId);
          if (readFrom.containsKey(readFromKey) && !readFrom.get(readFromKey).isEmpty()) {
            for (var readOp : readFrom.get(readFromKey)) {
              var readTxnId = readOp.txnId();
              if (readTxnId != laterWriteTxnId) {
                // Use the edge type from ReadOperation (RW or PRW based on original read type)
                var rwEdgeType = (readOp.edgeType() == WR) ? RW : PRW;
                if ((!skipPrwEdges) || rwEdgeType != PRW) {
                  es.add(new TypeEdge(GraphNodeId.txn(readTxnId), GraphNodeId.txn(laterWriteTxnId), rwEdgeType, key));
                }
              }
            }
          }
        }
      }

    return es;
  }

  public Pair<List<Superposition>, List<TypeEdge>> boomslangComputeUnknownWwRw(Set<Key> K,
      VersionOrder vo,
      Map<Pair<Key, Integer>, Set<ReadOperation>> readFrom, boolean skipPrwEdges) {
    var unknownWwSuperpositions = new ArrayList<Superposition>();
    var knownEdges = new ArrayList<TypeEdge>();

    for (Key key : K) {
      var kChains = vo.getPartialOrder(key);
      var numChains = kChains.size();

      for (int i = 0; i < numChains - 1; i++) {
        for (int j = i + 1; j < numChains; j++) {
          var chain1 = kChains.get(i);
          var chain2 = kChains.get(j);
          var es1 = boomslangGenChain2ChainEdges(chain1, chain2, key, readFrom, skipPrwEdges);
          var es2 = boomslangGenChain2ChainEdges(chain2, chain1, key, readFrom, skipPrwEdges);
          if (es1 == null && es2 != null) {
            knownEdges.addAll(es2);
          } else if (es2 == null && es1 != null) {
            knownEdges.addAll(es1);
          } else if (es1 != null && es2 != null) {
            var superposition = new Superposition(es1, es2);
            unknownWwSuperpositions.add(superposition);
          } else {
            assert false;
          }

          // if (unknownWwSuperpositions.size() % 10000 == 0)
          // System.out.printf("knownEdge.size()=%d, unknownWWSuperpositions.size()=%d\n",
          // knownEdges.size(), unknownWwSuperpositions.size());
          // var superposition = new Superposition(es1, es2);
          // unknownWwSuperpositions.add(superposition);
        }
      }
    }

    return Pair.of(unknownWwSuperpositions, knownEdges);
  }

  public Set<Imply> boomslangComputeImply(DirectDep directDep) {
    Set<Imply> implies = new HashSet<>();

    if (directDep.getCandidateWriteEvents().size() == 1) {
      // if this is a known wr dependency, then we have already inferred its
      // corresponding RW edges previously.
      return implies;
    }

    for (var writeEvent : directDep.getCandidateWriteEvents()) {
      var writeTxnId = writeEvent.getTxnId();
      var wrEdge = new TypeEdge(GraphNodeId.txn(writeTxnId), GraphNodeId.txn(directDep.txnId), directDep.edgeType,
          directDep.key);

      for (int Tk : directDep.allWriteTxnIds) {
        if (Tk != writeTxnId && Tk != directDep.txnId) {
          var wwEdge = new TypeEdge(GraphNodeId.txn(writeTxnId), GraphNodeId.txn(Tk), WW, directDep.key);
          var rwEdgeType = (directDep.edgeType == WR ? RW : PRW);
          var rwEdge = new TypeEdge(GraphNodeId.txn(directDep.txnId), GraphNodeId.txn(Tk), rwEdgeType,
              directDep.key);
          Imply imply = new Imply(wrEdge, wwEdge, rwEdge);
          implies.add(imply);
        }
      }
    }

    return implies;
  }

  private boolean includeAntiDependencies() {
    var isolation = mode.getIsolationLevel();
    return isolation != ISOLATION_LEVEL.READ_UNCOMMITTED
        && isolation != ISOLATION_LEVEL.READ_COMMITTED;
  }
}
