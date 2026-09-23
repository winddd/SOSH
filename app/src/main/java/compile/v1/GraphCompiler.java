package compile.v1;

import static graphs.edges.EdgeType.RW;
import static graphs.edges.EdgeType.WW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

import asg.ASG;
import asg.ReadIndex;
import asg.VersionOrder;
import asg.WriteEvent;
import common.Key;
import graphs.DirectDep;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import history.KVTxn;
import lombok.extern.slf4j.Slf4j;
import util.Config;
import util.Profiler;
import util.enumtypes.ISOLATION_LEVEL;
import util.enumtypes.MODE;

@Slf4j
public abstract class GraphCompiler {
  protected Profiler profiler;
  protected MODE mode;
  protected Config cfg;
  // debug
  List<Integer> cycle = List.of();
  Set<Pair<Integer, Integer>> targetEdges = new HashSet<>();
  private Set<Key> targetKeys = new HashSet<>(List.of());

  public GraphCompiler(Config cfg) {
    this.profiler = Profiler.getInstance();
    this.cfg = cfg;
    this.mode = cfg.RUNMODE;
    // debug
    if (cfg.DEBUG) {
      for (int i = 0; i < cycle.size() - 1; i++) {
        int next = (i + 1) % cycle.size();
        targetEdges.add(Pair.of(cycle.get(i), cycle.get(next)));
      }
    }
  }

  public abstract InCompleteGraph compile(ASG ASG);

  protected Set<Imply> boomslangComputeImply(DirectDep directDep) {
    Set<Imply> implies = new HashSet<>();

    List<WriteEvent> externalCandidates = directDep.externalCandidateWrites();
    if (externalCandidates.size() == 1) {
      // if this is a known wr dependency, then we have already inferred its
      // corresponding RW edges previously.
      return implies;
    }

    for (WriteEvent candidate : externalCandidates) {
      int writeTxn = candidate.getTxnId();
      assert writeTxn != directDep.txnId;
      var wrEdge = new TypeEdge(GraphNodeId.txn(writeTxn), GraphNodeId.txn(directDep.txnId),
          directDep.edgeType, directDep.key);

      for (int Tk : directDep.allWriteTxnIds) {
        if (Tk != writeTxn && Tk != directDep.txnId) {
          var wwEdge = new TypeEdge(GraphNodeId.txn(writeTxn), GraphNodeId.txn(Tk), WW, directDep.key);
          var rwEdge = new TypeEdge(GraphNodeId.txn(directDep.txnId), GraphNodeId.txn(Tk), RW,
              directDep.key);
          Imply imply = new Imply(wrEdge, wwEdge, rwEdge);
          implies.add(imply);
        }
      }
    }

    return implies;
  }

  /**
   * Generate WW edges across two chains of same key.
   * This is different from Cobra's GenChain2ChainEdges because it will also
   * includes anti edges.
   *
   * @param chain1
   * @param chain2
   * @param key
   * @return
   */
  private Set<TypeEdge> boomslangGenChain2ChainEdges(List<KVTxn> chain1, List<KVTxn> chain2, Key key,
      Map<Pair<Key, Integer>, Set<Integer>> readFrom) {
    var es = new HashSet<TypeEdge>();
    for (int i = 0; i < chain1.size(); i++)
      for (int j = 0; j < chain2.size(); j++) {
        var ti = chain1.get(i).getTxnId();
        var tj = chain2.get(j).getTxnId();
        if (tj == 0) { // this is impossible, so we prune this ahead of time.
          return null;
        }

        es.add(new TypeEdge(GraphNodeId.txn(ti), GraphNodeId.txn(tj), WW, key));
        // RW edges across chains
        var isolation = mode.getIsolationLevel();
        if ((isolation != ISOLATION_LEVEL.READ_COMMITTED)
            && (isolation != ISOLATION_LEVEL.READ_UNCOMMITTED)) {
          var readFromKey = Pair.of(key, ti);
          if (readFrom.containsKey(readFromKey) && !readFrom.get(readFromKey).isEmpty()) {
            for (var readTxnId : readFrom.get(readFromKey)) {
              if (readTxnId != tj) {
                es.add(new TypeEdge(GraphNodeId.txn(readTxnId), GraphNodeId.txn(tj), RW, key));
              }
            }
          }
        }
      }

    return es;
  }

