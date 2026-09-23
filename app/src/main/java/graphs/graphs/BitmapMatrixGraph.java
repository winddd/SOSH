package graphs.graphs;

import org.roaringbitmap.RoaringBitmap;
import util.Config;
import util.JGraphTConstructor;
import util.enumtypes.MODE;
import util.exception.RejectException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class BitmapMatrixGraph extends MatrixGraph {
  private RoaringBitmap[] adjacency;
  private boolean debug;
  private MODE mode;
  private Config cfg;
  private InCompleteGraph sourceGraph; // Reference to original graph for debug output

  public BitmapMatrixGraph(NodeIndexer indexer, boolean debug, MODE mode, Config cfg) {
    super(indexer);
    this.debug = debug;
    this.mode = mode;
    this.cfg = cfg;
    adjacency = new RoaringBitmap[indexer.size()];
    for (int i = 0; i < adjacency.length; i ++) {
      adjacency[i] = new RoaringBitmap();
    }
  }

  @Override
  public void buildFromGraphForPruning(InCompleteGraph graph, Config cfg) {
    this.sourceGraph = graph; // Store reference for debug cycle detection
    super.buildFromGraphForPruning(graph, cfg);
  }

  @Override
  public void set(int i, int j) {
    adjacency[i].add(j);
  }

  @Override
  public int get(int i, int j) {
    return adjacency[i].contains(j) ? 1 : 0;
  }

  private List<Integer> internalTopoSort() {
    var queue = new ArrayDeque<Integer>();
    var sortedNodes = new ArrayList<Integer>();
    var inDegrees = new int[adjacency.length];

    // Calculate in-degrees, skipping self-loops
    for (var i = 0; i < adjacency.length; i++) {
      for (var j : adjacency[i]) {
        if (i != j) { // Skip self-loops
          inDegrees[j]++;
        }
      }
    }

    for (var i = 0; i < adjacency.length; i++) {
      if (inDegrees[i] == 0) {
        queue.add(i);
      }
    }

    while (!queue.isEmpty()) {
      var u = queue.pop();
      sortedNodes.add(u);

      adjacency[u].stream().forEach(v -> {
        if (u != v) { // Skip self-loops
          inDegrees[v]--;

          if (inDegrees[v] == 0) {
            queue.add(v);
          }
        }
      });
    }

    if (sortedNodes.size() != adjacency.length) {
      return null;
    }

    return sortedNodes;
  }

  public void transitiveClosureInplace() {
    var topoOrder = internalTopoSort();
    if (topoOrder != null) {
      for (var i = topoOrder.size() - 1; i >= 0; i--) {
        var u = topoOrder.get(i);

        for (var v : adjacency[u].stream().toArray()) {
          assert topoOrder.indexOf(v) > i; // successor order must be kept in topo order
          adjacency[u].or(adjacency[v]);
        }
      }
    } else {
      if (debug && sourceGraph != null) {
        // Use the original source graph which has real edge types and keys
        new JGraphTConstructor().cycleDetectionInKnownGraph(sourceGraph, mode, cfg.NUM_CYCLES_TO_PRINT);
      }
      throw new RejectException("Cycle found in pruning");
    }
  }
}
