package solvers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.nodes.GraphNodeId;
import monosat.Graph;
import monosat.Lit;
import util.Config;
import util.MapFactory;
import util.Profiler;

/**
 * Abstract base class for SMT-based transaction isolation level verification
 * solvers.
 *
 * This class provides the foundational infrastructure for encoding transaction
 * dependency graphs
 * as Satisfiability Modulo Theories (SMT) formulas and delegating solving to
 * backend SMT solvers
 * (primarily MonoSAT). Concrete subclasses implement specific isolation level
 * checkers by encoding
 * isolation-level-specific constraints.
 *
 * Core Responsibilities:
 * - Literal Management: Maintains bidirectional mappings between graph elements
 * (edges, constraints, superpositions) and SMT literals
 * - Constraint Encoding: Provides abstract methods for subclasses to encode
 * edges, binary/generalized constraints, superpositions, and implication
 * constraints
 * - Solver Integration: Wraps a monosat.Solver instance and manages
 * solver-level operations
 * - Conflict Analysis: Supports retrieval and interpretation of conflicting
 * literals for UNSAT result analysis
 *
 * Architecture - Subclasses must implement:
 * - addNode(GraphNodeId) - Add a transaction node to the solver's internal
 * graph(s)
 * - addEdge(TypeEdge) - Encode a dependency edge as SMT constraints
 * - addConstraint(GeneralizedConstraint) - Encode generalized constraint
 * objects
 * - addSuperposition(Superposition) - Encode disjunctive edge choices
 * (exactly-one-of semantics)
 * - addImply(Imply) - Encode implication constraints between edges
 * - enforceAcyclic() - Add acyclicity constraints (for
 * serializability/SI/RC/etc.)
 * - solve() - Construct the complete SMT formula and invoke the solver
 * - printConflictClauses() - Output human-readable conflict analysis for UNSAT
 * results
 *
 * Data Structures - The class maintains several key mappings:
 * - edgeLitMap: Maps TypeEdge instances to their corresponding Lit literals
 * - generalizedConstraintLitMap: Constraint-to-literal mappings
 * - superpositionLitMap, implyLitMap: Specialized constraint mappings
 * - litMap: Reverse mapping from literals to all associated graph objects (for
 * debugging/conflict analysis)
 *
 * Typical Usage Pattern:
 * SMTSolver solver = new PL2PlusSolver("PL2+", config, graphs);
 * for (GraphNodeId node : nodes) {
 * solver.addNode(node);
 * }
 * // Subclass.solve() adds edges/constraints and invokes solver.solve()
 * boolean isSatisfiable = solver.solve();
 * if (!isSatisfiable) {
 * solver.printConflictClauses();
 * }
 *
 * See also: PL2PlusSolver, MonoSatNativeSolver, PolySISolver
 */
public abstract class SMTSolver implements Solver {
  protected boolean debug;
  // protected Set<Lit> lits = MapFactory.getEmptySet();
  protected List<Lit> lits = new ArrayList<>();
  // map both known/unknown edges to Lits
  protected Map<TypeEdge, Lit> edgeLitMap = MapFactory.getEmptyMap(debug);
  // map general constraints to Lits
  protected Map<GeneralizedConstraint, Lit> generalizedConstraintLitMap = MapFactory.getEmptyMap(debug);
  // map superpositions to Lits
  protected Map<Superposition, Lit> superpositionLitMap = MapFactory.getEmptyMap(debug);
  // map implies to Lits
  protected Map<Imply, Lit> implyLitMap = MapFactory.getEmptyMap(debug);
  // reversely map a lit to a set of its associated meaning, e.g.,
  // edges/constraints/superpositions/implies...
  protected Map<Lit, ArrayList<Object>> litMap = new HashMap<>();
  /**
   * outer node id to inner node id.
   */
  protected monosat.Solver solver = new monosat.Solver();
  protected Config cfg;
  protected Profiler profiler;
  protected String tagPrefix;
  protected Set<Integer> targetLit = new HashSet<>(Arrays.asList());

