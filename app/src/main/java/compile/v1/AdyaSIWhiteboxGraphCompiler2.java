package compile.v1;

import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.Polygraph;
import graphs.nodes.GraphNodeId;
import history.op.RangeOp;
import asg.ASG;
import util.Config;

import java.util.HashSet;
import java.util.List;

import static graphs.edges.EdgeType.*;
import static graphs.edges.EdgeType.WW;

public class AdyaSIWhiteboxGraphCompiler2 extends GraphCompiler {
  public AdyaSIWhiteboxGraphCompiler2(Config cfg) {
    super(cfg);
  }


  @Override
  public InCompleteGraph compile(ASG asg) {
    profiler.startTick("graphcompile");
    var vo = asg.getVersionOrder();
    assert vo.hasCompleteVersionOrder();

    var nodeIds = new HashSet<GraphNodeId>();
    for (int txnId = 0; txnId < asg.getNumTxns(); txnId++) {
      nodeIds.add(GraphNodeId.txn(txnId));
    }
    Polygraph g = new Polygraph(nodeIds, cfg);

    // known edges
    for (var edge : asg.getDependencyStore().edges()) { // for all isolation levels
      if (EdgeType.isSiEdgeType(edge.edgeType)) {
        g.addEdge(edge);
      }
    }

    for (var directDep : asg.getDependencyStore().directDeps()) {
      // Cobra assumes unique values.
      // assert == 1, not <= 1, because
      // TODO: create a write op in T0 for all the keys in K.
      var externals = directDep.externalCandidateWrites();
      assert externals.size() == 1; // Emme assumes unique values.
      int writeTxnId = externals.get(0).getTxnId();
      var edgeTypes = directDep.isRMW ? List.of(WW, WR) : List.of(WR);
      // read-dependencies
      addEdges(g, writeTxnId, directDep.txnId, directDep.key, edgeTypes);

      var nextWriteTxn = vo.getSubsequenceWrite(directDep.key, asg.getHistory().getKthTxn(writeTxnId));
      if (nextWriteTxn != null) {
        // anti-dependencies
        g.addEdge(new TypeEdge(GraphNodeId.txn(writeTxnId), GraphNodeId.txn(nextWriteTxn.getTxnId()), RW,
            directDep.key).toBCEdge());
      }
    }

    // write-dependencies
    for (var key : asg.getWriteMetadata().k2ExtWriteTxnIds().keySet()) {
      var kExtWriteTxnIds = asg.getWriteMetadata().k2ExtWriteTxnIds().get(key);

      for (var curTxnId : kExtWriteTxnIds) {
        var curTxn = asg.getHistory().getKthTxn(curTxnId);
        var prevTxn = vo.getPreviousWrite(key, curTxn);
        if (prevTxn != null) {
          g.addEdge(new TypeEdge(GraphNodeId.txn(prevTxn.getTxnId()), GraphNodeId.txn(curTxnId), WW, key));
        }
      }
    }

    // build predicate dependencies by version set
    // TODO: we may not hanlde in this way, instead, we can build directDep for those keys
    // in versionSet but doesn't match the predicate.
    for (var rangePos : asg.getReadMetadata().rangeQueries()) {
      var rangeTxnId = rangePos.getTxnId();
      var rangeMop = (RangeOp) asg.getHistory().getKthTxn(rangeTxnId).getMops().get(rangePos.getMopIndex());
      var versionSet = rangeMop.getVersionSet();

      for (var key : asg.getKeys()) {
        // We actually should iterate through all keys, i.e., K
        // however, in this workload, there is no deletes.
        // The only txn that can create a dead/unborn version is T0.

        // If a key doesn't exist in versionSet, it means
        // we should add a pwr edge from T0 to rangeTxn,
        // and a series of prw edges from rangeTxn to later Put txns of the same key.
        //    (a) this key conflicting with the range, they are considered as "should returned but not returned"
        //        then we have already added item-dependencies, do nothing.
        //    (b) this key doesn't conflict with the range,
        //        add pwr from the delete txn that creates the dead/unborn version to range txn,
        //        add prw from range txn to the next version.
        // If a key exists in versionSet,
        //    (a) conflicting: assert that it should be returned, almost do nothing due to item-dependencies.
        //    (b) non-conflicting:
        //        add pwr: from the Put txn that creates the version to range txn
        //        add prw: from the range txn to the next version
        var val = versionSet.getVersion(key);
        var returnedKvPairs = rangeMop.getKvPairs();

        if (key.inBetween(rangeMop.getKey1(), rangeMop.getKey2())) { // key belongs to the key range
          if (versionSet.contains(key)) {
            // if kv is returned, we have already built item-dependencies for them.
            assert returnedKvPairs.containsKey(key) && returnedKvPairs.get(key).equals(val);
          } else {
            // doesn't exist in versionSet => mustn't be returned either.
            assert !returnedKvPairs.containsKey(key);
            // do nothing because we have enforced the range txn reads from a dead version.
          }
        }
      }
    }

    profiler.endTick("graphcompile");
    return g;
  }

  private void addEdges(InCompleteGraph graph, int u, int v, Key key, List<EdgeType> edgeTypes) {
    for (var edgeType : edgeTypes) {
      var edge = new TypeEdge(GraphNodeId.txn(u), GraphNodeId.txn(v), edgeType, key);
      graph.addEdge(edge);
    }
  }
}
