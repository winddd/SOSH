package optimizations.reachability.pruner;

import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.PolySIMatrixGraph;
import graphs.graphs.interfaces.PolySIABGraph;
import util.Config;
import util.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class PolySIPruner extends Pruner {
  public PolySIPruner(Config cfg) {
    super(cfg);
  }

  private static Optional<TypeEdge> checkConflict(
      Collection<TypeEdge> edges, PolySIMatrixGraph<Integer> reachability,
      PolySIABGraph knownGraph) {
    for (var e : edges) {
      switch (e.edgeType) {
        case WW:
          if (reachability.hasEdgeConnecting(e.v, e.u)) {
            return Optional.of(e);
            // System.err.printf("conflict edge: %s\n", e);
          }
          break;
        case RW:
          for (var n : knownGraph.getGraphA().predecessors(e.u)) {
            if (reachability.hasEdgeConnecting(e.v, n)) {
              return Optional.of(e);
              // System.err.printf("conflict edge: %s\n", e);
            }
          }
          break;
        default:
          throw new Error("only WW and RW edges should appear in constraints");
      }
    }

    return Optional.empty();
  }

  @Override
  public PruneResult prune(InCompleteGraph inCompleteGraph) {
    if (!(inCompleteGraph instanceof PolySIABGraph graph)) {
      throw new IllegalArgumentException("PolySIPruner requires a PolySI A/B graph");
    }

    var graphA = new PolySIMatrixGraph<>(graph.getGraphA().asGraph());
    var graphB = new PolySIMatrixGraph<>(graph.getGraphB().asGraph(), graphA.getNodeMap());
    // TODO: precompute session order in the construction of graph IR.
    var orderInSession = Utils.getOrderInSession(inCompleteGraph.getHistory());

    var graphC = graphA.composition(graphB);

    if (graphC.hasLoops()) {
//      return Pair.of(0, true);
      return new PruneResult(true, 0, 0, 0, 0);
    }

    var graphAC = graphA.union(graphC);
    var reachability = Utils.reduceEdges(graphAC, inCompleteGraph.getHistory(), orderInSession).reachability();
    System.err.printf("reachability matrix sparsity: %.2f\n",
        1 - reachability.nonZeroElements() / Math.pow(reachability.nodes().size(), 2));

    var solvedConstraints = new ArrayList<Superposition>();

    for (var c : graph.getSuperpositions()) {
      assert c.getEdgeSets().size() == 2; // Should be 2-element superpositions
      var es1 = c.getEdgeSets().get(0);
      var es2 = c.getEdgeSets().get(1);
      var conflict = checkConflict(es1, reachability, graph);
      if (conflict.isPresent()) {
        inCompleteGraph.addEdges(es2);
        solvedConstraints.add(c);
        // System.err.printf("%s -> %s because of conflict in %s\n",
        // c.writeTransaction2, c.writeTransaction1,
        // conflict.get());
        continue;
      }

      conflict = checkConflict(es2, reachability, graph);
      if (conflict.isPresent()) {
        inCompleteGraph.addEdges(es1);
        // System.err.printf("%s -> %s because of conflict in %s\n",
        // c.writeTransaction1, c.writeTransaction2,
        // conflict.get());
        solvedConstraints.add(c);
      }
    }

    System.err.printf("solved %d constraints\n", solvedConstraints.size());
    // constraints.removeAll(solvedConstraints);
    // java removeAll has performance bugs; do it manually
    solvedConstraints.forEach(graph::removeSuperposition);
//    return Pair.of(solvedConstraints.size(), false);
    return new PruneResult(false, 0, solvedConstraints.size(), 0, 0);
  }
}
