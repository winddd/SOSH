package solvers;

import static monosat.Logic.implies;
import static monosat.Logic.not;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.BoomslangGraph;
import graphs.nodes.GraphNodeId;
import lombok.extern.slf4j.Slf4j;
import monosat.BitVector;
import monosat.Comparison;
import monosat.Graph;
import monosat.Lit;
import util.Config;
import util.MapFactory;

/**
 * SMT solver for verifying PL-FCV (Forward Consistent View) isolation guarantees.
 *
 * <p>PL-FCV requires that transactions see a forward-consistent view, which means
 * if a transaction T2 starts after T1 commits, T2 must see T1's writes.
 *
 * <h2>Three-Graph Encoding Strategy</h2>
 * This solver employs a triple-graph approach using MonoSAT:
 * <ul>
 *   <li><b>graphWwWr</b>: Encodes direct dependency edges (WW, WR, PWR, CB).
 *       Must be acyclic to prevent G1 violations.</li>
 *   <li><b>rwEdgeVars</b>: Maps for anti-dependency edges (RW, PRW) as Boolean variables.</li>
 *   <li><b>graphSSG</b>: Start-ordered Serialization Graph containing all WwWr edges
 *       plus start dependency edges. Used for G-SIb detection.</li>
 * </ul>
 *
 * <h2>Timestamp Encoding</h2>
 * Each transaction has symbolic begin and commit timestamps represented as BitVectors.
 * Start dependencies are derived from timestamp comparisons:
 * <ul>
 *   <li>For each transaction i: begin_i < commit_i</li>
 *   <li>Start dependency edge (i → j) exists when: commit_i < begin_j</li>
 * </ul>
 *
 * <h2>Constraint Structure</h2>
 * <ol>
 *   <li>G1 prevention: graphWwWr must be acyclic</li>
 *   <li>G-SIb prevention: For each RW edge (u,v), no path from v to u in graphSSG</li>
 *   <li>Timestamp consistency: begin < commit for each transaction</li>
 *   <li>Start dependency encoding: commit_i < begin_j implies edge in SSG</li>
 * </ol>
 */
@Slf4j
public class PlFcvSolver extends SMTSolver {
  /** Width of bitvectors for timestamps (supports 2^BV_WIDTH distinct timestamps) */
  private static final int BV_WIDTH = 16;
  private final List<BoomslangGraph> graphs;
  private final BoomslangGraph boomslangG;
  /** Maps external transaction node IDs to internal MonoSAT node IDs in graphWwWr */
  private Map<GraphNodeId, Integer> graphWwWrNodeMap;

  /** Maps external transaction node IDs to internal MonoSAT node IDs in graphSSG */
  private Map<GraphNodeId, Integer> graphSSGNodeMap;

  /** Maps pairs of MonoSAT node IDs to SMT literals for known WW/WR/CB edges */
  private Map<Pair<Integer, Integer>, Lit> wwWrKnownEdgesToLiterals;

  /** Maps pairs of node IDs to Boolean variables for RW/PRW edges */
  private Map<Pair<GraphNodeId, GraphNodeId>, Lit> rwEdgeVars;

  /** MonoSAT graph for regular dependency edges (WW, WR, PWR, CB) */
  private Graph graphWwWr;

  /** MonoSAT graph for Start-ordered Serialization Graph (WwWr + start dependencies) */
  private Graph graphSSG;

  /** Maps transaction node IDs to their begin timestamp BitVectors */
  private Map<GraphNodeId, BitVector> beginTimestamps;

  /** Maps transaction node IDs to their commit timestamp BitVectors */
  private Map<GraphNodeId, BitVector> commitTimestamps;

