package util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import common.Codec;
import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.InCompleteGraph;
import graphs.graphs.PolySIMatrixGraph;
import graphs.graphs.interfaces.HasGeneralConstraints;
import graphs.graphs.interfaces.HasSuperpositions;
import history.KVHistory;
import history.KVTxn;
import monosat.Lit;
import monosat.Logic;
import monosat.Solver;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import util.enumtypes.HISTORY_FORMAT;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Utils {
  public static Map<KVTxn, Integer> getOrderInSession(KVHistory history) {
    Map<KVTxn, Integer> orderInSession = new HashMap<>();
    var tids = history.getThreadIds();
    for (var tid : tids) {
      var txns = history.getTxnsByTid(tid);
      int rank = 1;
      for (var txn : txns) {
        orderInSession.put(txn, rank++);
      }
    }
    return orderInSession;
  }

  public static PolySIMatrixGraph<Integer> reduceEdges(PolySIMatrixGraph<Integer> graph, KVHistory history, Map<KVTxn, Integer> orderInSession) {
    var newGraph = PolySIMatrixGraph.ofNodes(graph);

    for (var n : graph.nodes()) {
      var succ = graph.successors(n);
      // @formatter:off
      var firstInSession = succ.stream()
          .collect(Collectors.toMap(
              m -> history.getKthTxn(m).getThreadId(),
              Function.identity(),
              (p, q) -> orderInSession.get(history.getKthTxn(p))
                  < orderInSession.get(history.getKthTxn(q)) ? p : q));

      // only add edge from n to the first successor in each session
      firstInSession.values().forEach(m -> newGraph.putEdge(n, m));

      // Jian: this is for direct SO edges
      succ.stream()
          .filter(m -> history.getKthTxn(m).getThreadId() == history.getKthTxn(n).getThreadId()
              && orderInSession.get(history.getKthTxn(m)) == orderInSession.get(history.getKthTxn(n)) + 1)
          .forEach(m -> newGraph.putEdge(n, m));
      // @formatter:on
    }

    System.err.printf("After: %d edges\n", newGraph.edges().size());
    return newGraph;
  }

  public static List<Triple<Integer, Integer, Lit>> getKnownEdges(
      MutableValueGraph<Integer, Collection<Lit>> graphA,
      MutableValueGraph<Integer, Collection<Lit>> graphB,
      PolySIMatrixGraph<Integer> AC) {
    return AC.edges().stream().map(e -> {
      var n = e.source();
      var m = e.target();
      var firstEdge = ((Function<Optional<Collection<Lit>>, Lit>) c -> c.get().iterator().next());

      // Jian: this is actually combine known edges? See CreateKnownGraph.
      if (graphA.hasEdgeConnecting(n, m)) {
        assert graphA.edgeValue(n, m).get().size() == 1;
        return Triple.of(n, m, firstEdge.apply(graphA.edgeValue(n, m)));
      }

      var middle = Sets.intersection(graphA.successors(n), graphB.predecessors(m)).iterator().next();
      return Triple.of(n, m, Logic.and(firstEdge.apply(graphA.edgeValue(n, middle)),
          firstEdge.apply(graphB.edgeValue(middle, m))));
    }).collect(Collectors.toList());
  }

  public static List<Triple<Integer, Integer, Lit>> getUnknownEdges(MutableValueGraph<Integer, Collection<Lit>> graphA,
                                       MutableValueGraph<Integer, Collection<Lit>> graphB,
                                       PolySIMatrixGraph<Integer> reachability, Solver solver) {
    var edges = new ArrayList<Triple<Integer, Integer, Lit>>();

    // graphA contains all the known/unknown WW/WR/SO edges
    // graphB contains all the known/unknown RW edges
    // trying to collect all the edges
    for (var p : graphA.nodes()) {
      for (var n : graphA.successors(p)) {
        var predEdges = graphA.edgeValue(p, n).get();

        if (p == n || !reachability.hasEdgeConnecting(p, n)) {
          // if BV[p,n] and (p,n) \in E_Dep
          predEdges.forEach(e -> edges.add(Triple.of(p, n, e))); // WW/WR/SO edges
        }

        var txns = graphB.successors(n).stream()
            .filter(t -> p == t || !reachability.hasEdgeConnecting(p, t))
            .collect(Collectors.toList());

        for (var s : txns) {
          var succEdges = graphB.edgeValue(n, s).get();
          predEdges.forEach(e1 -> succEdges.forEach(e2 -> {
            var lit = Logic.and(e1, e2); // BV^I
            solver.setDecisionLiteral(lit, false);
            edges.add(Triple.of(p, s, lit)); // composition of WW/WR/SO and RW edges
          }));
        }
      }

    }
    return edges;
  }

  public static MutableValueGraph<Integer, Collection<Lit>> createEmptyGraph(
      KVHistory history) {
    MutableValueGraph<Integer, Collection<Lit>> g = ValueGraphBuilder.directed()
        .allowsSelfLoops(false).build();

    history.getAllTxns().forEach(txn -> g.addNode(txn.getTxnId()));
    return g;
  }

  public static void addEdge(MutableValueGraph<Integer, Collection<Lit>> g,
                                           Integer src, Integer dst, Lit lit) {
    if (!g.hasEdgeConnecting(src, dst)) {
      g.putEdgeValue(src, dst, new ArrayList<>());
    }
    g.edgeValue(src, dst).get().add(lit);
  }

  public static String edgeSet2String(Collection<TypeEdge> es, Map<Integer, String> new2OldTxnMap) {
    StringBuilder builder = new StringBuilder();
    for (var edge : es) {
      builder.append(edge2String(edge, new2OldTxnMap)).append("\n");
    }
    return builder.toString();
  }

  private static String getCobraTxnId(Map<Integer, String> new2OldTxnMap, int boomslangTxnId) {
    return new2OldTxnMap.getOrDefault(boomslangTxnId, "0");
  }

  public static String edge2String(TypeEdge edge, Map<Integer, String> new2OldTxnMap) {
    return String.format("T[%s] -%s(%s)-> T[%s]",
        getCobraTxnId(new2OldTxnMap, edge.u),
        edge.edgeType.name(), edge.key.equals(Key.getNullKey()) ? "nil" : Long.toHexString(Codec.decode(edge.key.getBytes())),
        getCobraTxnId(new2OldTxnMap, edge.v));
  }

//  public static String edgeTypeKey2String(Pair<EdgeType, Key> pair, HISTORY_FORMAT format) {
//    var edgeType = pair.getLeft();
//    var key = pair.getRight();
//    if (format == HISTORY_FORMAT.COBRA) {
//      return String.format("(%s, %s)",
//          pair.getLeft().name(),
//          key.equals(Key.getNullKey()) ? "nil" : Long.toHexString(Codec.decode(key.getBytes())));
//    } else {
//      return String.format("(%s, %s)",
//          pair.getLeft().name(),
//          key.equals(Key.getNullKey()) ? "nil" : key.toString());
//    }
//  }

  public static String bytes2Hex(byte[] bytes) {
    byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    byte[] hexChars = new byte[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars, StandardCharsets.UTF_8);
  }

  public static Set<Key> subsetWithExclude(Set<Key> allKeys, Key key1, Key key2, Set<Key> excluding) {
    assert key1.compareTo(key2) < 0;
    Set<Key> set1 = new HashSet<>();

    for (var key : allKeys) {
      if (key.inBetween(key1, key2) && !excluding.contains(key)) {
        set1.add(key);
      }
    }

    return set1;
  }

  public static Key getMaxKey(Collection<Key> keys) {
    if (keys.isEmpty())
      return Key.getMinKey();
    assert !keys.isEmpty();

    List<Key> keyList = new ArrayList(keys);
    Key maxKey = keyList.get(0);

    for (var key : keys) {
      if (key.compareTo(maxKey) > 0) {
        maxKey = key;
      }
    }

    return maxKey;
  }

  public static void serialize(Object obj, String fileName) {
    try (FileOutputStream fileOut = new FileOutputStream(fileName);
         ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
      out.writeObject(obj);
      System.out.println("Serialized data is saved in " + fileName);
    } catch (IOException i) {
      i.printStackTrace();
    }
  }

  public static Object deserialize(String fileName) {
    Object obj = null;

    try (FileInputStream fileIn = new FileInputStream(fileName)) {
      ObjectInputStream objectIn = new ObjectInputStream(fileIn);
      // Deserialize the object
      obj = (Object) objectIn.readObject();
      // Print the deserialized object
    } catch (IOException e) {
      System.err.println("IOException occurred: " + e.getMessage());
    } catch (ClassNotFoundException e) {
      System.err.println("ClassNotFoundException occurred: " + e.getMessage());
    }
    return obj;
  }

  public static Config createConfig(String cfgFile) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Config cfg = null;
    try {
      cfg = mapper.readValue(new File(cfgFile), Config.class);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }
    return cfg;
  }
}