  protected Set<TypeEdge> boomslangComputeKnownWwRwEdges(Set<Key> K, VersionOrder vo,
      Map<Pair<Key, Integer>, Set<Integer>> readFrom) {
    var wwRwEdges = new HashSet<TypeEdge>();
    for (Key key : K) {
      var chainsOfKey = vo.getPartialOrder(key);

      for (int i = 0; i < chainsOfKey.size(); i++) {
        var chain = chainsOfKey.get(i);
        // transitive ww edges
        for (int j = 0; j < chain.size() - 1; j++)
          for (int l = j + 1; l < chain.size(); l++) {
            var tj = chain.get(j).getTxnId();
            var tl = chain.get(l).getTxnId();
            // debug
            // if (Config.get().DEBUG && targetEdges.contains(Pair.of(tj, tl))) {
            // System.out.print("");
            // }
            wwRwEdges.add(new TypeEdge(GraphNodeId.txn(tj), GraphNodeId.txn(tl), WW, key));
            // RW
            var isolation = mode.getIsolationLevel();
            if ((isolation != ISOLATION_LEVEL.READ_COMMITTED)
                && (isolation != ISOLATION_LEVEL.READ_UNCOMMITTED)) {
              var readFromKey = Pair.of(key, tj);
              if (readFrom.containsKey(readFromKey) && !readFrom.get(readFromKey).isEmpty()) {
                for (var readTxn : readFrom.get(readFromKey)) { // known RW edges inside chains
                  if (readTxn != tj && readTxn != tl) {
                    wwRwEdges.add(new TypeEdge(GraphNodeId.txn(readTxn), GraphNodeId.txn(tl), RW, key));
                  }
                }
              }
            }
          }
      }
    }
    return wwRwEdges;
  }

