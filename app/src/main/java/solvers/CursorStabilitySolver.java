package solvers;

import static monosat.Logic.implies;
import static monosat.Logic.not;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import common.Key;
import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.BoomslangGraph;
import graphs.nodes.GraphNodeId;
import lombok.extern.slf4j.Slf4j;
import monosat.Graph;
import monosat.Lit;
import util.Config;
import util.MapFactory;

/**
 * SMT solver for verifying Cursor Stability (PL-CS) isolation guarantees.
 *
 * <p>
 * Cursor Stability enforces two key constraints:
 * </p>
 * <ul>
 * <li><b>Constraint 1 (G1 in DSG):</b> No cycles consisting entirely of
 * dependency edges (WR, WW, PWR, CB) in the global DSG.</li>
 * <li><b>Constraint 2 (G-cursor in LDSG_x):</b> For every object x,
 * no cycle with exactly one anti-dependency edge (RW) and one or more
 * write-depndency edges in the LDSG.
 * Specifically, for each RW edge (u, v) on key x, there must be no
 * dependency path from v to u in LDSG_x.</li>
 * </ul>
 *
 * <p>
 * <b>Encoding Strategy:</b>
 * </p>
 * <ul>
 * <li>One global DSG graph for all dependency edges to enforce G1
 * acyclicity</li>
 * <li>One LDSG_x graph per key x to enforce G-cursor</li>
 * <li>RW edges stored as Boolean variables (not added to graphs)</li>
 * </ul>
 *
 * @see SMTSolver
 * @see BoomslangGraph
 */
@Slf4j
public class CursorStabilitySolver extends SMTSolver {
  /**
   * Global graph for all Regular edges (WR, WW, CB) - used for Constraint 1 (G1c)
   */
  private Graph globalRegularGraph;

  /** Maps GraphNodeId to internal MonoSAT node IDs in the global graph */
  private Map<GraphNodeId, Integer> globalNodeMap;

  /** Maps (u, v) pairs to literals for known Regular edges in global graph */
  private Map<Pair<Integer, Integer>, Lit> globalKnownEdgeLiterals;

  /** Primary LDSG graph containing edges, superpositions, and implications */
  private BoomslangGraph dsg;

  public CursorStabilitySolver(String tagPrefix, Config cfg, List<BoomslangGraph> boomslangGraphs) {
    super(tagPrefix, cfg);
    if (boomslangGraphs == null || boomslangGraphs.size() != 1) {
      throw new IllegalArgumentException("CursorStabilitySolver requires exactly one graph");
    }
    this.dsg = boomslangGraphs.get(0);
    this.globalRegularGraph = new Graph(solver);
    this.globalNodeMap = MapFactory.getEmptyMap(cfg.DEBUG);
    this.globalKnownEdgeLiterals = MapFactory.getEmptyMap(cfg.DEBUG);
  }

  /**
   * Adds a transaction node to the global graph.
   */
  public void addNode(GraphNodeId nodeId) {
    assert !globalNodeMap.containsKey(nodeId);
    int innerNid = globalRegularGraph.addNode();
    globalNodeMap.put(nodeId, innerNid);
  }

  public boolean solve() {
    log.info("Solving Cursor Stability constraints");
    encodeKnownEdges();
    encodeSuperpositions();
    encodeImplications();
    enforceGlobalAcyclicity();
    enforcePerObjectConstraints();
    return solver.solve(lits);
  }

  /**
   * Step 1: Encodes all known edges from the adjacency list into the global
   * graph.
   */
  private void encodeKnownEdges() {
    var adjList = dsg.getAdjList();
    for (var u : adjList.keySet()) {
      for (var v : adjList.get(u).keySet()) {
        for (var p : adjList.get(u).get(v)) {
          var edgeType = p.getLeft();
          var key = p.getRight();
          assert (edgeType != EdgeType.BC);
          // the existence of `edge` is enforced in encodeKnownEdge.
          encodeKnownEdge(new TypeEdge(u, v, edgeType, key));
        }
      }
    }
  }

  /**
   * Step 2: Encodes superpositions (unknown edges) as exactly-one-of constraints.
   */
  private void encodeSuperpositions() {
    for (var superposition : dsg.getSuperpositions()) {
      addLit(SolverUtilties.encodeSuperpos(superposition, this::encodeUnknownEdge, cfg.WEIGHT_GUIDED_SEARCH, solver), superposition);
    }
  }

