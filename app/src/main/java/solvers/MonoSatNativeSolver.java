package solvers;

import static monosat.Logic.and;
import static monosat.Logic.implies;
import static monosat.Logic.not;
import static monosat.Logic.or;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import lombok.extern.slf4j.Slf4j;
import monosat.Graph;
import monosat.Lit;
import util.Config;
import util.MapFactory;
import util.exception.DuplicatedNodesException;
import util.exception.NonexistingNodeException;

/**
 * Manager 1.
 * A solver that
 * - allowes multiple edges between the same nodes
 * - is based on MonoSAT native graph solver.
 *
 * Use hashmap to store the mapping from edges/constraints/superpositions to
 * Lits.
 * Hence may suffer from costly encoding.
 */
@Slf4j
public class MonoSatNativeSolver extends SMTSolver {
  protected Graph monoGraph;
  protected Map<graphs.nodes.GraphNodeId, Integer> nodeMap;
  // map known TypeEdges to Lits
  protected Map<Pair<Integer, Integer>, Lit> knownEdges2Lit;
  // private List<Lit> allLitsInSolver;
  // private Map<Lit, Object> lit2Cons = MapFactory.getEmptyMap();

  public MonoSatNativeSolver(String tagPrefix, Config cfg) {
    super(tagPrefix, cfg);
    this.nodeMap = MapFactory.getEmptyMap(cfg.DEBUG);
    this.monoGraph = new Graph(solver);
    this.knownEdges2Lit = MapFactory.getEmptyMap(debug);
    // Field nameField = null;
    // try {
    // nameField = solver.getClass().getDeclaredField("allLits");
    // } catch (NoSuchFieldException e) {
    // throw new RuntimeException(e);
    // }

    // nameField.setAccessible(true);
    // try {
    // allLitsInSolver = (ArrayList<Lit>) nameField.get(solver);
    // } catch (IllegalAccessException e) {
    // throw new RuntimeException(e);
    // }
  }

  /**
   * Create a Lit/retrieve the existing Lit for a given known edge.
   * 
   * @param e
   * @return
   */
  protected Lit encodeKnownEdge(TypeEdge e) {
    // get internal monosat node id
    int u = nodeMap.get(e.getSourceId());
    int v = nodeMap.get(e.getTargetId());
    var nodePair = Pair.of(u, v);
    // if there is no known edges between u and v, create a new Lit
    Lit lit;
    if ((lit = knownEdges2Lit.getOrDefault(nodePair, null)) == null) {
      lit = safeAddEdge(monoGraph, u, v);

      knownEdges2Lit.put(nodePair, lit);
      // solver.assertTrue(lit);
      addLit(lit);
    }

    // NOTE: this shouldn't be moved into the if body above.
    // because we want to enable the sharing of the Lit of multiple edges between
    // the same node pair.
    registerEdgeLiteral(e, lit);
    // if there is already a Lit for some known edge between u and v,
    // just reuse it without creating new Lits.
    // TODO: how to handle litMap? one versus many
    return lit;
  }

  /**
   * Create a Lit/retrieve the existing Lit for a given unknown edge.
   * u, v should outer node ids.
   *
   * @return
   */
  protected Lit encodeUnknownEdge(TypeEdge e) {
    int u = nodeMap.get(e.getSourceId());
    int v = nodeMap.get(e.getTargetId());

    Lit eLit = edgeLitMap.getOrDefault(e, null);
    if (eLit == null) {
      try {
        if (u != v) {
          eLit = monoGraph.addEdge(u, v);
        } else {
          eLit = new Lit(solver);
        }
        // lit2Cons.put(e, new BoolConstraint(EdgeType.INVALID, outSrc, outDst));
      } catch (RuntimeException ex) {
        ex.printStackTrace();
      }

      registerEdgeLiteral(e, eLit);
    }
    return eLit;
  }

  public void addNode(graphs.nodes.GraphNodeId nodeId) {
    if (!nodeMap.containsKey(nodeId)) {
      int innerNid = monoGraph.addNode();
      nodeMap.put(nodeId, innerNid);
    } else {
      throw new DuplicatedNodesException();
    }
  }

  @Override
  public boolean addEdge(TypeEdge e) {
    if (!nodeMap.containsKey(e.getSourceId()) || !nodeMap.containsKey(e.getTargetId())) {
      throw new NonexistingNodeException();
    }

    this.encodeKnownEdge(e);
    return false;
  }