  protected Pair<List<Superposition>, List<TypeEdge>> boomslangComputeUnknownWwRw(Set<Key> K,
      VersionOrder vo,
      Map<Pair<Key, Integer>, Set<Integer>> readFrom) {
    var unknownWwSuperpositions = new ArrayList<Superposition>();
    var knownEdges = new ArrayList<TypeEdge>();

    for (Key key : K) {
      var kChains = vo.getPartialOrder(key);
      var numChains = kChains.size();

      for (int i = 0; i < numChains - 1; i++) {
        for (int j = i + 1; j < numChains; j++) {
          var chain1 = kChains.get(i);
          var chain2 = kChains.get(j);
          var es1 = boomslangGenChain2ChainEdges(chain1, chain2, key, readFrom);
          var es2 = boomslangGenChain2ChainEdges(chain2, chain1, key, readFrom);
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

  protected Set<TypeEdge> cobraComputeDirectWwEdges(Set<Key> K, VersionOrder vo) {
    // Cobra doesn't need this because WR inferred from RMW txns can already
    // guarantee the consecutive WW edges.
    throw new NotImplementedException("");
  }

  protected Set<TypeEdge> cobraGenChain2ChainEdges(List<KVTxn> chain1, List<KVTxn> chain2, Key key,
      Map<Pair<Key, Integer>, Set<Integer>> readFrom) {
    var es = new HashSet<TypeEdge>();
    assert !chain1.isEmpty();
    var lastTxnChain1 = getLastTxnFromChain(chain1);
    var firstTxnChain2 = getFirstTxnFromChain(chain2);
    var readFromKey = Pair.of(key, lastTxnChain1.getTxnId());

    if (!readFrom.containsKey(readFromKey) || readFrom.get(readFromKey).isEmpty()) {
      es.add(new TypeEdge(
          GraphNodeId.txn(lastTxnChain1.getTxnId()),
          GraphNodeId.txn(firstTxnChain2.getTxnId()),
          WW, key));
      return es;
    }

    for (var readTxn : readFrom.get(readFromKey)) {
      es.add(new TypeEdge(GraphNodeId.txn(readTxn), GraphNodeId.txn(firstTxnChain2.getTxnId()), RW, key));
    }

    return es;
  }

  protected KVTxn getLastTxnFromChain(List<KVTxn> chain) {
    assert !chain.isEmpty();
    int n = chain.size();
    return chain.get(n - 1);
  }

  protected KVTxn getFirstTxnFromChain(List<KVTxn> chain) {
    assert !chain.isEmpty();
    return chain.get(0);
  }

  protected Set<TypeEdge> inferRwEdges(Set<Key> K, VersionOrder vo,
      Map<Pair<Key, Integer>, Set<Integer>> readFrom) {
    var antiEdgeSet = new HashSet<TypeEdge>();

    for (var key : K) {
      var kChains = vo.getPartialOrder(key); // List<List<KVTxn>>

      // debug
      // if (Config.get().DEBUG && targetKeys.contains(key)) {
      // System.out.print("");
      // }
      for (var chain : kChains) {
        for (int i = 0; i < chain.size() - 1; i++) {
          var readFromKey = Pair.of(key, chain.get(i).getTxnId());
          if (!readFrom.containsKey(readFromKey) || readFrom.get(readFromKey).isEmpty()) {
            continue;
          }

          var readTxns = readFrom.get(readFromKey);
          for (int readTxn : readTxns) {
            // debug
            // if (key.equals(new Key(new byte[]{-72, -121, 58, -88, 46, -10, -58, -45})) &&
            // readTxn == 1002) {
            // System.out.print("");
            // }
            if (readTxn != chain.get(i + 1).getTxnId()) {
              antiEdgeSet
                  .add(new TypeEdge(GraphNodeId.txn(readTxn), GraphNodeId.txn(chain.get(i + 1).getTxnId()), RW, key));
            }
          }
        }
      }
    }

    return antiEdgeSet;
  }

  protected List<Superposition> cobraComputeCoalescedConstraints(Set<Key> K, VersionOrder vo,
      Map<Pair<Key, Integer>, Set<Integer>> readFrom) {
    var cons = new ArrayList<Superposition>();

    for (Key key : K) {
      var kChains = vo.getPartialOrder(key);
      var nChains = kChains.size();

      for (int i = 0; i < nChains - 1; i++) {
        for (int j = i + 1; j < nChains; j++) {
          var chain1 = kChains.get(i);
          var chain2 = kChains.get(j);
          // profiler.startTick("cobraGenChain2ChainEdges");
          var es1 = cobraGenChain2ChainEdges(chain1, chain2, key, readFrom);
          var es2 = cobraGenChain2ChainEdges(chain2, chain1, key, readFrom);
          // profiler.endTick("cobraGenChain2ChainEdges");
          var con = new Superposition(List.of(es1, es2));
          cons.add(con);
        }
      }
    }

    return cons;
  }

  /**
   * Assumption: unique value.
   *
   * Builds ReadIndex on-demand from ASG's direct dependencies.
   * ReadIndex is no longer stored in ASG to keep it focused on core data structures.
   */
  protected Pair<List<TypeEdge>, Map<Pair<Key, Integer>, Set<Integer>>> computeReadFromMap(ASG asg) {
    ReadIndex index = ReadIndex.from(asg.getDependencyStore().directDeps());
    return Pair.of(index.wrEdges(), index.toLegacyReadFrom());
  }

  // protected Map<Pair<Key, Integer>, Set<Integer>>
  // computeReadFromMap(Collection<DirectDep> directDeps, InCompleteGraph graph) {
  // // traverse all the DirectDeps to construct a map
  // // <Key, ti> => all the txn that reads from key k from ti
  // Map<Pair<Key, Integer>, Set<Integer>> readFrom =
  // MapFactory.getEmptyMap(cfg.DEBUG);
  // int ithDirectDep = 0;
  // for (var directDep : directDeps) {
  // // debug
  // // if (ithDirectDep % 100 == 0) {
  // // log.debug("processing %d DirectDep", ithDirectDep);
  // // }
  // log.debug("processing %d DirectDep", ithDirectDep);
  // // PolySI assumes unique values.
  // assert directDep.externalCandidateWrites().size() == 1;
  // int writeTxn = directDep.externalCandidateWrites().get(0).getTxnId();
  // var wrEdge = new TypeEdge(GraphNodeId.txn(writeTxn),
  // GraphNodeId.txn(directDep.readTxnId), directDep.edgeType, directDep.key);
  // graph.addEdge(cfg.RUNMODE.getIsolationLevel() ==
  // ISOLATION_LEVEL.SNAPSHOT_ISOLATION? wrEdge.toBCEdge() : wrEdge);

  // var mapKey = Pair.of(directDep.key, writeTxn);
  // if (!readFrom.containsKey(mapKey)) {
  // readFrom.put(mapKey, new HashSet<>());
  // }

  // readFrom.get(mapKey).add(directDep.readTxnId);
  // ithDirectDep ++;
  // }

  // return readFrom;
  // }
}
