package encoders;

import solvers.Solver;

/**
 * Strategy interface for encoding dependency graphs as SMT solver instances.
 *
 * <p>Encoders transform high-level graph representations (nodes, edges, constraints) into
 * low-level solver-specific formats suitable for satisfiability checking. This abstraction
 * decouples graph construction from solver integration, enabling support for multiple solver
 * backends (MonoSAT, Z3, CVC4, etc.) via the Strategy pattern.
 *
 * <p><b>Encoding Pipeline:</b> The typical verification workflow is:
 * <ol>
 *   <li><b>Parsing:</b> Convert transaction logs to {@link history.KVHistory}</li>
 *   <li><b>ASG Construction:</b> Build {@link asg.ASG} from history</li>
 *   <li><b>Compilation:</b> Generate {@link graphs.graphs.InCompleteGraph} from ASG</li>
 *   <li><b>Encoding:</b> Transform graph to {@link solvers.Solver} instance (this interface)</li>
 *   <li><b>Solving:</b> Invoke solver to check satisfiability (cycle detection)</li>
 * </ol>
 *
 * <p><b>Encoder Responsibilities:</b>
 * <ul>
 *   <li>Translate graph nodes to solver variables</li>
 *   <li>Translate graph edges to solver constraints</li>
 *   <li>Encode superpositions as disjunctive constraints</li>
 *   <li>Encode implications as conditional constraints</li>
 *   <li>Add acyclicity constraint (for cycle detection)</li>
 *   <li>Return configured solver instance ready for satisfiability queries</li>
 * </ul>
 *
 * <p><b>Implementations:</b>
 * <ul>
 *   <li>{@link MonoSATEncoder} - Encodes graphs for MonoSAT native solver (primary implementation)</li>
 *   <li>{@link KnownGraphEncoder} - Specialized encoder for precomputed/known graphs</li>
 * </ul>
 *
 * <p><b>Factory Pattern:</b> Encoders are typically instantiated via {@link EncoderFactory},
 * which selects the appropriate encoder based on configuration (solver type, run mode, etc.).
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * InCompleteGraph graph = compiler.compile(asg);
 * Encoder encoder = new MonoSATEncoder(graph, "myTask", config);
 * Solver solver = encoder.encode(); // Returns configured solver instance
 * boolean sat = solver.solve();     // Check satisfiability (cycle exists?)
 * }</pre>
 *
 * @see solvers.Solver
 * @see MonoSATEncoder
 * @see EncoderFactory
 * @see graphs.graphs.InCompleteGraph
 */
public interface Encoder {
  /**
   * Encodes the graph as a solver instance ready for satisfiability checking.
   *
   * <p>This method performs the complete encoding transformation:
   * <ol>
   *   <li>Add all nodes to the solver</li>
   *   <li>Add all edges (known dependencies) to the solver</li>
   *   <li>Encode superposition constraints (disjunctive edge choices)</li>
   *   <li>Encode implication constraints (conditional dependencies)</li>
   *   <li>Encode generalized constraints (arbitrary boolean formulas)</li>
   *   <li>Add acyclicity constraint</li>
   * </ol>
   *
   * <p><b>Post-condition:</b> The returned solver is fully configured and ready for
   * {@link solvers.Solver#solve()} invocation.
   *
   * @return configured solver instance containing the encoded graph; never null
   */
  Solver encode();
}
