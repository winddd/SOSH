package solvers;

import common.Key;
import graphs.edges.EdgeType;
import graphs.graphs.InCompleteGraph;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;

public class KnownGraphSolver implements Solver {
  private final List<InCompleteGraph> graphs;

  public KnownGraphSolver(List<InCompleteGraph> graphs) {
    if (graphs == null || graphs.isEmpty()) {
      throw new IllegalArgumentException("KnownGraphSolver requires at least one graph");
    }
    this.graphs = List.copyOf(graphs);
  }

  public KnownGraphSolver(InCompleteGraph graph) {
    this(List.of(graph));
  }

  @Override
  public boolean solve() {
    for (InCompleteGraph graph : graphs) {
      if (!isAdyaSISCC_Lib(graph)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void printConflictClauses() {
    System.out.println("KnownGraphSolver detected a cycle in the known graph.");
    System.out.println("This solver uses graph algorithms (SCC + cycle detection) rather than SMT,");
    System.out.println("so detailed conflict clause analysis is not available.");
  }

  /**
   * use SCC algorithm to parition the monolithic graph into several components,
   * and then search for cycles in each component.
   *
   * @return
   */
  private boolean isAdyaSISCC_Lib(InCompleteGraph graph) {
    // if without cycles, serializable => Adya SI
    var scInspector =
        new KosarajuStrongConnectivityInspector<Integer, DefaultEdge>(toDefaultDirectedGraph(graph));
    List<Set<Integer>> sccList = scInspector.stronglyConnectedSets();
    for (Set<Integer> scc : sccList) {
      if (scc.size() <= 1)
        continue;
//                String s = "["+StringUtils.join(scc, ",")+"]";
//                System.out.println(s);
// try to find cycles in this SCC
//                DefaultDirectedGraph sccGraph = toDefaultDirectedGraph(scc);
      List<List<Integer>> cycles = findAllCyclesTarjan(graph, scc);
      // for each cycle, check adya's requirement
      for (List<Integer> cycle : cycles) {
        if (hasForbiddenCycle(graph, cycle))
          return false;
      }
    }

    return true;
  }

  private List<List<Integer>> findAllCyclesTarjan(InCompleteGraph graph, Set<Integer> scc) {
    DefaultDirectedGraph defaultDirectedGraph = toDefaultDirectedGraph(graph, scc);
    TarjanSimpleCycles<Integer, DefaultEdge> cycleDetector = new TarjanSimpleCycles<>(defaultDirectedGraph);
    List<List<Integer>> cycles = cycleDetector.findSimpleCycles();
    return cycles;
  }

  /**
   * Create a DefaultDirectedGraph from a given InCompleteGraph.
   *
   * @param graph
   * @return
   */
  public DefaultDirectedGraph toDefaultDirectedGraph(InCompleteGraph graph) {
    Set<Integer> scc = new HashSet<>();
    scc.addAll(graph.getNodes());
    return toDefaultDirectedGraph(graph, scc);
  }

  /**
   * Create a DefaultDirectedGraph from an induced subgraph of InCompleteGraph.
   *
   * @param graph
   * @param scc
   * @return
   */
  public DefaultDirectedGraph toDefaultDirectedGraph(InCompleteGraph graph, Set<Integer> scc) {
    var defaultDirectedGraph =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);

    for (int i : scc) {
      defaultDirectedGraph.addVertex(i);
    }

    var adjList = graph.getAdjList();
    for (var u : adjList.keySet()) {
      Integer encodedU = u.txnId();

      for (var v : adjList.get(u).keySet()) { // iterate over each out neighbor
        Integer encodedV = v.txnId();

        if (scc.contains(encodedU) && scc.contains(encodedV))
          defaultDirectedGraph.addEdge(encodedU, encodedV);
      }
    }

    return defaultDirectedGraph;
  }

  /**
   * If any RW edge in the cycle occurs individually or without RW edges at all, it'll be rejected by Adya SI.
   * Whether there exists a forbidden cycle among all the cycles corresponding to this path `cycle`.
   * @param path
   * @return return true if the cycle is disallowed by Adya SI, like, is a 01 cycle.
   */
  private boolean hasForbiddenCycle(InCompleteGraph graph, List<Integer> path) {
    // TODO: does path have the start node twice?
    List<Set<EdgeType>> edgeTypes = new ArrayList<>();
    var adjList = graph.getAdjList();
    for (int i = 0; i < path.size(); i++) { // 6->3->2->7->6 or 6->3->2->7?
      int u = path.get(i);
      int v = path.get((i + 1) % path.size());

      // Find GraphNodeIds for these encoded integers
      var uNodeId = graph.getNodeIds().stream().filter(id -> id.txnId() == u).findFirst().orElse(null);
      var vNodeId = graph.getNodeIds().stream().filter(id -> id.txnId() == v).findFirst().orElse(null);

      if (uNodeId != null && vNodeId != null) {
        edgeTypes.add(getEdgeTypes(adjList.get(uNodeId).get(vNodeId)));
      }
    }

    List<EdgeType> stack = new ArrayList<>();
    return dfs(path, 0, stack, edgeTypes);
  }

  private Set<EdgeType> getEdgeTypes(Set<Pair<EdgeType, Key>> pairs) {
    Set<EdgeType> edgeTypes = new HashSet<>();
    for (var pair : pairs) {
      edgeTypes.add(pair.getLeft());
    }
    return edgeTypes;
  }

  /**
   * use dfs to search if there exists a forbidden cycle.
   * @param cycle
   * @return
   */
  private boolean dfs(List<Integer> cycle, int idx,
                      List<EdgeType> queue,
                      List<Set<EdgeType>> edgeTypes) {
    assert cycle.size() >= 2;
    var nextIdx = (idx + 1) % cycle.size();

    if (nextIdx == 0) {
      for (var edgeType : edgeTypes.get(idx)) {
        queue.add(edgeType);
        if (forbidden(queue)) {
          return true;
        }
      }

      return false;
    } else {
      for (var edgeType : edgeTypes.get(idx)) {
        queue.add(edgeType);
        boolean ret = dfs(cycle, nextIdx, queue, edgeTypes);
        queue.remove(queue.size() - 1);
        if (ret) {
          return true;
        }
      }

      return false;
    }
  }

  private boolean forbidden(List<EdgeType> edgeTypes) {
    int n = edgeTypes.size();
    for (int i = 0; i < n; i ++) {
      var currEdgeType = edgeTypes.get(i);
      var nextEdgeType = edgeTypes.get((i+1) % n);
      if (EdgeType.isAntiDependencyType(currEdgeType) && EdgeType.isAntiDependencyType(nextEdgeType)) {
        return false;
      }
    }

    return true;
  }
}
