package compile.v1;

import static graphs.edges.EdgeType.BC;
import static graphs.edges.EdgeType.RW;
import static graphs.edges.EdgeType.WW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import asg.ASG;
import common.Key;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.BCPolygraph;
import graphs.nodes.GraphNodeId;
import util.CommonUtils;
import util.Config;

public class ViperSiGraphCompiler extends GraphCompiler {
  public ViperSiGraphCompiler(Config cfg) {
    super(cfg);
  }

  @Override
  public BCPolygraph compile(ASG asg) {
    profiler.startTick("graphcompile");
    GraphCompilerUtil.checkIntermediateReads(asg, mode, cfg);

    Set<GraphNodeId> nodeIds = new HashSet<>();
    for (int txnId = 0; txnId < asg.getNumTxns(); txnId++) {
      nodeIds.add(GraphNodeId.txn(CommonUtils.bi(txnId)));
      nodeIds.add(GraphNodeId.txn(CommonUtils.ci(txnId)));
    }

    BCPolygraph g = new BCPolygraph(nodeIds, cfg);
    // intra-txn edges
    for (int txnId = 1; txnId < asg.getNumTxns(); txnId++) {
      g.addEdge(new TypeEdge(GraphNodeId.txn(CommonUtils.bi(txnId)), GraphNodeId.txn(CommonUtils.ci(txnId)), BC,
          Key.getNullKey(), true));
    }

    // known edges
    for (var edge : asg.getDependencyStore().edges()) { // for all isolation levels
      if (EdgeType.isSiEdgeType(edge.edgeType)) {
        g.addEdge(edge.toBCEdge());
      }
    }

    if (!cfg.VIPER_COALESCE_CONSTRAINTS) {
      for (Key k : asg.getWriteMetadata().getExtWrites().keySet()) {
        // all the external write txn ids of key k
        List<Integer> kWritesTxnIds = new ArrayList<>(asg.getWriteMetadata().getExternalWriteTxnIds(k));
        int n = kWritesTxnIds.size();

        for (int i = 1; i < n - 1; i++) {
          for (int j = i + 1; j < n; j++) {
            int ti = kWritesTxnIds.get(i), tj = kWritesTxnIds.get(j);
            GraphNodeId tiNode = GraphNodeId.txn(ti);
            GraphNodeId tjNode = GraphNodeId.txn(tj);
            Superposition con = new Superposition(List.of(
                Set.of(new TypeEdge(tiNode, tjNode, WW, k).toBCEdge()),
                Set.of(new TypeEdge(tjNode, tiNode, WW, k).toBCEdge())), true);
            g.addSuperposition(con);
          }
        }
      }
      for (var directDep : asg.getDependencyStore().directDeps()) {
        // Cobra assumes unique values.
        var externals = directDep.externalCandidateWrites();
        assert externals.size() == 1;
        int readFromTxn = externals.get(0).getTxnId();
        var wrEdge = new TypeEdge(GraphNodeId.txn(readFromTxn), GraphNodeId.txn(directDep.txnId),
            directDep.edgeType, directDep.key);
        g.addEdge(wrEdge.toBCEdge());

        // constraints
        for (int Tk : directDep.allWriteTxnIds) {
          if (Tk != readFromTxn && Tk != directDep.txnId) {
            var wwEdge = new TypeEdge(GraphNodeId.txn(Tk), GraphNodeId.txn(readFromTxn), WW, directDep.key).toBCEdge();
            var rwEdge = new TypeEdge(GraphNodeId.txn(directDep.txnId), GraphNodeId.txn(Tk), RW, directDep.key)
                .toBCEdge();
            g.addSuperposition(new Superposition(List.of(Set.of(wwEdge), Set.of(rwEdge)), true));
          }
        }
      }
    } else {
      // default branch:
      // RW edges inside each chain
      var vo = asg.getVersionOrder();
      var p = computeReadFromMap(asg);
      var wrEdges = p.getLeft();
      for (var e : wrEdges) { // known wr edges
        g.addEdge(e.toBCEdge());
      }

      var readFrom = p.getRight();
      // rw edges inside chains
      Set<TypeEdge> es = inferRwEdges(asg.getWriteMetadata().getExtWrites().keySet(), vo, readFrom);
      for (var e : es) { // rw edges inside chains
        g.addEdge(e.toBCEdge());
      }

      // build coalesced constraints: ww and rw edges across chains
      var cons = cobraComputeCoalescedConstraints(asg.getWriteMetadata().getExtWrites().keySet(), vo, readFrom);
      for (var con : cons) {
        g.addSuperposition(con.toBCSuperposition());
      }
    }

    // range queries, actually we don't need to handle range queries here, they
    // are handled in the construction of graphIR.
    profiler.endTick("graphcompile");
    return g;
  }
}
