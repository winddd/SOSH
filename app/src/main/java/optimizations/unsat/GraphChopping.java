package optimizations.unsat;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.interfaces.HasGeneralConstraints;
import graphs.graphs.interfaces.HasImplies;
import graphs.graphs.interfaces.HasSuperpositions;
import graphs.nodes.GraphNodeId;
import util.Config;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * take a TypePolyGraph as input and returns a list of subgraphs.
 */
public class GraphChopping {
  /**
   * @param G: the large graph.
   * @param k: the number of subgraphs.
   * @return
   */
  public static List<Set<GraphNodeId>> splitNodes(InCompleteGraph G, int k) {
    // retrieve 4 items
    var nodes = G.getNodeIds();
    Random rand = new Random();
    rand.setSeed(123);
    List<Set<GraphNodeId>> nodePartitions = new ArrayList<>();

    // Create T0 node (transaction 0)
    GraphNodeId t0Node = GraphNodeId.txn(0);

    for (int i = 0; i < k; i++) {
      nodePartitions.add(new HashSet<>());
      nodePartitions.get(i).add(t0Node); // why each partition needs to have T0?
    }

    for (var nodeId : nodes) {
      int assignedSubGraph = rand.nextInt(k);
      nodePartitions.get(assignedSubGraph).add(nodeId);
    }

    return nodePartitions;
  }

  public static InCompleteGraph getInducedSubgraph(InCompleteGraph G, Set<GraphNodeId> nodes, Config cfg) {
    var adjList = G.getAdjList();

    InCompleteGraph subgraph = null;
    try {
      subgraph = G.getClass().getDeclaredConstructor(Collection.class, Config.class).newInstance(nodes, cfg);
    } catch (NoSuchMethodException|InvocationTargetException|InstantiationException|IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    // edges
    for (var u : adjList.keySet()) {
      for (var v : adjList.get(u).keySet()) {
        if (nodes.contains(u) && nodes.contains(v)) {
          for (var p : adjList.get(u).get(v)) {
            subgraph.addEdge(new TypeEdge(u, v, p.getLeft(), p.getRight()));
          }
        }
      }
    }

    // constraints
    if (G instanceof HasGeneralConstraints) {
      for (var con : ((HasGeneralConstraints) G).getGeneralCons()) {
        if (belongToPartition(con, nodes)) {
          ((HasGeneralConstraints) subgraph).addGeneralizedConstraint(con);
        }
      }
    }

    if (G instanceof HasSuperpositions) {
      for (var superposition : ((HasSuperpositions) G).getSuperpositions()) {
        if (belongToPartition(superposition, nodes)) {
          ((HasSuperpositions) subgraph).addSuperposition(superposition);
        }
      }
    }

    if (G instanceof HasImplies) {
      for (var imply : ((HasImplies) G).getImplies()) {
        if (belongToPartition(imply, nodes)) {
          ((HasImplies) subgraph).addImply(imply);
        }
      }
    }

    return subgraph;
  }

  /**
   * whether all the involved nodes in `con` are in `nodePartition`.
   * @param con
   * @param nodePartition
   * @return
   */
  private static boolean belongToPartition(GeneralizedConstraint con, Set<GraphNodeId> nodePartition) {
    return belongToPartition(con.getEdgeSet1(), nodePartition) && belongToPartition(con.getEdgeSet2(), nodePartition);
  }

  private static boolean belongToPartition(Superposition superposition, Set<GraphNodeId> nodePartition) {
    return superposition.getEdgeSets().stream().map(es -> belongToPartition(es, nodePartition)).reduce((a,b) -> a&&b).get();
  }

  private static boolean belongToPartition(Imply imply, Set<GraphNodeId> nodePartition) {
    return belongToPartition(imply.rwEdge, nodePartition)
        && belongToPartition(imply.wrEdge, nodePartition)
        && belongToPartition(imply.wwEdge, nodePartition);
  }

  private static boolean belongToPartition(Collection<TypeEdge> edges, Set<GraphNodeId> nodePartition) {
    for (var edge : edges) {
      if (!nodePartition.contains(edge.getSourceId()) || !nodePartition.contains(edge.getTargetId())) {
        return false;
      }
    }
    return true;
  }

  private static boolean belongToPartition(TypeEdge edge, Set<GraphNodeId> nodePartition) {
    return nodePartition.contains(edge.getSourceId()) && nodePartition.contains(edge.getTargetId());
  }
}