  /**
   * Constructs a PL-FCV solver instance.
   *
   * @param tagPrefix identifier prefix for profiling/logging
   * @param cfg configuration object with solver settings
   * @param graphs list of BoomslangGraph instances representing the transaction history
   */
  public PlFcvSolver(String tagPrefix, Config cfg, List<BoomslangGraph> graphs) {
    super(tagPrefix, cfg);
    if (graphs == null || graphs.isEmpty()) {
      throw new IllegalArgumentException("PlFcvSolver requires at least one graph");
    }
    this.graphs = List.copyOf(graphs);
    this.boomslangG = this.graphs.get(0);
    this.graphWwWr = new Graph(solver);
    this.graphSSG = new Graph(solver);
    this.graphWwWrNodeMap = MapFactory.getEmptyMap(cfg.DEBUG);
    this.graphSSGNodeMap = MapFactory.getEmptyMap(cfg.DEBUG);
    this.wwWrKnownEdgesToLiterals = MapFactory.getEmptyMap(cfg.DEBUG);
    this.rwEdgeVars = MapFactory.getEmptyMap(cfg.DEBUG);
    this.beginTimestamps = new HashMap<>();
    this.commitTimestamps = new HashMap<>();
  }

  /**
   * Adds a transaction node to both graphs and creates timestamp variables.
   *
   * @param nodeId the transaction node to add
   */
  public void addNode(GraphNodeId nodeId) {
    assert !graphWwWrNodeMap.containsKey(nodeId);

    // Add to WwWr graph
    int wwWrInnerNid = graphWwWr.addNode();
    graphWwWrNodeMap.put(nodeId, wwWrInnerNid);

    // Add to SSG graph
    int ssgInnerNid = graphSSG.addNode();
    graphSSGNodeMap.put(nodeId, ssgInnerNid);

    // Create timestamp bitvectors for this transaction
    BitVector beginTs = new BitVector(solver, BV_WIDTH);
    BitVector commitTs = new BitVector(solver, BV_WIDTH);
    beginTimestamps.put(nodeId, beginTs);
    commitTimestamps.put(nodeId, commitTs);

    // Constraint: begin < commit for this transaction
    solver.assertTrue(beginTs.compare(Comparison.LT, commitTs));
  }

  /**
   * Encodes a known (definite) edge into the SMT formula.
   */
  private Lit encodeKnownEdge(TypeEdge e) {
    assert EdgeType.isDependencyType(e.edgeType) || EdgeType.isAntiDependencyType(e.edgeType);

    Lit lit;
    if (EdgeType.isDependencyType(e.edgeType)) {
      int u = graphWwWrNodeMap.get(e.getSourceId());
      int v = graphWwWrNodeMap.get(e.getTargetId());
      var nodePair = Pair.of(u, v);

      lit = wwWrKnownEdgesToLiterals.get(nodePair);
      if (lit == null) {
        // Add to graphWwWr
        lit = safeAddEdge(graphWwWr, u, v);
        wwWrKnownEdgesToLiterals.put(nodePair, lit);

        // Also add to graphSSG (SSG includes all WwWr edges)
        int uSSG = graphSSGNodeMap.get(e.getSourceId());
        int vSSG = graphSSGNodeMap.get(e.getTargetId());
        // bind the edge in DSG and SSG, make sure they share the same value
        solver.assertEqual(lit, safeAddEdge(graphSSG, uSSG, vSSG));
        // lit must be true
        addLit(lit);
      }
    } else {
      // Anti-dependency edge (RW, PRW): create Boolean variable
      var nodePair = Pair.of(e.getSourceId(), e.getTargetId());
      lit = rwEdgeVars.get(nodePair);
      if (lit == null) {
        lit = new Lit(solver);
        rwEdgeVars.put(nodePair, lit);
        addLit(lit);
      }
    }

    // We only map the edge to the lit in graphWwWrGraph
    registerEdgeLiteral(e, lit);
    return lit;
  }