  /**
   * Constructs a new SMT solver instance.
   *
   * @param tagPrefix identifier prefix for profiling/logging (e.g., "SER",
   *                  "PL2+")
   * @param cfg       configuration object containing solver parameters (debug
   *                  mode, weight-guided search, etc.)
   */
  public SMTSolver(String tagPrefix, Config cfg) {
    this.cfg = cfg;
    this.debug = this.cfg.DEBUG;
    this.profiler = Profiler.getInstance();
    this.tagPrefix = tagPrefix;
  }

  /**
   * Encodes a typed dependency edge into the SMT formula.
   *
   * @param e the edge to encode (e.g., WW, WR, RW dependency)
   * @return true if the edge triggers special handling, false otherwise
   */
  public boolean addEdge(TypeEdge e) { return false; }

  /**
   * Encodes a generalized constraint (n-ary constraint over multiple edges) into
   * the SMT formula.
   *
   * @param con the generalized constraint to encode
   * @return true if the constraint triggers special handling, false otherwise
   */
  public boolean addConstraint(GeneralizedConstraint con) { return false; }

  /**
   * Encodes a superposition constraint (exactly-one-of semantics for multiple
   * edge sets).
   *
   * A superposition represents a choice between mutually exclusive edge sets,
   * typically arising
   * from version order uncertainty in snapshot isolation checking.
   *
   * @param superposition the superposition constraint to encode
   * @return true if the superposition triggers special handling, false otherwise
   */
  public boolean addSuperposition(Superposition superposition) { return false; }

  /**
   * Encodes an implication constraint between edges (e.g., WR ∧ WW → RW).
   *
   * @param imply the implication constraint to encode
   * @return true if the implication triggers special handling, false otherwise
   */
  public boolean addImply(Imply imply) { return false; }

  /**
   * Adds acyclicity constraints to the SMT formula.
   *
   * Acyclicity enforcement prevents dependency cycles that would violate
   * isolation guarantees
   * (e.g., G0 cycles for Read Uncommitted, G1 cycles for Read Committed,
   * serializability cycles).
   *
   * @return true if acyclicity enforcement triggers special handling, false
   *         otherwise
   */
  public boolean enforceAcyclic() { return false; }

  /**
   * Constructs the complete SMT formula and invokes the solver.
   *
   * @return true if the formula is satisfiable (no isolation violations
   *         detected),
   *         false if unsatisfiable (isolation violation exists)
   */
  @Override
  public abstract boolean solve();

  /**
   * Registers a literal with the solver's literal tracking system.
   * Any lits added will be enforced to be true.
   *
   * @param lit the literal to register
   * @return false (reserved for future targeted literal tracking)
   */
  protected boolean addLit(Lit lit) {
    // if (lits.contains(lit))
    // return false;
    // else { // only consider the first time that we see this `lit`
    // lits.add(lit);
    // int internalId = extractLiteralInternalId(lit);
    // boolean targeted = targetLit.contains(internalId);
    // return targeted;
    // }
    lits.add(lit);
    // int internalId = extractLiteralInternalId(lit);
    // boolean targeted = targetLit.contains(internalId);
    // return targeted;
    return false;
  }

  /**
   * Associates a graph object (edge/constraint/etc.) with its SMT literal for
   * reverse lookup.
   *
   * This mapping enables conflict analysis by allowing the solver to translate
   * unsatisfiable
   * cores (sets of conflicting literals) back to high-level graph objects.
   *
   * Note: This method prevents duplicate associations - if the same object is
   * already
   * associated with the literal, it will not be added again. This is important
   * when
   * the same edge appears in multiple mini-transactions (e.g., with hybrid
   * snapshot epoch splitting).
   *
   * @param lit the SMT literal
   * @param obj the associated graph object (TypeEdge, Superposition, etc.)
   */
  protected void associateLiteralWithObject(Lit lit, Object obj) {
    if (!litMap.containsKey(lit)) {
      litMap.put(lit, new ArrayList<>());
    }

    // Prevent duplicates: only add if not already present
    ArrayList<Object> objs = litMap.get(lit);
    if (!objs.contains(obj)) {
      objs.add(obj);
    }
  }

