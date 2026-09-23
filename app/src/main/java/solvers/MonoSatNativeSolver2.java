package solvers;

import graphs.constraints.GeneralizedConstraint;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import lombok.extern.slf4j.Slf4j;
import monosat.Graph;
import monosat.Lit;
import org.apache.commons.lang3.NotImplementedException;
import util.Config;
import util.MapFactory;
import util.exception.DuplicatedNodesException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static monosat.Logic.*;

/**
 * Manager 2.
 * This solver that doesn't use hashmap to reuse lits.
 * This is correct for some encodings because any edge can never appear in two different
 *  constraints/generalied constraints/superpositions.
 */
@Slf4j
public class MonoSatNativeSolver2 extends SMTSolver {
  protected Map<graphs.nodes.GraphNodeId, Integer> nodeMap;
  private Graph monoGraph;

  public MonoSatNativeSolver2(String tagPrefix, Config cfg) {
    super(tagPrefix, cfg);
    this.monoGraph = new Graph(solver);
    this.nodeMap = MapFactory.getEmptyMap(cfg.DEBUG);
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
    var u = nodeMap.get(e.getSourceId());
    var v = nodeMap.get(e.getTargetId());
    Lit lit = safeAddEdge(monoGraph, u, v);
    solver.assertTrue(lit);
    return false;
  }

  private List<Lit> encodeES2(List<TypeEdge> es) {
    assert !es.isEmpty();
    var lits = new ArrayList<Lit>();
    for (var edge : es) {
      var u = nodeMap.get(edge.getSourceId());
      var v = nodeMap.get(edge.getTargetId());
      Lit lit = safeAddEdge(monoGraph, u, v);
      lits.add(lit);
    }
    return lits;
  }

  @Override
  public boolean addConstraint(GeneralizedConstraint con) {
    assert false;
    var es1 = new ArrayList<>(con.getEdgeSet1());
    var es2 = new ArrayList<>(con.getEdgeSet2());
    var es1Lits = this.encodeES2(es1);
    var es2Lits = this.encodeES2(es2);
    var es1Lit = and(es1Lits);
//    solver.setDecisionLiteral(es1Lit, false);
    var es2Lit = and(es2Lits);
//    solver.setDecisionLiteral(es2Lit, false);
    Lit xorLit = xor(es1Lit, es2Lit);
//    solver.setDecisionLiteral(xorLit, false);

    if (cfg.WEIGHT_GUIDED_SEARCH) {
      for (int i = 0; i < es1.size(); i++) {
        var edge = es1.get(i);
        var lit = es1Lits.get(i);
        var priority = edge.getPriority();
//        if (priority != 0) {
//          solver.setDecisionPriority(lit, edge.getPriority());
//        }
        solver.setDecisionPriority(lit, priority);
      }

      for (int i = 0; i < es2.size(); i++) {
        var edge = es2.get(i);
        var lit = es2Lits.get(i);
        var priority = edge.getPriority();
//        if (priority != 0) {
//          solver.setDecisionPriority(lit, edge.getPriority());
//        }
        solver.setDecisionPriority(lit, priority);
      }
    }
    return false;
  }

  @Override
  public boolean addSuperposition(Superposition superposition) {
    List<Lit> esLits = new ArrayList<>();
    for (var es : superposition.getEdgeSets()) {
      var edgeList = new ArrayList<>(es);
      var lits = encodeES2(edgeList);
      var esLit = Lit.True;
      for (int i = 0; i < edgeList.size(); i++) {
        var edge = edgeList.get(i);
        var lit = lits.get(i);
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
    assertTrue(lit);
    return false;
  }

  @Override
  public boolean addImply(Imply imply) {
    throw new NotImplementedException("");
  }

  @Override
  public boolean enforceAcyclic() {
    solver.assertTrue(monoGraph.acyclic());
    return false;
  }

  @Override
  public boolean solve() {
    log.info("Starting solving");
    profiler.startTick(tagPrefix + "solving");
//    var ret = solver.solve(lits);
    var ret = solver.solve();
    profiler.endTick(tagPrefix + "solving");
    return ret;
  }

  @Override
  public void printConflictClauses() {

  }
}
