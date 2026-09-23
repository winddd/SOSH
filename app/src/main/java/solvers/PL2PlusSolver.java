package solvers;

import static monosat.Logic.and;
import static monosat.Logic.implies;
import static monosat.Logic.not;
import static monosat.Logic.or;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

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
 * SMT solver for verifying Predicate Lock level 2+ (PL-2+) isolation
 * guarantees.
 *
 * Two-Graph Encoding Strategy:
 * This solver employs a dual-graph approach using MonoSAT:
 * - graphWwWr: Encodes direct dependency edges (WW, WR, and transitive closure
 * CB edges).
 * Must be acyclic to prevent G1 violations.
 * - graphRw: Encodes anti-dependency edges (RW). Used in conjunction with
 * graphWwWr
 * to detect G-single violations.
 *
 * G-single Detection Algorithm:
 * For every pair of distinct transactions (u, v), the solver asserts:
 * ¬(graphWwWr.reaches(u, v) ∧ graphRw.reaches(v, u))
 * This forbids cycles where there is a dependency path from u to v and a
 * reverse anti-dependency
 * path from v to u, which would violate PL-2+ guarantees.
 *
 * Constraint Handling:
 * - Known edges: Edges definitively present in the transaction history are
 * encoded
 * immediately and mapped to literals via encodeKnownEdge(TypeEdge)
 * - Unknown edges (Superpositions): When version order is uncertain, the solver
 * encodes
 * exactly-one-of semantics: at least one edge set must be chosen, at most one
 * can be true
 * - Implication constraints: Logical implications between edges (e.g., WR ∧ WW
 * → RW)
 * are encoded as SMT implications
 *
 * Optimization Features:
 * - Weight-guided search: When cfg.WEIGHT_GUIDED_SEARCH is enabled, assigns
 * decision priorities to edge literals based on topological properties to guide
 * the solver
 * toward faster satisfiability checking
 *
 * Typical Usage:
 * List<BoomslangGraph> graphs = ...; // Compiled dependency graphs
 * Config cfg = ...; // Configuration with solver settings
 * PL2PlusSolver solver = new PL2PlusSolver("PL2+", cfg, graphs);
 *
 * // Add all transaction nodes
 * for (GraphNodeId nodeId : graphs.get(0).getNodeIds()) {
 * solver.addNode(nodeId);
 * }
 *
 * // Encode and solve (solve() internally adds edges/constraints)
 * boolean isValid = solver.solve();
 * if (!isValid) {
 * solver.printConflictClauses(); // Output violation details
 * }
 */
@Slf4j
public class PL2PlusSolver extends SMTSolver {
  /**
   * List of dependency graphs from compilation; the first graph is used as the
   * primary source
   */
  private final List<BoomslangGraph> graphs;

  /**
   * Maps external transaction node IDs to internal MonoSAT node IDs in the
   * regular dependency graph (WW/WR/CB edges)
   */
  protected Map<GraphNodeId, Integer> graphWwWrNodeMap;

  /**
   * Maps pairs of MonoSAT node IDs (u,v) to SMT literals for **known** regular
   * dependency edges (WW/WR/CB)
   */
  protected Map<Pair<Integer, Integer>, Lit> wwWrKnownEdgesToLiterals;

  /**
   * Only for **known** RW edges.
   * It is sufficient to use the node pair as the key here because we applied a technique of
   * combining all the known RW edges between the same node pair.
   * Maps pairs of node IDs (u,v) to Boolean variables for RW (anti-dependency)
   * edges; not added to any monosat graph
   */
  protected Map<Pair<GraphNodeId, GraphNodeId>, Lit> rwEdgeVars;

  /**
   * Primary Boomslang graph containing transaction history, edges, and
   * constraints
   */
  private BoomslangGraph boomslangG;

  /**
   * MonoSAT graph for regular dependency edges (WW, WR, CB); must be acyclic to
   * prevent cycles of pure regular edges
   */
  private Graph graphWwWr;