  /**
   * Registers a bidirectional mapping between an edge and its literal.
   *
   * <p>This method updates both:
   * <ul>
   *   <li>{@code edgeLitMap}: edge → literal (forward lookup)</li>
   *   <li>{@code litMap}: literal → edges (reverse lookup via associateLiteralWithObject)</li>
   * </ul>
   *
   * @param edge the typed edge to register
   * @param lit the SMT literal representing this edge
   */
  protected void registerEdgeLiteral(TypeEdge edge, Lit lit) {
    edgeLitMap.put(edge, lit);
    associateLiteralWithObject(lit, edge);
  }

  protected boolean addLit(Lit lit, GeneralizedConstraint con) {
    // assert !lits.contains(lit);
    addLit(lit);
    generalizedConstraintLitMap.put(con, lit);
    associateLiteralWithObject(lit, con);
    return false;
  }

  protected boolean addLit(Lit lit, Superposition superposition) {
    // assert !lits.contains(lit);
    addLit(lit);
    superpositionLitMap.put(superposition, lit);
    associateLiteralWithObject(lit, superposition);
    return false;
  }

  protected boolean addLit(Lit lit, Imply imply) {
    // assert !lits.contains(lit);
    addLit(lit);
    implyLitMap.put(imply, lit);
    associateLiteralWithObject(lit, imply);
    return false;
  }

