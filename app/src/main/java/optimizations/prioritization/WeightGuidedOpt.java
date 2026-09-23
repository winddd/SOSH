package optimizations.prioritization;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.PolySIMatrixGraph;
import graphs.graphs.interfaces.HasGeneralConstraints;
import graphs.graphs.interfaces.HasSuperpositions;
import graphs.graphs.interfaces.PolySIABGraph;
import lombok.extern.slf4j.Slf4j;
import optimizations.GraphOptimizer;
import util.Config;
import util.Profiler;

import java.util.HashMap;
import java.util.Collection;
import java.util.Map;

@Slf4j
public class WeightGuidedOpt implements GraphOptimizer {
  private InCompleteGraph graph;
  private Map<Integer, Integer> node2Rank;
  private Profiler profiler;
  private Config cfg;

  public WeightGuidedOpt(InCompleteGraph g, Config cfg) {
   this.graph = g;
   this.profiler = Profiler.getInstance();
   this.cfg = cfg;
 }

  @Override
  public InCompleteGraph optimize() {
    log.info("Start weights assignment...");
    profiler.startTick("OPT");
    // topo sort
    var node2Rank = computeNodeRank();
    if (node2Rank != null) {
      this.node2Rank = node2Rank;
      // solver.setNode2Rank(node2Rank);
    } else { // topo sort failed and hence reject
      // Cycle is already printed in TopoSort when cfg.DEBUG is enabled
      profiler.endTick("OPT");
      return null;
    }

    // GeneralizedConstraints
    if (graph instanceof HasGeneralConstraints) {
      var cons = ((HasGeneralConstraints) graph).getGeneralCons();
      for (var con : cons) {
        computePriority(con);
      }
    }

    // Superpositions
    if (graph instanceof HasSuperpositions) {
      for (var superposition : ((HasSuperpositions) graph).getSuperpositions()) {
        computePriority(superposition);
      }
    }

    profiler.endTick("OPT");
    return this.graph;
  }

  private Map<Integer, Integer> computeNodeRank() {
    if (graph instanceof PolySIABGraph polySIABGraph) {
      return computePolySINodeRank(polySIABGraph);
    }
    return TopoSort.computeNodeRank(graph, cfg.DEBUG);
  }

  private Map<Integer, Integer> computePolySINodeRank(PolySIABGraph graph) {
    var graphA = new PolySIMatrixGraph<>(graph.getGraphA().asGraph());
    var graphB = new PolySIMatrixGraph<>(graph.getGraphB().asGraph(), graphA.getNodeMap());
    var graphC = graphA.composition(graphB);
    var graphAC = graphA.union(graphC);
    var sortedNodes = graphAC.topologicalSort();
    if (sortedNodes.isEmpty()) {
      return null;
    }

    var ranks = new HashMap<Integer, Integer>();
    var topoOrder = sortedNodes.get();
    for (int i = 0; i < topoOrder.size(); i++) {
      ranks.put(topoOrder.get(i), i);
    }
    return ranks;
  }

  private void computePriority(GeneralizedConstraint con) {
    var es1 = con.getEdgeSet1();
    var es2 = con.getEdgeSet2();
    boolean pickEs1 = TopoSort.pickThisEs(node2Rank, es1);
    boolean pickEs2 = TopoSort.pickThisEs(node2Rank, es2);
//    int[] priorities = new int[es1.size() + es2.size()];
//    Arrays.fill(priorities, -1);

    if (pickEs1 && !pickEs2) {
      setPriority(es2, 1);
      setPriority(es1, -1);
//      Arrays.fill(priorities, es1.size(), priorities.length, 1);
//      solver.addConstraint(con, priorities);
    } else if(pickEs2 && !pickEs1) {
      setPriority(es1, 1);
      setPriority(es2, -1);
    }
  }

  private void computePriority(Superposition superposition) {
    var edgeSets = superposition.getEdgeSets();
    int index = TopoSort.pickWhichEs(node2Rank, edgeSets);

    for (int i = 0; i < edgeSets.size(); i++) {
      var edgeSet = edgeSets.get(i);

      if (i == index) {
        setPriority(edgeSet, -1);
      } else {
        setPriority(edgeSet, 1);
      }
    }
  }

  private void setPriority(Collection<TypeEdge> es, int priority) {
    for (var edge: es) {
      edge.setPriority(priority);
    }
  }
}
