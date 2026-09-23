//package graphs;
//
//import common.Key;
//import graphs.edges.TypeEdge;
//import graphs.graphs.InCompleteGraph;
//import graphs.graphs.Polygraph;
//import graphs.edges.EdgeType;
//import java.lang.reflect.InvocationTargetException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Set;
//import org.apache.commons.lang3.tuple.Pair;
//import org.jgrapht.graph.DefaultDirectedGraph;
//import org.jgrapht.graph.DefaultEdge;
//
//public class GraphFactory {
////  public static Pair<DefaultDirectedGraph, TypePolygraph> constructCompleteJGraphAndTypeGraph(
////      int nNodes) {
////    DefaultDirectedGraph<Integer, DefaultEdge> graph =
////        new DefaultDirectedGraph<>(DefaultEdge.class);
////    TypePolygraph typePolygraph = new TypePolygraph(nNodes);
////
////    for (int i = 0; i < nNodes; i++) {
////      graph.addVertex(i);
////    }
////
////    for (int i = 0; i < nNodes; i++) {
////      for (int j = i + 1; j < nNodes; j++) {
////        graph.addEdge(i, j);
////        graph.addEdge(j, i);
////
////        typePolygraph.addTxnEdge(i, j, EdgeType.WR);
////        typePolygraph.addTxnEdge(j, i, EdgeType.WR);
////      }
////    }
////
////    return Pair.of(graph, typePolygraph);
////  }
//
//  public static Pair<DefaultDirectedGraph, Polygraph> constructGraph1() {
//    List<Pair<Integer, Integer>> edges = new ArrayList<>();
//    edges.add(Pair.of(0, 1));
//    edges.add(Pair.of(0, 2));
//    edges.add(Pair.of(2, 3));
//    edges.add(Pair.of(3, 1));
//
//    return constructGraph(4, edges);
//  }
//
//  public static Pair<DefaultDirectedGraph, Polygraph> constructGraph2() {
//    List<Pair<Integer, Integer>> edges = new ArrayList<>();
//    edges.add(Pair.of(0, 1));
//    edges.add(Pair.of(1, 2));
//    edges.add(Pair.of(2, 3));
//    edges.add(Pair.of(3, 1));
//
//    return constructGraph(4, edges);
//  }
//
//  private static Pair<DefaultDirectedGraph, Polygraph> constructGraph(int nNodes,
//                                                                      List<Pair<Integer, Integer>> edges) {
//    DefaultDirectedGraph<Integer, DefaultEdge> graph =
//        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
//    Polygraph typePolygraph = new Polygraph(Set.of(1, 2, 3, 4));
//
//    for (int i = 0; i < nNodes; i++) {
//      graph.addVertex(i);
//    }
//
//    for (Pair<Integer, Integer> edge : edges) {
//      int i = edge.getLeft();
//      int j = edge.getRight();
//      graph.addEdge(i, j);
//      typePolygraph.addEdge(new TypeEdge(i, j, EdgeType.WR, Key.getNullKey()));
//    }
//
//    return Pair.of(graph, typePolygraph);
//  }
//
//  public static InCompleteGraph mkGraph(int numTxns, Class graphClass) {
//    InCompleteGraph G;
//    try {
//      G = (InCompleteGraph) graphClass.getConstructor(int.class)
//          .newInstance(numTxns);
//    } catch (NoSuchMethodException | InvocationTargetException
//             | InstantiationException | IllegalAccessException e) {
//      throw new RuntimeException(e);
//    }
//    return G;
//  }
//}
