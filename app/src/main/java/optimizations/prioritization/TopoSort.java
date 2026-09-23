package optimizations.prioritization;

import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopoSort {
  private static final Logger logger = LoggerFactory.getLogger(TopoSort.class);

  /**
   * Perform topological sort on the graph with optional debug output.
   * @param graph the graph to sort
   * @param debug if true, prints cycle info on failure
   * @return sorted list of nodes, or null if a cycle is detected
   */
  public static List<GraphNodeId> topoSort(InCompleteGraph graph, boolean debug) {
    Map<GraphNodeId, Integer> inDegrees = new HashMap<>();
    for (var nodeId : graph.getNodeIds()) {
      inDegrees.put(nodeId, 0);
    }
    assert inDegrees.size() == graph.getNodeIds().size();

    // Build adjacency list without self-loops
    var originalAdjList = graph.getAdjList();
    var adjListNoSelfLoops = new HashMap<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>>();

    // Initialize adjacency list for all nodes
    for (var nodeId : graph.getNodeIds()) {
      adjListNoSelfLoops.put(nodeId, new HashMap<>());
    }

    // Copy edges excluding self-loops
    for (var u : originalAdjList.keySet()) {
      for (var v : originalAdjList.get(u).keySet()) {
        if (!u.equals(v)) {  // Skip self-loops
          adjListNoSelfLoops.get(u).put(v, originalAdjList.get(u).get(v));
          assert !originalAdjList.get(u).get(v).isEmpty();
          inDegrees.put(v, inDegrees.getOrDefault(v, 0) + 1);
        }
      }
    }

    var queue = new ArrayList<GraphNodeId>();
    for (var u : graph.getNodeIds()) {
      if (inDegrees.get(u) == 0) {
        queue.add(u);
      }
    }

    var sortedNodes = new ArrayList<GraphNodeId>();

    while (!queue.isEmpty()) {
      var u = queue.remove(0);
      sortedNodes.add(u);

      for (var v: adjListNoSelfLoops.get(u).keySet()) {
        inDegrees.put(v, inDegrees.get(v) - 1);

        if (inDegrees.get(v) == 0) {
          queue.add(v);
        }
      }
    }

    if (sortedNodes.size() != graph.getNodeIds().size()) {
      // Cycle detected - find and print it only when debug is enabled
      if (debug) {
        logger.info("Topological sort failed: cycle detected in graph");
        List<GraphNodeId> cycle = findCycle(adjListNoSelfLoops, graph.getNodeIds());
        if (cycle != null) {
          logger.info("Cycle found with {} nodes:", cycle.size());
          printCycleWithEdgeInfo(cycle, adjListNoSelfLoops);
        }
      }
      return null;
    }

    return sortedNodes;
  }

  /**
   * Print the cycle with edge type and key information for each edge.
   * Each edge is printed on a separate line for readability.
   */
  private static void printCycleWithEdgeInfo(
      List<GraphNodeId> cycle,
      Map<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>> adjList) {

    logger.info("Cycle edges:");
    for (int i = 0; i < cycle.size(); i++) {
      GraphNodeId from = cycle.get(i);
      GraphNodeId to = cycle.get((i + 1) % cycle.size());

      // Get edge info between from and to
      var edgeInfo = adjList.get(from).get(to);
      StringBuilder edgeStr = new StringBuilder();
      edgeStr.append("  ").append(from).append(" -> ").append(to);

      if (edgeInfo != null && !edgeInfo.isEmpty()) {
        edgeStr.append("  |  ");
        StringJoiner edgeJoiner = new StringJoiner(", ");
        for (var pair : edgeInfo) {
          edgeJoiner.add(pair.getLeft() + ": " + pair.getRight());
        }
        edgeStr.append(edgeJoiner);
      }

      logger.info("{}", edgeStr);
    }
  }

  /**
   * Find a cycle in the graph using DFS with coloring.
   * WHITE (0) = unvisited, GRAY (1) = in current DFS path, BLACK (2) = fully processed
   */
  private static List<GraphNodeId> findCycle(
      Map<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>> adjList,
      Set<GraphNodeId> nodeIds) {

    Map<GraphNodeId, Integer> color = new HashMap<>();
    Map<GraphNodeId, GraphNodeId> parent = new HashMap<>();

    for (var nodeId : nodeIds) {
      color.put(nodeId, 0); // WHITE
    }

    for (var startNode : nodeIds) {
      if (color.get(startNode) == 0) { // WHITE
        List<GraphNodeId> cycle = dfsForCycle(startNode, adjList, color, parent);
        if (cycle != null) {
          return cycle;
        }
      }
    }
    return null;
  }

  private static List<GraphNodeId> dfsForCycle(
      GraphNodeId node,
      Map<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>> adjList,
      Map<GraphNodeId, Integer> color,
      Map<GraphNodeId, GraphNodeId> parent) {

    color.put(node, 1); // GRAY - in current path

    var neighbors = adjList.get(node);
    if (neighbors != null) {
      for (var neighbor : neighbors.keySet()) {
        if (color.get(neighbor) == 1) { // GRAY - back edge found, cycle detected
          // Reconstruct the cycle
          List<GraphNodeId> cycle = new ArrayList<>();
          cycle.add(neighbor);
          GraphNodeId curr = node;
          while (curr != null && !curr.equals(neighbor)) {
            cycle.add(0, curr);
            curr = parent.get(curr);
          }
          return cycle;
        } else if (color.get(neighbor) == 0) { // WHITE - unvisited
          parent.put(neighbor, node);
          List<GraphNodeId> cycle = dfsForCycle(neighbor, adjList, color, parent);
          if (cycle != null) {
            return cycle;
          }
        }
      }
    }

    color.put(node, 2); // BLACK - fully processed
    return null;
  }

  public static Map<Integer, Integer> computeNodeRank(InCompleteGraph graph, boolean debug) {
    Map<Integer, Integer> node2Rank = new HashMap<>();
    var sortedNodes = topoSort(graph, debug);
    if (sortedNodes == null)
      return null;

    for (int i = 0; i < sortedNodes.size(); i++) {
      var nodeId = sortedNodes.get(i);
      // Use txnId for ranking key since this is used for prioritization
      node2Rank.put(nodeId.txnId(), i);
    }

    return node2Rank;
  }

  public static boolean pickThisEs(Map<Integer, Integer> node2Rank, Collection<TypeEdge> es) {
    return computeExpection(node2Rank, es) > 0;
  }

  private static int computeExpection(Map<Integer, Integer> node2Rank, Collection<TypeEdge> es) {
    int diff = 0;
    for (var e : es) {
      diff += (node2Rank.get(e.getTargetId().txnId()) - node2Rank.get(e.getSourceId().txnId()));
    }
    return diff;
  }

  public static int pickWhichEs(Map<Integer, Integer> node2Rank, List<Set<TypeEdge>> edgeSets) {
    int index = -1;
    int maxExp = Integer.MIN_VALUE;
    for (int i = 0; i < edgeSets.size(); i++) {
      var es = edgeSets.get(i);
      var currExp = computeExpection(node2Rank, es);
      if (currExp > maxExp) {
        index = i;
        maxExp = currExp;
      }
    }
    return index;
  }

  public static boolean pickThisEdge(Map<Integer, Integer> node2Rank, TypeEdge e) {
    return node2Rank.get(e.v) > node2Rank.get(e.u);
  }
}