  /**
   * Constructs a PL-2+ solver instance.
   *
   * @param tagPrefix identifier prefix for profiling/logging (typically "PL2+")
   * @param cfg       configuration object with solver settings (debug mode,
   *                  weight-guided search, etc.)
   * @param graphs    list of BoomslangGraph instances representing the
   *                  transaction history; the first
   *                  graph is used as the primary source of edges and constraints
   * @throws IllegalArgumentException if graphs is null or empty
   */
  public PL2PlusSolver(String tagPrefix, Config cfg, List<BoomslangGraph> graphs) {
    super(tagPrefix, cfg);
    if (graphs == null || graphs.isEmpty()) {
      throw new IllegalArgumentException("PL2PlusSolver requires at least one graph");
    }
    this.graphs = List.copyOf(graphs);
    this.boomslangG = this.graphs.get(0);
    this.graphWwWr = new Graph(solver);
    this.graphWwWrNodeMap = MapFactory.getEmptyMap(cfg.DEBUG);
    this.wwWrKnownEdgesToLiterals = MapFactory.getEmptyMap(cfg.DEBUG);
    this.rwEdgeVars = MapFactory.getEmptyMap(cfg.DEBUG);
  }

  /**
   * Adds a transaction node to the regular dependency graph.
   *
   * Creates a corresponding node in graphWwWr (for WW/WR/CB dependencies),
   * maintaining
   * the mapping from the external GraphNodeId to the internal solver node
   * identifier.
   *
   * @param nodeId the transaction node to add
   * @throws AssertionError if the node already exists in the graph (when
   *                        assertions enabled)
   */
  public void addNode(GraphNodeId nodeId) {
    assert !graphWwWrNodeMap.containsKey(nodeId);
    int graphInnerNid = graphWwWr.addNode();
    graphWwWrNodeMap.put(nodeId, graphInnerNid);
  }

  /**
   * Encodes a known (definite) edge into the SMT formula.
   *
   * For regular dependency edges (WW, WR, CB), adds them to graphWwWr.
   * For anti-dependency edges (RW), creates Boolean variables (not added to any
   * graph).
   *
   * Known edges are deduplicated: if an edge between the same node pair already
   * exists, the
   * existing literal is reused and associated with the new edge instance.
   *
   * @param e the edge to encode (must be a dependency or anti-dependency type)
   * @return the SMT literal representing this edge's presence
   * @throws AssertionError if the edge type is not a dependency or
   *                        anti-dependency type
   */
  private Lit encodeKnownEdge(TypeEdge e) {
    assert EdgeType.isDependencyType(e.edgeType) || EdgeType.isAntiDependencyType(e.edgeType);

    Lit lit;
    if (EdgeType.isDependencyType(e.edgeType)) {
      // Regular dependency edge: add to graphWwWr
      int u = graphWwWrNodeMap.get(e.getSourceId());
      int v = graphWwWrNodeMap.get(e.getTargetId());
      var nodePair = Pair.of(u, v);

      // This check cannot be replaced by edgeLitMap, because we want to enable
      // lit sharing across all known edges between the same node pair.
      lit = wwWrKnownEdgesToLiterals.get(nodePair);
      if (lit == null) {
        lit = safeAddEdge(graphWwWr, u, v);
        wwWrKnownEdgesToLiterals.put(nodePair, lit);
        // enforce this lit to be true because it is known.
        addLit(lit);
      }
    } else {
      // Anti-dependency edge (RW): create Boolean variable, don't add to graph
      var nodePair = Pair.of(e.getSourceId(), e.getTargetId());
      lit = rwEdgeVars.get(nodePair);
      if (lit == null) {
        lit = new Lit(solver);
        rwEdgeVars.put(nodePair, lit);
        // enforce this lit to be true because it is known.
        addLit(lit);
      }
    }

    // NOTE: this shouldn't be moved into the if body above.
    // because we want to enable the sharing of the Lit of multiple edges between
    // the same node pair.
    // Update the bidirectional map between edges and Lits
    registerEdgeLiteral(e, lit);
    return lit;
  }