  /**
   * Encodes an unknown (conditional) edge into the SMT formula.
   */
  protected Lit encodeUnknownEdge(TypeEdge e) {
    assert EdgeType.isDependencyType(e.edgeType) || EdgeType.isAntiDependencyType(e.edgeType);

    Lit eLit = edgeLitMap.get(e);
    if (eLit == null) {
      if (EdgeType.isDependencyType(e.edgeType)) {
        int u = graphWwWrNodeMap.get(e.getSourceId());
        int v = graphWwWrNodeMap.get(e.getTargetId());
        eLit = safeAddEdge(graphWwWr, u, v);

        // Also add to graphSSG
        int uSSG = graphSSGNodeMap.get(e.getSourceId());
        int vSSG = graphSSGNodeMap.get(e.getTargetId());
        // bind the edge in DSG and SSG, make sure they share the same value
        solver.assertEqual(eLit, safeAddEdge(graphSSG, uSSG, vSSG));
      } else {
        eLit = new Lit(solver);
      }

      registerEdgeLiteral(e, eLit);
    }
    return eLit;
  }

  /**
   * Constructs the complete PL-FCV SMT formula and invokes the solver.
   */
  public boolean solve() {
    log.info("Solving PL-FCV");
    var adjList = boomslangG.getAdjList();
    var nodeIds = new ArrayList<>(boomslangG.getNodeIds());

    // Step 1: Encode known edges
    for (var u : adjList.keySet()) {
      for (var v : adjList.get(u).keySet()) {
        if (u.equals(v)) continue;

        for (var p : adjList.get(u).get(v)) {
          var edgeType = p.getLeft();
          var key = p.getRight();
          if (edgeType == EdgeType.BC) continue;

          var edge = new TypeEdge(u, v, edgeType, key);
          encodeKnownEdge(edge);
        }
      }
    }

    // Step 2: Encode superpositions (unknown edges)
    for (var superposition : boomslangG.getSuperpositions()) {
      addLit(SolverUtilties.encodeSuperpos(superposition, this::encodeUnknownEdge, cfg.WEIGHT_GUIDED_SEARCH, solver), superposition);
    }

    // Step 3: Encode implications
    for (var imply : boomslangG.getImplies()) {
      addLit(SolverUtilties.encodeImply(imply, this::encodeUnknownEdge), imply);
    }

    // Step 4: Encode start dependency edges in SSG
    // For each pair of transactions (i, j), if commit_i < begin_j, add edge i -> j in SSG
    for (int i = 0; i < nodeIds.size(); i++) {
      GraphNodeId nodeI = nodeIds.get(i);
      BitVector commitI = commitTimestamps.get(nodeI);
      int iSSG = graphSSGNodeMap.get(nodeI);

      for (int j = 0; j < nodeIds.size(); j++) {
        if (i == j) continue;

        GraphNodeId nodeJ = nodeIds.get(j);
        BitVector beginJ = beginTimestamps.get(nodeJ);
        int jSSG = graphSSGNodeMap.get(nodeJ);

        // Create conditional start dependency edge:
        // If commit_i < begin_j, then edge i -> j exists in SSG
        Lit startDepLit = commitI.compare(Comparison.LT, beginJ);
        Lit startDepEdgeLit = graphSSG.addEdge(iSSG, jSSG);

        // The edge is enabled iff the start dependency condition holds
        solver.assertTrue(implies(startDepLit, startDepEdgeLit));
      }
    }

    // Step 5: Constraint 1 - No Pure Regular Cycles (G1 prevention)
    solver.assertTrue(graphWwWr.acyclic());

    // Step 6: Constraint 2 - No Cycles with Exactly One Anti-Edge in SSG (G-SIb prevention)
    // For each RW edge (u, v), assert: Implies(rw_var, Not(graphSSG.reaches(v, u)))
    for (var entry : rwEdgeVars.entrySet()) {
      var nodePair = entry.getKey();
      var rwVar = entry.getValue();
      GraphNodeId u = nodePair.getLeft();
      GraphNodeId v = nodePair.getRight();

      int uSSG = graphSSGNodeMap.get(u);
      int vSSG = graphSSGNodeMap.get(v);

      // If RW edge (u, v) exists, then there must NOT be a path from v to u in SSG
      solver.assertTrue(implies(rwVar, not(graphSSG.reaches(vSSG, uSSG))));
    }

    boolean sat = solver.solve(lits);
    return sat;
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
    return false;
  }

  @Override
  public boolean enforceAcyclic() {
    return false;
  }

  public void printConflictClauses() {
    // no-op
  }
}
