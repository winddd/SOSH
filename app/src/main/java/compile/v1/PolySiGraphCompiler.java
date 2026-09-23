package compile.v1;

import com.google.common.graph.ValueGraphBuilder;
import common.Key;
import graphs.constraints.Superposition;
import graphs.constraints.GeneralizedConstraint;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.PolySIOriginalGraph;
import graphs.nodes.GraphNodeId;
import asg.ASG;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import com.google.common.graph.MutableValueGraph;
import util.Config;

import java.util.*;

import static graphs.edges.EdgeType.RW;
import static graphs.edges.EdgeType.WW;

@Slf4j
public class PolySiGraphCompiler extends GraphCompiler {
  @Getter
  protected Set<GeneralizedConstraint> generalCons;

  public PolySiGraphCompiler(Config cfg) {
    super(cfg);
  }

  @Override
  public InCompleteGraph compile(ASG asg) {
    profiler.startTick("graphcompile");
    GraphCompilerUtil.checkIntermediateReads(asg, mode, cfg);

    var txnIds = new HashSet<GraphNodeId>();
    for (int i = 0; i < asg.getNumTxns(); i++) {
      txnIds.add(GraphNodeId.txn(i));
    }

//    Polygraph g = new Polygraph(txnIds, cfg);
    PolySIOriginalGraph g = new PolySIOriginalGraph(txnIds, cfg);
    int numCons = 0;

    // known edges
    for (var edge : asg.getDependencyStore().edges()) { // for all isolation levels
      g.addEdge(edge);
    }
    // traverse all the DirectDeps to construct a map
    // <Key, ti> => all the txn that reads from key k from ti
    var pair = computeReadFromMap(asg);
    var wrEdges = pair.getLeft();
    Map<Pair<Key, Integer>, Set<Integer>> readFrom = pair.getRight();
    for (var wrEdge : wrEdges) {
      g.addEdge(wrEdge);
    }

    var writes = asg.getWriteMetadata().k2ExtWriteTxnIds();
    var constraintEdges = new HashMap<Pair<Integer,Integer>, HashSet<TypeEdge>>();
    for (var key : writes.keySet()) {
      var kWritesTxnIds = new ArrayList<>(writes.get(key));
      int numOfkWrites = kWritesTxnIds.size();
      for (int i = 0; i < numOfkWrites - 1; i++) {
        for (int j = i + 1; j < numOfkWrites; j++) {
          int ti = kWritesTxnIds.get(i), tj = kWritesTxnIds.get(j);
          var ijNodePair = Pair.of(ti, tj);
          var jiNodePair = Pair.of(tj, ti);
          // add WW edges
          if (!constraintEdges.containsKey(ijNodePair)) {
            constraintEdges.put(ijNodePair, new HashSet<>());
          }
          constraintEdges.get(ijNodePair).add(new TypeEdge(GraphNodeId.txn(ti), GraphNodeId.txn(tj), WW, key));

          if (!constraintEdges.containsKey(jiNodePair)){
            constraintEdges.put(jiNodePair, new HashSet<>());
          }
          constraintEdges.get(jiNodePair).add(new TypeEdge(GraphNodeId.txn(tj), GraphNodeId.txn(ti), WW, key));

          // add RW edges
          Set<Integer> readFromTi = new HashSet<>(readFrom.getOrDefault(Pair.of(key, ti), new HashSet<>()));
          Set<Integer> readFromTj = new HashSet<>(readFrom.getOrDefault(Pair.of(key, tj), new HashSet<>()));
          readFromTj.remove(ti);
          readFromTi.remove(tj);

          for (var readTxn: readFromTi) {
            constraintEdges.get(ijNodePair).add(new TypeEdge(GraphNodeId.txn(readTxn), GraphNodeId.txn(tj), RW, key));
          }

          for (var readTxn: readFromTj) {
            constraintEdges.get(jiNodePair).add(new TypeEdge(GraphNodeId.txn(readTxn), GraphNodeId.txn(ti), RW, key));
          }
        }
      }
    }

//    var constraints = new HashSet<BinaryConstraint>();
    var addedPairs = new HashSet<Pair<Integer, Integer>>();
    for (var key : writes.keySet()) {
      var kWritesTxnIds = new ArrayList<>(writes.get(key));
      int numOfkWrites = kWritesTxnIds.size();
      for (int i = 0; i < numOfkWrites - 1; i++) {
        for (int j = i + 1; j < numOfkWrites; j++) {
          int ti = kWritesTxnIds.get(i), tj = kWritesTxnIds.get(j);
          var ijNodePair = Pair.of(ti, tj);
          var jiNodePair = Pair.of(tj, ti);

          if (addedPairs.contains(Pair.of(ti, tj)) || addedPairs.contains(Pair.of(tj, ti))) {
            continue;
          }
          addedPairs.add(Pair.of(ti, tj));

          g.addSuperposition(new Superposition(List.of(constraintEdges.get(ijNodePair),
              constraintEdges.get(jiNodePair))));
          numCons ++;
        }
      }
    }

    profiler.endTick("graphcompile");
    log.info("After compilation: {} constraints", numCons);
    return g;
  }

  private boolean containsEdgeToT0(Set<TypeEdge> es) {
    for (var edge : es) {
      if (edge.v == 0) {
        return true;
      }
    }
    return false;
  }

  protected MutableValueGraph<Integer, Collection<Pair<EdgeType, Key>>> getPolySiReadFrom(ASG ASG) {
    MutableValueGraph<Integer, Collection<Pair<EdgeType, Key>>> readTxns = ValueGraphBuilder.directed().build();

    for (var txn: ASG.getHistory().getAllTxns()) {
      readTxns.addNode(txn.getTxnId());
    }

    for (var directDep : ASG.getDependencyStore().directDeps()) {
      var externals = directDep.externalCandidateWrites();
      assert externals.size() == 1;
      int writeTxn = externals.get(0).getTxnId();
      var edge = Pair.of(directDep.edgeType, directDep.key);

      if (!readTxns.hasEdgeConnecting(writeTxn, directDep.txnId)) {
        readTxns.putEdgeValue(writeTxn, directDep.txnId, new ArrayList<>());
      }

      readTxns.edgeValue(writeTxn, directDep.txnId).get().add(edge);
    }

    return readTxns;
  }
}