  /** Step 3: Encodes WR ∧ WW → RW implications. */
  private void encodeImplications() {
    for (var imply : dsg.getImplies()) {
      addLit(SolverUtilties.encodeImply(imply, this::encodeUnknownEdge), imply);
    }
  }

  /**
   * Step 4: Constraint 1 (G1 in DSG) - No cycles of dependency edges globally.
   */
  private void enforceGlobalAcyclicity() {
    solver.assertTrue(globalRegularGraph.acyclic());
  }

  /**
   * Enforces Constraint 2 (G-cursor) for every key x in LDSG_x:
   * For each RW edge (u, v) on key x, there must be no write-dependency path
   * from v to u in LDSG_x (i.e., no cycle with exactly one RW and one or more WW
   * edges).
   */
  private void enforcePerObjectConstraints() {
    Map<Key, List<TypeEdge>> edgesByKey = groupEdgesByKey();

    if (edgesByKey.isEmpty()) {
      return;
    }

    for (var entry : edgesByKey.entrySet()) {
      Key targetKey = entry.getKey();
      List<TypeEdge> keyEdges = entry.getValue();

      log.info("Constructing LDSG for key: {}", targetKey);
      // Separate write-dependency edges (WW) and RW edges for this key
      List<TypeEdge> writeDepEdges = new ArrayList<>();
      List<Pair<TypeEdge, Lit>> rwEdges = new ArrayList<>();

      for (var edge : keyEdges) {
        Lit edgeLit = edgeLitMap.get(edge);
        if (edgeLit == null)
          continue;

        if (EdgeType.isWriteDependencyType(edge.edgeType)) {
          writeDepEdges.add(edge);
        } else if (EdgeType.isAntiDependencyType(edge.edgeType)) {
          rwEdges.add(Pair.of(edge, edgeLit));
        }
      }

      // Skip if no RW edges for this key
      if (rwEdges.isEmpty()) {
        log.info("No RW edges for key {}, skipping G-cursor constraint", targetKey);
        continue;
      }

      // Create LDSG_x graph for this key's write-dependency (WW) edges
      Graph ldsgWwGraph = new Graph(solver);
      Map<GraphNodeId, Integer> ldsgXNodeMap = new HashMap<>();

      // Collect nodes involved in this key's edges
      Set<GraphNodeId> keyNodes = new HashSet<>();
      for (var edge : writeDepEdges) {
        keyNodes.add(edge.getSourceId());
        keyNodes.add(edge.getTargetId());
      }
      for (var rwPair : rwEdges) {
        keyNodes.add(rwPair.getLeft().getSourceId());
        keyNodes.add(rwPair.getLeft().getTargetId());
      }

      // Add nodes to LDSG_x
      for (var nodeId : keyNodes) {
        int innerNid = ldsgWwGraph.addNode();
        ldsgXNodeMap.put(nodeId, innerNid);
      }

      // Add write-dependency edges to LDSG_x and bind them to global graph literals
      for (var edge : writeDepEdges) {
        int u = ldsgXNodeMap.get(edge.getSourceId());
        int v = ldsgXNodeMap.get(edge.getTargetId());
        Lit ldsgLit = safeAddEdge(ldsgWwGraph, u, v);

        // Bind to the same literal used in global graph
        Lit globalLit = edgeLitMap.get(edge);
        if (globalLit != null) {
          solver.assertEqual(ldsgLit, globalLit);
        }
      }

      // G-cursor constraint for LDSG_x:
      // For each RW edge (u, v), assert: Implies(rw_var, NOT(ldsgWwGraph.reaches(v,
      // u)))
      for (var rwPair : rwEdges) {
        TypeEdge rwEdge = rwPair.getLeft();
        Lit rwVar = rwPair.getRight();
        GraphNodeId u = rwEdge.getSourceId();
        GraphNodeId v = rwEdge.getTargetId();

        if (!ldsgXNodeMap.containsKey(u) || !ldsgXNodeMap.containsKey(v)) {
          continue;
        }

        int uInner = ldsgXNodeMap.get(u);
        int vInner = ldsgXNodeMap.get(v);

        // If RW edge (u, v) exists, then v must not reach u via WW edges in LDSG_x
        solver.assertTrue(implies(rwVar, not(ldsgWwGraph.reaches(vInner, uInner))));
      }
    }
  }

