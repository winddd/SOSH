package graphs;

import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import util.io.DumpResult;

/**
 * This is a writer that can dumps any instance of InCompleteGraph.
 */
public class IncompletegraphWriter {
  private static String edgePairTemplate1 = "[%s], [%s]";
  private static String edgePairTemplate2 = "%s, %s";
  private InCompleteGraph G;

  public IncompletegraphWriter(InCompleteGraph G) {
    this.G = G;
  }

  public static String edge2String(int u, int v) {
    return String.format("(%d, %d)", u, v);
  }

  public static String edge2String(TypeEdge edge, boolean ignoreType) {
    if (edge == null) {
      throw new RuntimeException("");
//      System.out.printf("");
    }
    int u = edge.getSourceId().txnId();
    int v = edge.getTargetId().txnId();
    return ignoreType ? String.format("(%d, %d)", u, v)
        : String.format("(%d, %d, %s)", u, v, edge.edgeType.name());
  }

  public static String edgePair2String(TypeEdge e1, TypeEdge e2, boolean wrapWithBrackets) {
    return String.format(wrapWithBrackets ? edgePairTemplate1 : edgePairTemplate2,
        edge2String(e1, true), edge2String(e2, true));
  }

  public static String edgeSet2String(Set<TypeEdge> edgeSet) {
    List<String> edgeStrings = new ArrayList<>();
    for (var edge : edgeSet) {
      edgeStrings.add(edge2String(edge, true));
    }
    String s = "[" + edgeStrings.stream().collect(Collectors.joining(",")) + "]";
    return s;
  }

  public static String edgeSetPair2String(Set<TypeEdge> es1, Set<TypeEdge> es2) {
    var s1 = edgeSet2String(es1);
    var s2 = edgeSet2String(es2);
    return String.format("(%s, %s)", s1, s2);
  }

  public static String superposition2String(Superposition superposition) {
    List<String> strings = new ArrayList<>();
    for (var edge : superposition.getEdgeSets()) {
      strings.add(edgeSet2String(edge));
    }
    return strings.stream().collect(Collectors.joining(","));
  }

  public static String implies2String(Imply imply) {
    throw new NotImplementedException("Not updated");
    // String eString = edge2String(imply.edge, true);
    // List<String> generalExprStrs = new ArrayList<>();
    // for (var generalExpr : imply.cons) {
    // if (generalExpr instanceof TypeEdge) {
    // generalExprStrs.add(edge2String((TypeEdge) generalExpr, true));
    // } else if (generalExpr instanceof EdgePair) {
    // String t = edgePair2String((EdgePair) generalExpr, false);
    // generalExprStrs.add(String.format("Xor(%s)", t));
    // } else {
    // throw new InvalidInputException("");
    // }
    // }
    // return String.format("%s -> [%s]", eString,
    // generalExprStrs.stream().collect(Collectors.joining(",")));
  }

  /**
   *
   */
  public void dumpIntoPolygraph(String path) {
    var lines = new ArrayList<String>();
    var adjList = G.getAdjList();

    lines.add(String.format("n:%d\n", G.getNodeIds().size()));

    for (var u : adjList.keySet())
      for (var v : adjList.get(u).keySet()) {
        int encodedU = u.txnId();
        int encodedV = v.txnId();

        // Hexu doesn't care about edge types, so we don't traverse edge types.
        lines.add("e:" + edge2String(encodedU, encodedV) + "\n");
      }

    if (G instanceof BoomslangGraph) {
      var boomslangG = (BoomslangGraph) G;
      for (var nTuple : boomslangG.getSuperpositions()) {
        String eTupleStr = superposition2String(nTuple);
        // if (eTupleStr.equals("")) {
        // System.out.println();
        // }
        lines.add("c:" + eTupleStr + "\n");
      }

      for (var imply : boomslangG.getImplies()) {
        lines.add("i:" + implies2String(imply) + "\n");
      }
    }

    DumpResult.writeToFile(lines, path, false);
  }
}
