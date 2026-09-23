package optimizations.reachability.pruner;

import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.ArrayMatrixGraph;
import graphs.graphs.NodeIndexer;
import graphs.nodes.GraphNodeId;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;
import util.Config;
import util.enumtypes.MODE;

/** Regression tests for isolation-aware reachability pruning. */
public class BoomslangPrunerTest {
  private static final GraphNodeId T1 = GraphNodeId.txn(1);
  private static final GraphNodeId T2 = GraphNodeId.txn(2);
  private static final Key KEY = Key.getNullKey();

  private ArrayMatrixGraph reversePathMatrix() {
    var matrix = new ArrayMatrixGraph(NodeIndexer.from(List.of(T1, T2)));
    matrix.set(matrix.indexOfNode(T2), matrix.indexOfNode(T1));
    return matrix;
  }

  private TypeEdge edge(EdgeType type) {
    return new TypeEdge(T1, T2, type, KEY);
  }

  @Test
  public void dependencyOnlyModesIgnoreAntiDependencyCandidates() {
    for (var mode : List.of(MODE.B_PL2P, MODE.B_PLCS, MODE.B_PLFCV)) {
      var cfg = new Config();
      cfg.RUNMODE = mode;
      var pruner = new BoomslangPruner(cfg);
      var matrix = reversePathMatrix();

      for (var edgeType : List.of(EdgeType.RW, EdgeType.PRW)) {
        Assert.assertFalse(pruner.conflict(Set.of(edge(edgeType)), matrix),
            mode + " must leave anti-dependency constraints to its specialized solver");
      }
      Assert.assertTrue(pruner.conflict(Set.of(edge(EdgeType.WW)), matrix),
          mode + " must still prune a dependency edge that creates a G1 cycle");
    }
  }

  @Test
  public void forcedAntiDependenciesDoNotPolluteDependencyClosure() {
    for (var mode : List.of(MODE.B_PL2P, MODE.B_PLCS, MODE.B_PLFCV)) {
      var cfg = new Config();
      cfg.RUNMODE = mode;
      var pruner = new BoomslangPruner(cfg);
      var matrix = new ArrayMatrixGraph(NodeIndexer.from(List.of(T1, T2)));

      pruner.addEdges2MatrixGraph(
          matrix, Set.of(edge(EdgeType.RW), edge(EdgeType.PRW)));
      Assert.assertEquals(
          matrix.get(matrix.indexOfNode(T1), matrix.indexOfNode(T2)), 0);

      pruner.addEdges2MatrixGraph(matrix, Set.of(edge(EdgeType.WW)));
      Assert.assertEquals(
          matrix.get(matrix.indexOfNode(T1), matrix.indexOfNode(T2)), 1);
    }
  }

  @Test
  public void serializabilityStillChecksAntiDependencyCandidates() {
    var cfg = new Config();
    cfg.RUNMODE = MODE.B_SER;
    var pruner = new BoomslangPruner(cfg);

    Assert.assertTrue(pruner.conflict(
        Set.of(edge(EdgeType.RW)), reversePathMatrix()));
  }
}