  @Override
  public boolean addSuperposition(Superposition superposition) {
    // TODO: note priorities hasn't been used in this function
    List<Lit> esLits = new ArrayList<>();
    for (var es : superposition.getEdgeSets()) {
      Map<TypeEdge, Lit> edge2Lit = encodeES2(es);
      var esLit = Lit.True;

      for (var edge : edge2Lit.keySet()) {
        var lit = edge2Lit.get(edge);
        esLit = and(esLit, lit);

        if (cfg.WEIGHT_GUIDED_SEARCH) {
          solver.setDecisionPriority(lit, edge.getPriority());
        }
      }

      esLits.add(esLit);
    }
    // at least one is true
    var lit = or(esLits);
    // at most one is true
    var n = superposition.getEdgeSets().size();
    for (int i = 0; i < n - 1; i++) {
      for (int j = i + 1; j < n; j++) {
        lit = and(lit, or(not(esLits.get(i)), not(esLits.get(j))));
      }
    }

    addLit(lit, superposition);
    return false;
  }

  @Override
  public boolean addImply(Imply imply) {
    var wrLit = encodeUnknownEdge(imply.wrEdge);
    var wwLit = encodeUnknownEdge(imply.wwEdge);
    var rwLit = encodeUnknownEdge(imply.rwEdge);
    var lit = implies(and(wrLit, wwLit), rwLit);
    addLit(lit, imply);
    return false;
  }

  @Override
  public boolean enforceAcyclic() {
    Lit lit = monoGraph.acyclic();
    addLit(lit);
    associateLiteralWithObject(lit, "acyclic");
    return false;
  }

  private String captureConflictingClausesFromConsole() {
    // Create a ByteArrayOutputStream to capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out; // Save original System.out
    PrintStream printStream = new PrintStream(outputStream);
    System.setOut(printStream);

    // Code whose output we want to capture (e.g., MonoSAT's printConflictClauses)

    // If using MonoSAT: solver.printConflictClauses();

    // Restore original System.out
    System.setOut(originalOut);

    // Convert captured output to a String
    String capturedOutput = outputStream.toString();

    // Close the custom PrintStream
    printStream.close();
    return capturedOutput;
  }

  @Override
  public boolean solve() {
    log.info(tagPrefix + "Start solving");
    log.info("{} In solving phase: #edges: {}, #known edges: {}", tagPrefix, edgeLitMap.size(), knownEdges2Lit.size());
    profiler.startTick(tagPrefix + "solving");
    boolean sat = this.solver.solve(lits);

    if (debug) {
      System.out.println("debugging...");

      if (!sat) {
        // String conflictingClauses = captureConflictingClausesFromConsole();
        // System.out.println(conflictingClauses);
        List<Lit> clauses = solver.minimizeUnsatCore(lits);
        if (cfg.DEBUG) {
          var debugInfo = getConflictingCons(clauses);
          System.out.println(debugInfo);
        }
      } else { // do nothing.
        // for (var edge : edgeLitMap.keySet()) {
        // System.out.printf("%s: %s\n", edge, edgeLitMap.get(edge).value());
        // }
        //
        // for (var con : binaryConstraintLitMap.keySet()) {
        // System.out.printf("%s: %s\n", con, binaryConstraintLitMap.get(con).value());
        // }
        //
        // for (var con : generalizedConstraintLitMap.keySet()) {
        // System.out.printf("%s: %s\n", con,
        // generalizedConstraintLitMap.get(con).value());
        // }
        //
        // for (var superposition : superpositionLitMap.keySet()) {
        // System.out.printf("%s: %s\n", superposition,
        // superpositionLitMap.get(superposition).value());
        // }
        //
        // for (var imply : implyLitMap.keySet()) {
        // System.out.printf("%s: %s\n", imply, implyLitMap.get(imply).value());
        // }
      }
    }

    profiler.endTick(tagPrefix + "solving");
    return sat;
  }

  @Override
  public void printConflictClauses() {
    List<Lit> clauses = solver.minimizeUnsatCore(lits);
    System.out.println("=".repeat(80));
    System.out.println("UNSAT CORE ANALYSIS");
    System.out.println("=".repeat(80));
    System.out.printf("Found %d conflicting literals in the minimized UNSAT core\n\n", clauses.size());

    String conflictDetails = getConflictingCons(clauses);
    System.out.println("Conflicting constraints:");
    System.out.println("-".repeat(80));
    System.out.println(conflictDetails);
    System.out.println("=".repeat(80));
  }

  private Map<TypeEdge, Lit> encodeES2(Set<TypeEdge> es) {
    var map = new HashMap<TypeEdge, Lit>();
    for (var edge : es) {
      map.put(edge, encodeUnknownEdge(edge));
    }
    return map;
  }

  @Override
  public boolean addConstraint(GeneralizedConstraint con) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'GeneralizedConstraint'");
  }
}