  /**
   * Encodes an unknown (conditional) edge into the SMT formula.
   *
   * Unknown edges arise from superpositions and implications where the edge's
   * presence depends
   * on the solver's choice of version order. Unlike encodeKnownEdge(TypeEdge),
   * these edges
   * are not immediately asserted as true; their literals participate in
   * superposition constraints
   * and implications.
   *
   * For regular dependency edges (WW, WR, CB), adds them to graphWwWr.
   * For anti-dependency edges (RW), creates Boolean variables (not added to any
   * graph).
   *
   * @param e the edge to encode (must be a dependency or anti-dependency type)
   * @return the SMT literal representing this edge (not asserted; used in
   *         constraints)
   * @throws AssertionError if the edge type is not a dependency or
   *                        anti-dependency type
   */
  protected Lit encodeUnknownEdge(TypeEdge e) {
    assert EdgeType.isDependencyType(e.edgeType) || EdgeType.isAntiDependencyType(e.edgeType);

    Lit eLit = edgeLitMap.get(e);
    if (eLit == null) {
      if (EdgeType.isDependencyType(e.edgeType)) {
        // Regular dependency edge: add to graphWwWr
        int u = graphWwWrNodeMap.get(e.getSourceId());
        int v = graphWwWrNodeMap.get(e.getTargetId());
        eLit = safeAddEdge(graphWwWr, u, v);
      } else {
        // Anti-dependency edge (RW): create Boolean variable, don't add to graph
        eLit = new Lit(solver);
      }

      registerEdgeLiteral(e, eLit);
    }
    return eLit;
  }

  /**
   * Constructs the complete PL-2+ SMT formula and invokes the solver.
   *
   * This method performs the following steps:
   * 1. Encode known edges: Regular edges (WW/WR/CB) are added to graphWwWr;
   * anti-dependency edges (RW) become Boolean variables
   * 2. Encode superpositions: For each superposition (uncertain version order),
   * encodes
   * exactly-one-of semantics: exactly one edge set from the superposition must be
   * chosen
   * 3. Encode implications: Encodes implication constraints (WR ∧ WW → RW)
   * derived
   * from the transaction history
   * 4. Constraint 1 (No Pure Regular Cycles): Enforces graphWwWr.acyclic() to
   * prevent
   * cycles consisting of only WW/WR/CB edges
   * 5. Constraint 2 (No Cycles with Exactly One Anti-Edge): For each RW edge (u,
   * v),
   * asserts Implies(rw_var, Not(graphWwWr.reaches(v, u))) to prevent a single
   * anti-edge from closing a loop formed by regular edges
   * 6. Invoke solver: Calls the underlying MonoSAT solver to determine
   * satisfiability
   *
   * @return true if the transaction history satisfies PL-2+ (no violations
   *         detected),
   *         false if there exists a PL-2+ violation
   */
  public boolean solve() {
    log.info("Solving");
    var adjList = boomslangG.getAdjList();

    // Step 1: Encode known edges
    for (var u : adjList.keySet()) {
      for (var v : adjList.get(u).keySet()) {
        assert (!u.equals(v));

        for (var p : adjList.get(u).get(v)) {
          var edgeType = p.getLeft();
          var key = p.getRight();
          assert (edgeType != EdgeType.BC);

          var edge = new TypeEdge(u, v, edgeType, key);
          encodeKnownEdge(edge);
        }
      }
    }

    // Step 2: Encode superpositions (unknown edges)
    for (var superposition : boomslangG.getSuperpositions()) {
      List<Lit> esLits = new ArrayList<>();

      for (var es : superposition.getEdgeSets()) {
        // encode all the edges in this edge set.
        Map<TypeEdge, Lit> edge2Lit = encodeEdgeSet(es);

        // esLit stores the AND of all the edges.
        var esLit = Lit.True;
        for (var edge : edge2Lit.keySet()) {
          var lit = edge2Lit.get(edge);
          esLit = and(esLit, lit);

          // topological prioritization
          if (cfg.WEIGHT_GUIDED_SEARCH) {
            solver.setDecisionPriority(lit, edge.getPriority());
          }
        }

        esLits.add(esLit);
      }

      // at least one edge set is true
      var lit = or(esLits);
      // at most one edge set is true
      var n = superposition.getEdgeSets().size();
      for (int i = 0; i < n - 1; i++) {
        for (int j = i + 1; j < n; j++) {
          lit = and(lit, or(not(esLits.get(i)), not(esLits.get(j))));
        }
      }

      // assert this superposition to be true.
      addLit(lit, superposition);
    }

    // Step 3: Encode implications
    for (var imply : boomslangG.getImplies()) {
      var wrLit = encodeUnknownEdge(imply.wrEdge);
      var wwLit = encodeUnknownEdge(imply.wwEdge);
      var rwLit = encodeUnknownEdge(imply.rwEdge);
      var lit = implies(and(wrLit, wwLit), rwLit);
      addLit(lit, imply);
    }

    // Step 4: Constraint 1 - No Pure Regular Cycles
    // Enforce that graphWwWr (containing only WW/WR/CB edges) is acyclic
    solver.assertTrue(graphWwWr.acyclic());

    // Step 5: Constraint 2 - No Cycles with Exactly One Anti-Edge
    // For each RW edge (u, v), assert: Implies(rw_var, Not(graphWwWr.reaches(v,
    // u)))
    // This prevents a single RW edge from closing a loop formed by regular edges
    for (var entry : rwEdgeVars.entrySet()) {
      var nodePair = entry.getKey();
      var rwVar = entry.getValue();
      GraphNodeId u = nodePair.getLeft();
      GraphNodeId v = nodePair.getRight();

      int uInner = graphWwWrNodeMap.get(u);
      int vInner = graphWwWrNodeMap.get(v);

      // If RW edge (u, v) exists, then there must NOT be a regular path from v to u
      solver.assertTrue(implies(rwVar, not(graphWwWr.reaches(vInner, uInner))));
    }

    boolean sat = solver.solve(lits);
    return sat;
  }

