package compile.v2.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import asg.WriteEvent;
import compile.v2.inputs.GraphInputs;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import util.Config;
import util.Profiler;
import util.enumtypes.MODE;
import util.exception.RejectException;

public class GenReadDepModule implements GenDepModule {
  private Profiler profiler;
  private MODE mode;
  private Config cfg;

  public GenReadDepModule(Profiler profiler, MODE mode, Config cfg) {
    this.profiler = profiler;
    this.mode = mode;
    this.cfg = cfg;
  }

  @Override
  public InCompleteGraph generate(GraphInputs inputs, InCompleteGraph G) {
    assert G instanceof BoomslangGraph;
    var g = (BoomslangGraph) G;

    for (var directDep : inputs.directDeps()) {
      var txn = inputs.history().getKthTxn(directDep.txnId);
      List<WriteEvent> writeEvents = ModuleUtility.resolveCandidateWritesWithRyowPolicy(directDep, txn, mode, cfg.RYOW_POLICY);
      // Update candiate write events in `DirectDep` after we use RYOW to correct it.
      directDep.setCandidateWriteEvents(writeEvents);

      List<Set<TypeEdge>> wrs = new ArrayList<>();

      if (writeEvents.isEmpty()) {
        throw new RejectException("No candidate writes");
      } else if (writeEvents.size() == 1) { // known WR edge
        var wrEdge = new TypeEdge(GraphNodeId.txn(writeEvents.iterator().next().getTxnId()),
            GraphNodeId.txn(directDep.txnId), directDep.edgeType, directDep.key);
        g.addEdge(wrEdge);
      } else {
        for (WriteEvent candidate : writeEvents) {
          int writeTxn = candidate.getTxnId();
          var wrEdge = new TypeEdge(GraphNodeId.txn(writeTxn), GraphNodeId.txn(directDep.txnId),
              directDep.edgeType,
              directDep.key);
          wrs.add(Set.of(wrEdge));
        }

        Superposition superposition = new Superposition(wrs);
        g.addSuperposition(superposition);
      }
    }

    return g;
  }
}
