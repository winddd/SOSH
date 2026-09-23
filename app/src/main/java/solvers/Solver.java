package solvers;

public interface Solver {
  boolean solve();

  /**
   * Prints detailed information about the conflicting constraints when the solver
   * returns UNSAT. This method analyzes the unsatisfiable core and outputs
   * human-readable descriptions of edges, superpositions, and implications that
   * conflict with each other.
   */
  void printConflictClauses();
}
