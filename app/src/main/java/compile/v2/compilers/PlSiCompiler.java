package compile.v2.compilers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import common.Key;
import compile.v2.inputs.GraphInputs;
import compile.v2.modules.GenAntiDepModule;
import compile.v2.modules.GenDepModule;
import compile.v2.modules.GenReadDepModule;
import compile.v2.modules.GenSessionDepModule;
import compile.v2.modules.GenWriteDepModule;
import compile.v2.modules.ModuleUtility;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import util.CommonUtils;
import util.Config;
import util.Profiler;
import util.enumtypes.AdyaGraphType;
import util.enumtypes.MODE;

/**
 * Modular SI compiler that builds the transaction-level graph with dependency
 * generators and then lifts it into the BC space expected by the original
 * Boomslang SI algorithms.
 */
public class PlSiCompiler implements GraphCompiler {
  private final Profiler profiler;
  private final MODE mode;
  private final Config cfg;

  public PlSiCompiler(Config cfg) {
    this.profiler = Profiler.getInstance();
    this.mode = cfg.RUNMODE;
    this.cfg = cfg;
  }

  @Override
  public List<InCompleteGraph> compile(GraphInputs inputs) {
    profiler.startTick("graphcompile");
    var txnNodes = new HashSet<GraphNodeId>();
    for (int i = 0; i < inputs.numTxns(); i++) {
      txnNodes.add(GraphNodeId.txn(i));
    }

    BoomslangGraph txnGraph = new BoomslangGraph(txnNodes, cfg);
    for (var edge : inputs.edges()) {
      assert edge.edgeType != EdgeType.BC;
      if (EdgeType.isSerEdgeType(edge.edgeType)) {
        txnGraph.addEdge(edge);
      }
    }

    var modules = new ArrayList<GenDepModule>();
    modules.add(new GenReadDepModule(profiler, mode, cfg));
    modules.add(new GenWriteDepModule(profiler, mode, cfg));
    modules.add(new GenAntiDepModule(profiler, mode, cfg));
    modules.add(new GenSessionDepModule(profiler, mode, cfg));

    InCompleteGraph current = ModuleUtility.generate(inputs, txnGraph, modules);
    assert current instanceof BoomslangGraph;
    List<InCompleteGraph> ret = List.of(liftToBcGraph((BoomslangGraph) current, inputs.numTxns()));
    profiler.endTick("graphcompile");
    return ret;
  }

  private BoomslangGraph liftToBcGraph(BoomslangGraph txnGraph, int numTxns) {
    var bcNodes = new HashSet<GraphNodeId>();
    bcNodes.add(GraphNodeId.txn(0));
    for (int txnId = 1; txnId < numTxns; txnId++) {
      bcNodes.add(GraphNodeId.txn(CommonUtils.bi(txnId)));
      bcNodes.add(GraphNodeId.txn(CommonUtils.ci(txnId)));
    }

    BoomslangGraph bcGraph = new BoomslangGraph(bcNodes, cfg);
    for (int txnId = 1; txnId < numTxns; txnId++) {
      bcGraph.addEdge(new TypeEdge(GraphNodeId.txn(CommonUtils.bi(txnId)), GraphNodeId.txn(CommonUtils.ci(txnId)),
          EdgeType.BC, Key.getNullKey(), true));
    }

    var adjList = txnGraph.getAdjList();
    for (var srcEntry : adjList.entrySet()) {
      var srcNodeId = srcEntry.getKey();
      for (var dstEntry : srcEntry.getValue().entrySet()) {
        var dstNodeId = dstEntry.getKey();
        for (var typeKey : dstEntry.getValue()) {
          if (typeKey.getLeft() == EdgeType.BC) {
            continue;
          }

          var edge = new TypeEdge(srcNodeId, dstNodeId, typeKey.getLeft(), typeKey.getRight());
          bcGraph.addEdge(edge.toBCEdge());
        }
      }
    }

    for (Superposition superposition : txnGraph.getSuperpositions()) {
      bcGraph.addSuperposition(superposition.toBCSuperposition());
    }

    for (Imply imply : txnGraph.getImplies()) {
      bcGraph.addImply(imply.toBCImply());
    }
    bcGraph.setGraphType(AdyaGraphType.DSG);
    bcGraph.setHistory(txnGraph.getHistory());
    return bcGraph;
  }
}