  /**
   * Groups all edges from the LDSG by their key labels.
   */
  private Map<Key, List<TypeEdge>> groupEdgesByKey() {
    Map<Key, List<TypeEdge>> edgesByKey = new HashMap<>();

    var adjList = dsg.getAdjList();
    for (var u : adjList.keySet()) {
      for (var v : adjList.get(u).keySet()) {
        for (var p : adjList.get(u).get(v)) {
          var edgeType = p.getLeft();
          var key = p.getRight();

          if (key == null)
            continue;

          var edge = new TypeEdge(u, v, edgeType, key);
          edgesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(edge);
        }
      }
    }

    // Also collect edges from superpositions and implications
    for (var superposition : dsg.getSuperpositions()) {
      for (var es : superposition.getEdgeSets()) {
        for (var edge : es) {
          if (edge.key != null) {
            edgesByKey.computeIfAbsent(edge.key, k -> new ArrayList<>()).add(edge);
          }
        }
      }
    }

    for (var imply : dsg.getImplies()) {
      if (imply.wrEdge.key != null) {
        edgesByKey.computeIfAbsent(imply.wrEdge.key, k -> new ArrayList<>()).add(imply.wrEdge);
      }
      if (imply.wwEdge.key != null) {
        edgesByKey.computeIfAbsent(imply.wwEdge.key, k -> new ArrayList<>()).add(imply.wwEdge);
      }
      if (imply.rwEdge.key != null) {
        edgesByKey.computeIfAbsent(imply.rwEdge.key, k -> new ArrayList<>()).add(imply.rwEdge);
      }
    }

    return edgesByKey;
  }

  /**
   * Encodes a known edge. Regular edges (WR, WW, CB) go into the global graph.
   * RW edges are stored as Boolean variables.
   */
  private Lit encodeKnownEdge(TypeEdge e) {
    assert EdgeType.isDependencyType(e.edgeType) || EdgeType.isAntiDependencyType(e.edgeType);

    Lit lit;
    // If it is a dependency edge, add it
    if (EdgeType.isDependencyType(e.edgeType)) {
      // Regular edge: check globalKnownEdgeLiterals by node pair (not by key)
      // Multiple edges with different keys but same endpoints share one literal in
      // global graph
      int u = globalNodeMap.get(e.getSourceId());
      int v = globalNodeMap.get(e.getTargetId());
      var nodePair = Pair.of(u, v);

      lit = globalKnownEdgeLiterals.get(nodePair);
      if (lit == null) {
        lit = safeAddEdge(globalRegularGraph, u, v);
        globalKnownEdgeLiterals.put(nodePair, lit);
        // only add lit when the lit is created.
        addLit(lit);
      }
    } else {
      // RW edge: check edgeLitMap (each RW edge is independent per key)
      lit = edgeLitMap.get(e);
      if (lit == null) {
        lit = new Lit(solver);
        // only add lit when the lit is created.
        addLit(lit);
      }
    }

    // update `edgeLitMap`
    // also store in edgeLitMap so we can look them up by TypeEdge
    if (!edgeLitMap.containsKey(e)) {
      registerEdgeLiteral(e, lit);
    }
    return lit;
  }

  /**
   * Encodes an unknown edge from superpositions/implications.
   */
  protected Lit encodeUnknownEdge(TypeEdge e) {
    assert EdgeType.isDependencyType(e.edgeType) || EdgeType.isAntiDependencyType(e.edgeType);

    Lit lit = edgeLitMap.get(e);
    if (lit != null) {
      return lit;
    }

    if (EdgeType.isDependencyType(e.edgeType)) {
      // Regular edge: add to global graph
      int u = globalNodeMap.get(e.getSourceId());
      int v = globalNodeMap.get(e.getTargetId());
      lit = safeAddEdge(globalRegularGraph, u, v);
    } else {
      // RW edge: Boolean variable only
      lit = new Lit(solver);
    }

    registerEdgeLiteral(e, lit);
    return lit;
  }
}
