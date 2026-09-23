package compile.v1;

import static graphs.edges.EdgeType.RW;
import static graphs.edges.EdgeType.WW;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import asg.WriteEvent;
import compile.v2.inputs.GraphInputs;
import compile.v2.modules.ModuleUtility;
import org.apache.commons.lang3.tuple.Pair;

import asg.ASG;
import common.Key;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.Polygraph;
import graphs.nodes.GraphNodeId;
import lombok.extern.slf4j.Slf4j;
import util.Config;
import util.exception.RejectException;

@Slf4j
public class CobraSerGraphCompiler extends GraphCompiler {
  public CobraSerGraphCompiler(Config cfg) {
    super(cfg);
  }

  @Override
  public Polygraph compile(ASG asg) {
    profiler.startTick("graphcompile");
    GraphCompilerUtil.checkIntermediateReads(asg, mode, cfg);

    var txnIds = new HashSet<GraphNodeId>();
    for (int i = 0; i < asg.getNumTxns(); i++) {
      txnIds.add(GraphNodeId.txn(i));
    }

    Polygraph g = new Polygraph(txnIds, cfg);

    // known edges, CB
    for (var edge : asg.getDependencyStore().edges()) { // for all isolation levels
      if (EdgeType.isSerEdgeType(edge.edgeType)) {
        g.addEdge(edge);
      }
    }

    int numCons = 0;
    if (!cfg.COBRA_COALESCE_CONSTRAINTS) {
      for (var directDep : asg.getDependencyStore().directDeps()) {
        // Cobra assumes unique values.
        // assert == 1, not <= 1, because
        // TODO: create a write op in T0 for all the keys in K.
        var externals = directDep.externalCandidateWrites();
        assert externals.size() == 1;
        // WR dependencies
        int readFromTxn = externals.get(0).getTxnId();
        var wrEdge = new TypeEdge(GraphNodeId.txn(readFromTxn), GraphNodeId.txn(directDep.txnId),
            directDep.edgeType, directDep.key);
        g.addEdge(wrEdge);

        // constraints
        for (int Tk : directDep.allWriteTxnIds) {
          if (Tk != readFromTxn && Tk != directDep.txnId) {
            var wwEdge = new TypeEdge(GraphNodeId.txn(Tk), GraphNodeId.txn(readFromTxn), WW, directDep.key);
            var rwEdge = new TypeEdge(GraphNodeId.txn(directDep.txnId), GraphNodeId.txn(Tk), RW, directDep.key);
            g.addSuperposition(new Superposition(Set.of(wwEdge), Set.of(rwEdge)));
            numCons++;
          }
        }
      }
    } else { // default branch:
      // RW edges inside each chain
      var vo = asg.getVersionOrder();
      var p = computeReadFromMap(asg);
      var wrEdges = p.getLeft();
      for (var e : wrEdges) { // wr edges
        g.addEdge(e);
      }
      Map<Pair<Key, Integer>, Set<Integer>> readFrom = p.getRight();
      Set<TypeEdge> es = inferRwEdges(asg.getWriteMetadata().getExtWrites().keySet(), vo, readFrom);
      for (var e : es) { // rw edges inside chains
        g.addEdge(e);
      }

      // build coalesced constraints: ww and rw edges across chains
      var cons = cobraComputeCoalescedConstraints(asg.getWriteMetadata().getExtWrites().keySet(), vo, readFrom);
      for (var con : cons) {
        g.addSuperposition(con);
        numCons++;
      }
    }

    profiler.endTick("graphcompile");
    log.info("After compilation: {} constraints", numCons);
    return g;
  }
}
