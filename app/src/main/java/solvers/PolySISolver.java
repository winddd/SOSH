package solvers;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;

import common.Key;
import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.PolySIMatrixGraph;
import graphs.graphs.interfaces.PolySIABGraph;
import history.KVHistory;
import lombok.extern.slf4j.Slf4j;
import monosat.Lit;
import monosat.Logic;
import util.Config;
import util.Utils;

/**
 * A solver that
 * - combines multiple edges into single one
 * - implements PolySI's SAT/SMT encoding.
 * 
 * I didn't fit this solver in the SMTSolver inferface due to limited time.
 * Instead, I leave everything in the `solver` function.
 */
@Slf4j
public class PolySISolver extends SMTSolver {
  private final Map<Lit, Pair<Pair<Integer, Integer>, Collection<Pair<EdgeType, Key>>>> knownLiterals = new HashMap<>();
  private final Map<Lit, Superposition> superpositionLiterals = new HashMap<>();
  private final PolySIABGraph graph;

  public PolySISolver(PolySIABGraph graph, String tagPrefix, Config cfg) {
    super(tagPrefix, cfg);
    this.graph = graph;
  }

  @Override
  public void addNode(graphs.nodes.GraphNodeId nodeId) {
    return;
  }

  @Override
  public boolean addEdge(TypeEdge e) {
    return false;
  }

  @Override
  public boolean addConstraint(GeneralizedConstraint con) {
    return false;
  }

  @Override
  public boolean addSuperposition(Superposition superposition) {
    return false;
  }

  @Override
  public boolean addImply(Imply imply) {
    throw new RuntimeException("shouldn't call this function");
  }

  // @Override
  // public boolean addImply(Imply imply, int[] priorities) {
  // throw new RuntimeException("shouldn't call this function");
  // }

  @Override
  public boolean enforceAcyclic() {
    return false;
  }

  @Override
  public boolean solve() {
    profiler.startTick(tagPrefix + "encode");
    // profiler.startTick("createKnownGraph");
    var graphA = createKnownGraph(graph.getHistory(), graph.getGraphA());
    var graphB = createKnownGraph(graph.getHistory(), graph.getGraphB());
    // profiler.endTick("createKnownGraph");

    var matA = new PolySIMatrixGraph<>(graphA.asGraph());
    var orderInSession = Utils.getOrderInSession(graph.getHistory());
    var matAB = matA.composition(new PolySIMatrixGraph<>(graphB.asGraph(), matA.getNodeMap()));
    var matAC = Utils.reduceEdges(
        matA.union(matAB),
        graph.getHistory(), orderInSession);
    // profiler.startTick("matAC reach");
    var reachability = matAC.reachability();
    // profiler.endTick("matAC reach");

    // profiler.startTick("getKnownEdges/addC
    var knownEdges = Utils.getKnownEdges(graphA, graphB, matAC);
    addSuperpositions(graph.getSuperpositions(), graphA, graphB);
    var unknownEdges = Utils.getUnknownEdges(graphA, graphB, reachability, solver);
    // profiler.endTick("getKnownEdges/addConstraints/getUnknownEdges");

    // profiler.startTick("bing to MonoGraph");
    var monoGraph = new monosat.Graph(solver);
    var nodeMap = new HashMap<Integer, Integer>();

    graph.getHistory().getAllTxns().forEach(n -> {
      nodeMap.put(n.getTxnId(), monoGraph.addNode());
    });

    var addToMonoSAT = ((Consumer<Triple<Integer, Integer, Lit>>) e -> {
      var n = e.getLeft();
      var s = e.getMiddle();
      // assert the BV edge variables and the MonoSAT native edge variables share the
      // same truth value, bind them.
      solver.assertEqual(e.getRight(),
          monoGraph.addEdge(nodeMap.get(n), nodeMap.get(s)));
    });

    knownEdges.forEach(addToMonoSAT);
    unknownEdges.forEach(addToMonoSAT);
    solver.assertTrue(monoGraph.acyclic());
    // profiler.endTick("bind to MonoGraph");
    profiler.endTick(tagPrefix + "encode");

    return solveInternal();
  }

