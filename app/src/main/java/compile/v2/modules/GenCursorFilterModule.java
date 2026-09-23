package compile.v2.modules;

import compile.v2.inputs.GraphInputs;
import common.Key;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import util.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Module that strips all edges and constraints that do not touch the target
 * cursor-stability key. This keeps the filtering step within the modular
 * pipeline.
 */
public class GenCursorFilterModule implements GenDepModule {
  private final Config cfg;
  private final Key targetKey;

  public GenCursorFilterModule(Config cfg, Key targetKey) {
    this.cfg = cfg;
    this.targetKey = targetKey;
  }

  @Override
  public InCompleteGraph generate(GraphInputs inputs, InCompleteGraph G) {
    assert G instanceof BoomslangGraph;
    var fullGraph = (BoomslangGraph) G;
    var nodes = new HashSet<>(fullGraph.getNodeIds());
    BoomslangGraph filtered = new BoomslangGraph(nodes, cfg);

    var adj = fullGraph.getAdjList();
    for (var srcEntry : adj.entrySet()) {
      var srcNodeId = srcEntry.getKey();
      for (var dstEntry : srcEntry.getValue().entrySet()) {
        var dstNodeId = dstEntry.getKey();
        for (var typeKey : dstEntry.getValue()) {
          var key = typeKey.getRight();
          if (isTarget(key)) {
            filtered.addEdge(new TypeEdge(srcNodeId, dstNodeId, typeKey.getLeft(), key));
          }
        }
      }
    }

    for (Superposition superposition : fullGraph.getSuperpositions()) {
      List<Set<TypeEdge>> filteredSets = new ArrayList<>();
      boolean drop = false;
      for (var edgeSet : superposition.getEdgeSets()) {
        Set<TypeEdge> subset = new HashSet<>();
        for (var edge : edgeSet) {
          if (isTarget(edge)) {
            subset.add(new TypeEdge(GraphNodeId.txn(edge.u), GraphNodeId.txn(edge.v), edge.edgeType, edge.key));
          }
        }

        if (subset.isEmpty()) {
          drop = true;
          break;
        }
        filteredSets.add(subset);
      }

      if (!drop && !filteredSets.isEmpty()) {
        filtered.addSuperposition(new Superposition(filteredSets));
      }
    }

    for (Imply imply : fullGraph.getImplies()) {
      if (isTarget(imply.wrEdge) && isTarget(imply.wwEdge) && isTarget(imply.rwEdge)) {
        filtered.addImply(new Imply(
            new TypeEdge(GraphNodeId.txn(imply.wrEdge.u), GraphNodeId.txn(imply.wrEdge.v), imply.wrEdge.edgeType, imply.wrEdge.key),
            new TypeEdge(GraphNodeId.txn(imply.wwEdge.u), GraphNodeId.txn(imply.wwEdge.v), imply.wwEdge.edgeType, imply.wwEdge.key),
            new TypeEdge(GraphNodeId.txn(imply.rwEdge.u), GraphNodeId.txn(imply.rwEdge.v), imply.rwEdge.edgeType, imply.rwEdge.key)));
      }
    }

    filtered.setHistory(fullGraph.getHistory());
    return filtered;
  }

  private boolean isTarget(TypeEdge edge) {
    return edge != null && isTarget(edge.key);
  }

  private boolean isTarget(Key key) {
    return key != null && key.equals(targetKey);
  }
}
