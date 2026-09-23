//package graphs.graphs;
//
//import common.Key;
//import graphs.edges.EdgeType;
//import graphs.edges.TypeEdge;
//import util.Config;
//import util.JGraphTConstructor;
//import util.exception.RejectException;
//
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.BitSet;
//import java.util.List;
//import java.util.stream.Collectors;
//import java.util.stream.IntStream;
//
//public class BitmapMatrixGraph_bk extends MatrixGraph {
////  private RoaringBitmap[] adjacency;
//  private BitSet[] adjacency;
//
//  public BitmapMatrixGraph_bk(int numNodes) {
//    super();
////    adjacency = new RoaringBitmap[numNodes];
//    adjacency = new BitSet[numNodes];
//    for (int i = 0; i < numNodes; i ++) {
////      adjacency[i] = new RoaringBitmap();
//      adjacency[i] = new BitSet();
//      // ensures that the BitSet has an internal capacity to accommodate bits from indexes 0 to size - 1 inclusive
//      adjacency[i].set(numNodes-1);
//      adjacency[i].clear(numNodes-1);
//    }
//  }
//
//  @Override
//  public void set(int i, int j) {
////    adjacency[i].add(j);
//    adjacency[i].set(j);
//  }
//
//  @Override
//  public int get(int i, int j) {
////    return adjacency[i].contains(j) ? 1 : 0;
//    return adjacency[i].get(j) ? 1 : 0;
//  }
//
//  private List<Integer> topoSort() {
////    profiler.startTick("topoSort in transitive closure");
//    var queue = new ArrayDeque<Integer>();
//    var sortedNodes = new ArrayList<Integer>();
//    var inDegrees = new int[adjacency.length];
//
//    for (var i = 0; i < adjacency.length; i++) {
////      for (var j : adjacency[i]) {
////        inDegrees[j]++;
////      }
//
//      for (int j = adjacency[i].nextSetBit(0); j >= 0; j = adjacency[i].nextSetBit(j + 1)) {
//        // Print the index of each set bit
//        inDegrees[j] ++;
//      }
//    }
//
//    for (var i = 0; i < adjacency.length; i++) {
//      if (inDegrees[i] == 0) {
//        queue.add(i);
//      }
//    }
//
//    while (!queue.isEmpty()) {
//      var u = queue.pop();
//      sortedNodes.add(u);
//
//      adjacency[u].stream().forEach(v -> {
//        inDegrees[v]--;
//
//        if (inDegrees[v] == 0) {
//          queue.add(v);
//        }
//      });
//    }
//
////    profiler.endTick("topoSort in transitive closure");
//    if (sortedNodes.size() != adjacency.length) {
//      return null;
//    }
//
//    return sortedNodes;
//  }
//
////  public BitmapMatrixGraph transitiveClosure() {
////    var topoOrder = topoSort();
////    if (topoOrder != null) {
////      var result = new BitmapMatrixGraph(adjacency.length);
////
////      for (var i = topoOrder.size() - 1; i >= 0; i--) {
////        var u = topoOrder.get(i);
////
////        for (var v : adjacency[u].stream().toArray()) {
////          assert topoOrder.indexOf(v) > i; // successor order must be kept in topo order
////          result.set(u, v);
////          result.adjacency[u].or(result.adjacency[v]);
////        }
////      }
////
////      return result;
////    } else {
////      throw new RejectException("Already have cycles");
////    }
////  }
//
//  public void transitiveClosureInplace() {
////    profiler.startTick("transitiveClousre");
//    var topoOrder = topoSort();
//    if (topoOrder != null) {
//      for (var i = topoOrder.size() - 1; i >= 0; i--) {
//        var u = topoOrder.get(i);
//
//        for (var v : adjacency[u].stream().toArray()) {
////          if (v == 1000) {
////            System.out.println();
////          }
//          assert topoOrder.indexOf(v) > i; // successor order must be kept in topo order
//          adjacency[u].or(adjacency[v]);
//        }
//      }
////      profiler.endTick("transitiveClousre");
//    } else {
////      profiler.endTick("transitiveClousre");
//      if (Config.get().DEBUG) {
//        var txnIds = IntStream.rangeClosed(0, adjacency.length-1)
//            .boxed()
//            .collect(Collectors.toSet());
//        Polygraph graph = new Polygraph(txnIds);
//        for (int u = 0; u < adjacency.length; u++) {
////          int finalU = u;
////          adjacency[u].forEach(((int v) -> graph.addEdge(new TypeEdge(finalU, v, EdgeType.WW, Key.getNullKey()))));
//          for (int j = adjacency[u].nextSetBit(0); j >= 0; j = adjacency[u].nextSetBit(j + 1)) {
//            // Print the index of each set bit
//            graph.addEdge(new TypeEdge(u, j, EdgeType.WW, Key.getNullKey()));
//          }
//        }
//        new JGraphTConstructor().cycleDetectionInKnownGraph(graph);
//      }
//      throw new RejectException("Cycle found in pruning");
//    }
//  }
//
////  public boolean equals(Object o) {
////    if (!(o instanceof BitmapMatrixGraph))
////      return false;
////    var o1 = (BitmapMatrixGraph) o;
////    for (int i = 0; i < adjacency.length; i ++) {
////      if (adjacency[i].hashCode() != o1.adjacency[i].hashCode())
////        return false;
////    }
////    return true;
////  }
//}
