package graphs.graphs;

import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.interfaces.PolySIABGraph;
import graphs.nodes.GraphNodeId;
import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import util.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class PolySIOriginalGraph extends Polygraph implements PolySIABGraph {
  @Getter
  private MutableValueGraph<Integer, Collection<Pair<EdgeType, Key>>> graphA = ValueGraphBuilder
      .directed().build();
  @Getter
  private MutableValueGraph<Integer, Collection<Pair<EdgeType, Key>>> graphB = ValueGraphBuilder
      .directed().build();

  public PolySIOriginalGraph(Collection<GraphNodeId> txns, Config cfg) {
    super(txns, cfg);
    for (GraphNodeId txn : txns) {
      int encoded = txn.txnId();
      graphA.addNode(encoded);
      graphB.addNode(encoded);
    }
  }

  public void addEdge(TypeEdge e) {
    super.addEdge(e);
    switch (e.edgeType) {
      case WR:
      case WW:
      case CB:
        addEdge(graphA, e.u, e.v, Pair.of(e.edgeType, e.key));
        break;
      case RW:
        addEdge(graphB, e.u, e.v, Pair.of(e.edgeType, e.key));
        break;
    }
  }

  private void addEdge(
      MutableValueGraph<Integer, Collection<Pair<EdgeType, Key>>> graph,
      Integer u, Integer v, Pair<EdgeType, Key> edge) {
    if (!graph.hasEdgeConnecting(u, v)) {
      graph.putEdgeValue(u, v, new ArrayList<>());
    }
    graph.edgeValue(u, v).get().add(edge);
  }

  @Override
  public void dump(String filePath) {
    throw new NotImplementedException("");
  }
}