  boolean solveInternal() {
    var lits = Stream
        .concat(knownLiterals.keySet().stream(),
            superpositionLiterals.keySet().stream())
        .collect(Collectors.toList());
    profiler.startTick(tagPrefix + "solving");
    var result = solver.solve(lits);
    profiler.endTick(tagPrefix + "solving");
    return result;
  }

  private void addEdgeToAB(MutableValueGraph<Integer, Collection<Lit>> g, int u, int v, Lit lit) {
    if (!g.hasEdgeConnecting(u, v)) {
      g.putEdgeValue(u, v, new HashSet<>());
    }
    g.edgeValue(u, v).get().add(lit);
  }

  private MutableValueGraph<Integer, Collection<Lit>> createKnownGraph(
      KVHistory history,
      ValueGraph<Integer, Collection<Pair<EdgeType, Key>>> knownGraph) {
    var g = Utils.createEmptyGraph(history);
    for (var e : knownGraph.edges()) {
      var lit = new Lit(solver);
      knownLiterals.put(lit, Pair.of(Pair.of(e.source(), e.target()), knownGraph.edgeValue(e).get()));
      Utils.addEdge(g, e.source(), e.target(), lit);
    }

    return g;
  }

  private Lit addUnknownEdge(
      TypeEdge e,
      MutableValueGraph<Integer, Collection<Lit>> graphA,
      MutableValueGraph<Integer, Collection<Lit>> graphB) {
    var lit = edgeLitMap.get(e);
    if (lit == null) {
      lit = new Lit(solver);
      registerEdgeLiteral(e, lit);
      if (cfg.WEIGHT_GUIDED_SEARCH) {
        solver.setDecisionPriority(lit, e.getPriority());
      }
    }

    if (e.edgeType.equals(EdgeType.WW)) {
      addEdgeToAB(graphA, e.u, e.v, lit);
    } else if (e.edgeType.equals(EdgeType.RW)) {
      addEdgeToAB(graphB, e.u, e.v, lit);
    } else {
      throw new RuntimeException("only WW and RW edges should appear in PolySI superpositions");
    }

    return lit;
  }

  private Pair<Lit, Lit> addEdgeSet(
      Collection<TypeEdge> edges,
      MutableValueGraph<Integer, Collection<Lit>> graphA,
      MutableValueGraph<Integer, Collection<Lit>> graphB) {
    // all means all edges exists in the graph.
    // Similar for none.
    Lit all = Lit.True, none = Lit.True;
    for (var e : edges) {
      var lit = addUnknownEdge(e, graphA, graphB);
      var not = Logic.not(lit);
      all = Logic.and(all, lit);
      none = Logic.and(none, not);
        // ???
        solver.setDecisionLiteral(lit, false);
        solver.setDecisionLiteral(not, false);
        solver.setDecisionLiteral(all, false);
        solver.setDecisionLiteral(none, false);

      }
      return Pair.of(all, none);
  }

  private void addSuperpositions(
      Collection<Superposition> superpositions,
      MutableValueGraph<Integer, Collection<Lit>> graphA,
      MutableValueGraph<Integer, Collection<Lit>> graphB) {
    for (var superposition : superpositions) {
      var alternatives = superposition.getEdgeSets().stream()
          .map(edges -> addEdgeSet(edges, graphA, graphB))
          .collect(Collectors.toList());

      var choices = new java.util.ArrayList<Lit>();
      for (int i = 0; i < alternatives.size(); i++) {
        var choice = alternatives.get(i).getLeft();
        for (int j = 0; j < alternatives.size(); j++) {
          if (i != j) {
            choice = Logic.and(choice, alternatives.get(j).getRight());
          }
        }
        choices.add(choice);
      }

      var lit = Logic.or(choices);
      superpositionLiterals.put(lit, superposition);
      associateLiteralWithObject(lit, superposition);
    }
  }

  @Override
  public void printConflictClauses() {
    return;
  }
}
