package solvers;

import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import monosat.Lit;
import monosat.Solver;

import java.util.*;
import java.util.function.Function;

import static monosat.Logic.*;
import static monosat.Logic.not;

public class SolverUtilties {
  protected static Lit encodeSuperpos(Superposition superpos, Function<TypeEdge, Lit> unknownEdgeEncoder,
                                      boolean weight_guided_search, Solver solver) {
    List<Lit> esLits = new ArrayList<>();

    for (var es : superpos.getEdgeSets()) {
      Map<TypeEdge, Lit> edge2Lit = SolverUtilties.encodeEdgeSet(es, unknownEdgeEncoder);

      var esLit = Lit.True;
      for (var edge : edge2Lit.keySet()) {
        var lit = edge2Lit.get(edge);
        esLit = and(esLit, lit);

        if (weight_guided_search) {
          solver.setDecisionPriority(lit, edge.getPriority());
        }
      }

      esLits.add(esLit);
    }

    // at least one edge set is true
    var lit = or(esLits);
    // at most one edge set is true
    var n = superpos.getEdgeSets().size();
    for (int i = 0; i < n - 1; i++) {
      for (int j = i + 1; j < n; j++) {
        lit = and(lit, or(not(esLits.get(i)), not(esLits.get(j))));
      }
    }

    return lit;
  }
  /**
   * Encodes an edge set from a superposition,
   * Map each edge to a Lit, return a hashmap of TypeEdge => Lit.
   */
  protected static Map<TypeEdge, Lit> encodeEdgeSet(Set<TypeEdge> es, Function<TypeEdge, Lit> unknownEdgeEncoder) {
    var map = new HashMap<TypeEdge, Lit>();
    for (var edge : es) {
      map.put(edge, unknownEdgeEncoder.apply(edge));
    }
    return map;
  }

  protected static Lit encodeImply(Imply imply, Function<TypeEdge, Lit> unknownEdgeEncoder) {
    var wrLit = unknownEdgeEncoder.apply(imply.wrEdge);
    var wwLit = unknownEdgeEncoder.apply(imply.wwEdge);
    var rwLit = unknownEdgeEncoder.apply(imply.rwEdge);
    return implies(and(wrLit, wwLit), rwLit);
  }
}