  /**
   * Retrieves all literals from the underlying solver using reflection.
   *
   * This method accesses the internal 'allLits' field of the MonoSAT solver
   * to obtain the complete list of literals.
   *
   * @return list of all literals from the solver
   * @throws RuntimeException if reflection fails
   */
  protected List<Lit> getAllLiteralsFromSolver() {
    List<Lit> allLits = null;
    Field nameField = null;
    try {
      nameField = solver.getClass().getDeclaredField("allLits");
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }

    nameField.setAccessible(true);
    try {
      allLits = (ArrayList<Lit>) nameField.get(solver);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return allLits;
  }

  /**
   * Converts a set of conflicting literals to a human-readable constraint
   * description.
   *
   * Used for UNSAT core analysis - translates SMT solver literals back to the
   * graph objects
   * (edges/constraints) that contributed to the unsatisfiable result.
   *
   * For edges that share the same source and target nodes, they are grouped
   * together and
   * formatted as: [source -> target: type1(key1), type2(key2), ...]
   *
   * For Superposition and Imply constraints, they are displayed as-is without
   * grouping.
   *
   * @param lits the conflicting literals from the UNSAT core
   * @return multi-line string describing the conflicting graph objects
   */
  protected String getConflictingCons(List<Lit> lits) {
    StringBuilder builder = new StringBuilder();

    for (var lit : lits) {
      var objs = litMap.get(lit);
      if (objs == null || objs.isEmpty()) {
        continue;
      }

      // Group TypeEdges by (source, target) pair
      // Key: "source->target", Value: List of TypeEdges with that pair
      Map<String, List<TypeEdge>> edgeGroups = new LinkedHashMap<>();
      List<Object> nonEdgeObjects = new ArrayList<>();

      for (var obj : objs) {
        if (obj instanceof TypeEdge) {
          TypeEdge edge = (TypeEdge) obj;
          String nodePair = edge.getSourceId().toString() + "->" + edge.getTargetId().toString();
          edgeGroups.computeIfAbsent(nodePair, k -> new ArrayList<>()).add(edge);
        } else {
          // Superposition, Imply, or other constraint types
          nonEdgeObjects.add(obj);
        }
      }

      // Format grouped edges
      boolean isFirst = true;
      for (Map.Entry<String, List<TypeEdge>> entry : edgeGroups.entrySet()) {
        if (!isFirst) {
          builder.append("; ");
        }
        isFirst = false;

        String nodePair = entry.getKey();
        List<TypeEdge> edges = entry.getValue();

        // Extract source and target from first edge
        TypeEdge firstEdge = edges.get(0);
        String source = formatNodeForGroup(firstEdge.getSourceId());
        String target = formatNodeForGroup(firstEdge.getTargetId());

        if (edges.size() == 1) {
          // Single edge: use standard format [source -type(key)-> target]
          builder.append(firstEdge.toString());
        } else {
          // Multiple edges: group as [source -> target: type1(key1), type2(key2), ...]
          builder.append("[").append(source).append(" -> ").append(target).append(": ");
          for (int i = 0; i < edges.size(); i++) {
            if (i > 0) {
              builder.append(", ");
            }
            TypeEdge edge = edges.get(i);
            builder.append(edge.edgeType.name());
            if (edge.key != null) {
              builder.append("(").append(edge.key).append(")");
            }
          }
          builder.append("]");
        }
      }

      // Add non-edge objects (Superposition, Imply, etc.) as-is
      for (var obj : nonEdgeObjects) {
        if (!isFirst) {
          builder.append("; ");
        }
        isFirst = false;
        builder.append(obj.toString());
      }

      builder.append("\n");
    }

    return builder.toString();
  }

  /**
   * Formats a GraphNodeId for grouped edge display.
   *
   * @param nodeId the node to format
   * @return a human-readable string representation
   */
  private String formatNodeForGroup(GraphNodeId nodeId) {
    if (nodeId.kind() == graphs.nodes.NodeKind.TXN) {
      return Integer.toString(nodeId.txnId());
    }
    String type = nodeId.actionType().map(Enum::name).orElse("?");
    return nodeId.txnId() + ":" + nodeId.opIndex() + "(" + type + ")";
  }

  /**
   * Outputs a human-readable representation of the conflicting
   * clauses/constraints when the
   * formula is unsatisfiable.
   *
   * Implementations typically extract the UNSAT core from the solver and use
   * getConflictingCons(List) to translate it to graph-level objects.
   */
  public void printConflictClauses(){}

  /**
   * Applies this function to the given argument.
   *
   * @return the function result
   */
  // public Boolean apply(String tag) {
  // profiler.startTick(tag);
  // boolean ret = this.solve();
  // profiler.endTick(tag);
  // return ret;
  // }

  /**
   * Extracts the internal integer ID from a literal using reflection.
   *
   * Accesses the private 'l' field of the Lit object to retrieve its internal
   * identifier.
   *
   * @param lit the literal to extract the ID from
   * @return the internal integer ID of the literal
   * @throws RuntimeException if reflection fails
   */
  private Integer extractLiteralInternalId(Lit lit) {
    Field nameField;
    try {
      nameField = lit.getClass().getDeclaredField("l");
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }

    int l = -1;
    nameField.setAccessible(true);
    try {
      l = nameField.getInt(lit);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return l;
  }

  /**
   * Adds a transaction node to the solver's internal graph representation(s).
   *
   * Implementations typically create corresponding nodes in one or more MonoSAT
   * Graph instances
   * and maintain mappings from GraphNodeId to internal solver node identifiers.
   *
   * @param nodeId the transaction node identifier to add
   */
  public abstract void addNode(graphs.nodes.GraphNodeId nodeId);

  /**
   * Safely adds an edge to a MonoSAT graph, handling self-loops correctly.
   *
   * MonoSAT's graph solver does not support self-loops (edges from a node to
   * itself).
   * This helper method checks if the source and target nodes are the same:
   * - If u != v: Creates a graph edge using graph.addEdge(u, v)
   * - If u == v: Creates a standalone literal that can participate in SAT
   * constraints
   * but is not part of the graph structure
   *
   * @param graph the MonoSAT graph to add the edge to
   * @param u     the source node ID (internal MonoSAT node identifier)
   * @param v     the target node ID (internal MonoSAT node identifier)
   * @return an SMT literal representing the edge
   */
  protected Lit safeAddEdge(Graph graph, int u, int v) {
    if (u != v) {
      return graph.addEdge(u, v);
    } else {
      return new Lit(solver);
    }
  }
}
