package compile.v2.compilers;

import asg.ASG;
import compile.v2.inputs.GraphInputs;
import graphs.graphs.InCompleteGraph;
import java.util.List;

/**
 * Interface for compiling Abstract Syntax Graphs into constraint graphs for isolation verification.
 *
 * <p>GraphCompilers are the core of Boomslang's verification pipeline. They transform the
 * high-level transaction history representation (ASG) into constraint graphs whose acyclicity
 * determines whether the history satisfies a specific isolation level.</p>
 *
 * <h2>Compilation Model</h2>
 * <p>The compilation process follows these steps:</p>
 * <ol>
 *   <li>Extract {@link GraphInputs} from the ASG (transaction metadata, dependencies, etc.)</li>
 *   <li>Create an appropriate {@link InCompleteGraph} structure (e.g., {@link graphs.graphs.BoomslangGraph})</li>
 *   <li>Apply isolation-specific {@link compile.v2.modules.GenDepModule}s to generate dependency edges</li>
 *   <li>Return the completed constraint graph(s) for SMT encoding and solving</li>
 * </ol>
 *
 * <h2>Multiple Graphs</h2>
 * <p>Most compilers return a single graph, but some isolation levels (particularly predicate lock
 * variants) may produce multiple independent graphs that must all be acyclic. For example, PL-FCV
 * may generate per-key dependency graphs.</p>
 *
 * <h2>Modular Architecture</h2>
 * <p>The v2 compiler design emphasizes modularity through dependency generation modules. Instead
 * of monolithic compiler classes, each isolation level is defined by composing modules:</p>
 * <ul>
 *   <li>{@link compile.v2.modules.GenReadDepModule} - Write-read dependencies</li>
 *   <li>{@link compile.v2.modules.GenWriteDepModule} - Write-write dependencies</li>
 *   <li>{@link compile.v2.modules.GenAntiDepModule} - Anti-dependencies (read-write)</li>
 *   <li>{@link compile.v2.modules.GenSessionDepModule} - Session order constraints</li>
 *   <li>And isolation-specific modules for temporal, predicate, or commit-order constraints</li>
 * </ul>
 *
 * <h2>Implementation Strategy</h2>
 * <p>Concrete compilers (e.g., {@link SerCompiler}, {@link RcCompiler}) implement this interface
 * by selecting the appropriate modules for their isolation level. The {@link compile.v2.modules.ModuleUtility}
 * class provides helpers for applying modules to graphs.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ASG asg = ASGConstructor.build(history, cfg);
 * GraphCompiler compiler = new SerCompiler(cfg);
 * List<InCompleteGraph> graphs = compiler.compile(asg);
 * // graphs[0] is the Direct Serialization Graph (DSG)
 * }</pre>
 *
 * @see ASG
 * @see GraphInputs
 * @see InCompleteGraph
 * @see compile.v2.modules.GenDepModule
 * @since 2.0
 */
public interface GraphCompiler {

  /**
   * Compiles an ASG into constraint graphs by first extracting GraphInputs.
   *
   * <p>This is a convenience method that wraps the ASG in {@link GraphInputs} before
   * delegating to {@link #compile(GraphInputs)}. Most clients should use this method.</p>
   *
   * @param asg the Abstract Syntax Graph representing the transaction history
   * @return a list of constraint graphs (typically a single graph)
   */
  default List<InCompleteGraph> compile(ASG asg) {
    return compile(GraphInputs.from(asg));
  }

  /**
   * Compiles graph inputs into constraint graphs for the target isolation level.
   *
   * <p>Implementations apply isolation-specific dependency generation logic to produce
   * graphs whose cycles correspond to isolation violations. The returned graphs are ready
   * for encoding and SMT solving.</p>
   *
   * @param inputs the extracted transaction metadata and dependencies
   * @return a list of constraint graphs (one or more depending on isolation level)
   */
  List<InCompleteGraph> compile(GraphInputs inputs);
}