  /**
   * Outputs human-readable conflict information when the formula is
   * unsatisfiable.
   *
   * Currently a no-op. Subclasses or future implementations may extract the UNSAT
   * core
   * from the solver and use SMTSolver.getConflictingCons(List) to display the
   * conflicting edges/constraints.
   */
  public void printConflictClauses() {
  }

  /**
   * Not used in PL-2+ solving (edges are added internally via solve()).
   *
   * @param e the edge (ignored)
   * @return false
   */
  @Override
  public boolean addEdge(TypeEdge e) {
    return false;
  }

  /**
   * Not used in PL-2+ solving (constraints are encoded internally via solve()).
   *
   * @param con the constraint (ignored)
   * @return false
   */
  @Override
  public boolean addConstraint(GeneralizedConstraint con) {
    return false;
  }

  /**
   * Not used in PL-2+ solving (superpositions are encoded internally via
   * solve()).
   *
   * @param superposition the superposition (ignored)
   * @return false
   */
  @Override
  public boolean addSuperposition(Superposition superposition) {
    return false;
  }

  /**
   * Not used in PL-2+ solving (implications are encoded internally via solve()).
   *
   * @param imply the implication (ignored)
   * @return false
   */
  @Override
  public boolean addImply(Imply imply) {
    return false;
  }

  /**
   * Not used in PL-2+ solving (acyclicity is enforced internally via solve()).
   *
   * @return false
   */
  @Override
  public boolean enforceAcyclic() {
    return false;
  }

  /**
   * Encodes an edge set from a superposition, creating or retrieving SMT literals
   * for each edge.
   *
   * This helper method processes one disjunct in a superposition constraint. Each
   * edge in the
   * set is encoded as an unknown edge via encodeUnknownEdge(TypeEdge), and the
   * returned
   * map associates each edge with its corresponding SMT literal.
   *
   * @param edgeSet the set of edges representing one possible version order
   *                choice
   * @return map from each edge in the set to its SMT literal
   */
  private Map<TypeEdge, Lit> encodeEdgeSet(Set<TypeEdge> edgeSet) {
    var map = new HashMap<TypeEdge, Lit>();
    for (var edge : edgeSet) {
      map.put(edge, encodeUnknownEdge(edge));
    }
    return map;
  }
}
