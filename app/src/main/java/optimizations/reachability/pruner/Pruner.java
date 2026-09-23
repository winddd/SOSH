package optimizations.reachability.pruner;

import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.MatrixGraph;
import graphs.nodes.GraphNodeId;
import java.util.Collection;
import util.Config;
import util.isolation.PruningSpec;

public abstract class Pruner {
  protected Config cfg;
  private final PruningSpec pruningSpec;

  public Pruner(Config cfg) {
    this.cfg = cfg;
    this.pruningSpec = cfg.getIsolationSpec().getPruningSpec();
  }

  protected boolean reach(GraphNodeId from, GraphNodeId to, MatrixGraph rm) {
    return rm.reach(rm.indexOfNode(from), rm.indexOfNode(to));
  }

  protected boolean includeForPruning(TypeEdge edge) {
    return pruningSpec.includeForPruning(edge.edgeType);
  }

  protected void addEdges2MatrixGraph(MatrixGraph matrix, Collection<TypeEdge> es) {
    for (var edge : es) {
      if (includeForPruning(edge)) {
        matrix.set(matrix.indexOfNode(edge.getSourceId()),
            matrix.indexOfNode(edge.getTargetId()));
      }
    }
  }

  public abstract PruneResult prune(InCompleteGraph graph);
}
