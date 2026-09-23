package optimizations.reachability.pruner;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.BitmapMatrixGraph;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.MatrixGraph;
import graphs.graphs.NodeIndexer;
import graphs.graphs.MatrixGraphFactory;
import graphs.graphs.interfaces.HasGeneralConstraints;
import graphs.graphs.interfaces.HasSuperpositions;
import lombok.extern.slf4j.Slf4j;
import optimizations.prioritization.TopoSort;
import util.Config;
import util.exception.RejectException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

@Slf4j
public class BoomslangPruner extends Pruner {
  public BoomslangPruner(Config cfg) {
    super(cfg);
  }

  private boolean likely(Collection<TypeEdge> es, MatrixGraph rm) {
    for (var edge : es) {
      if (includeForPruning(edge)
          && reach(edge.getTargetId(), edge.getSourceId(), rm)) {
        return false;
      }
    }
    return true;
  }

  boolean conflict(Collection<TypeEdge> es, MatrixGraph rm) {
    return !likely(es, rm);
  }

  public boolean pruneGeneralizedConstraint(GeneralizedConstraint con, MatrixGraph rm, InCompleteGraph graph) {
    var es1 = con.getEdgeSet1();
    var es2 = con.getEdgeSet2();

    if (conflict(es1, rm)) {
      graph.addEdges(es2);
      addEdges2MatrixGraph(rm, es2);
      return true;
    }

    if (conflict(es2, rm)) {
      graph.addEdges(es1);
      addEdges2MatrixGraph(rm, es1);
      return true;
    }

    return false;
  }

  public int simplifySuperposition(final Superposition originalSuperposition, MatrixGraph rm, InCompleteGraph graph) {
    // a copy of edge set
    var edgeSets = new ArrayList<>(originalSuperposition.getEdgeSets());
    var toBeDeletedEdgeSets = new HashSet<>();

    for (var es : edgeSets) {
      if (conflict(es, rm)) {
        toBeDeletedEdgeSets.add(es);
      }
    }

    for (var es : toBeDeletedEdgeSets) {
      edgeSets.remove(es);
    }

    // potential downgrade of superposition
    if (edgeSets.size() == 1) {
      var edges = edgeSets.iterator().next();
      log.debug("Simplified {} to be {}", originalSuperposition, edges);
      graph.addEdges(edges);
      // TODO: uncomment this line later, this is only for unsat search eval.
      if (!cfg.INJECT_UNSATCORE) {
        addEdges2MatrixGraph(rm, edges);
      }
      return 2;
    } else if (edgeSets.isEmpty()) {
      log.info("This superposition can never hold:" + originalSuperposition);
      throw new RejectException("");
    } else {
      if (toBeDeletedEdgeSets.isEmpty()) {
        return 0;
      } else {
        originalSuperposition.setEdgeSets(edgeSets);
        return 1;
      }
    }
  }

  @Override
  public PruneResult prune(InCompleteGraph graph) {
    NodeIndexer indexer = NodeIndexer.from(graph.getNodeIds());
    MatrixGraph reachMatrix = MatrixGraphFactory.getMatrixGraph(
        indexer, cfg.BFS_PRUNING,
        cfg.MATRIX_PARTITION, cfg.DEBUG, cfg.RUNMODE, cfg);
    reachMatrix.buildFromGraphForPruning(graph, cfg);
    log.info("Computing transitive closure...");
    assert reachMatrix instanceof BitmapMatrixGraph;
    ((BitmapMatrixGraph) reachMatrix).transitiveClosureInplace();

//    profiler.startTick("prune");
    int numSolvedGeneralCons = 0, numSolvedSuperpositions = 0, numSimplifiedSuperpositions = 0;

    log.debug("Starting pruning general constraints");
    if (graph instanceof HasGeneralConstraints) {
      var cons = ((HasGeneralConstraints) graph).getGeneralCons();
      var toBeRemoved = new HashSet<GeneralizedConstraint>();

      for (var con : cons) {
        if (pruneGeneralizedConstraint(con, reachMatrix, graph)) {
          numSolvedGeneralCons++;
          toBeRemoved.add(con);
        }
      }

      for(var con: toBeRemoved) {
        ((HasGeneralConstraints) graph).removeGeneralizedConstraint(con);
      }
    }

    if (graph instanceof HasSuperpositions) {
      log.debug("Starting pruning superpositions");
      var toBeRemoved = new HashSet<Superposition>();
      var superpositions = ((HasSuperpositions) graph).getSuperpositions();

      for (var superposition : superpositions) {
        var ret = simplifySuperposition(superposition, reachMatrix, graph);
        switch (ret) {
          case 1 -> {
            numSimplifiedSuperpositions ++;
          }
          case 2 -> {
            numSolvedSuperpositions ++;
            toBeRemoved.add(superposition);
          }
        }
      }

      for(var superposition: toBeRemoved) {
        ((HasSuperpositions) graph).removeSuperposition(superposition);
      }
    }

//    profiler.endTick("prune");
    boolean hasCycles = (TopoSort.topoSort(graph, cfg.DEBUG) == null);
    return new PruneResult(hasCycles, 0, numSolvedGeneralCons, numSolvedSuperpositions, numSimplifiedSuperpositions);
  }
}
