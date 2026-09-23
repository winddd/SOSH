package encoders;

import graphs.graphs.InCompleteGraph;
import util.Config;
import util.enumtypes.MODE;

import java.util.List;

/**
 * Factory for creating SMT encoder instances based on verification mode.
 *
 * <p>Encoders translate the constraint graphs produced by the compilation pipeline into
 * SMT formulas that can be solved by underlying SMT solvers. Different verification modes
 * may require specialized encoding strategies.</p>
 *
 * <h2>Encoding Strategies</h2>
 * <ul>
 *   <li><b>MonoSAT Encoding</b>: Default encoding for most isolation levels (SER, SI, RC, RU, PL-*).
 *       Leverages MonoSAT's built-in graph theory support for efficient cycle detection.</li>
 *   <li><b>Known Graph Encoding</b>: Specialized encoding for Adya SI2 mode (A_SI2) where
 *       the dependency structure is fully known upfront, enabling optimized constraint generation.</li>
 * </ul>
 *
 * <h2>Architecture Notes</h2>
 * <p>The factory abstracts the choice of encoder from client code, allowing the verification
 * pipeline to work uniformly across different encoding backends. All encoders implement the
 * {@link Encoder} interface, providing methods to convert graph constraints into SMT assertions.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * InCompleteGraph graph = compiler.compile(asg);
 * Encoder encoder = EncoderFactory.getEncoder(graph, "check", cfg);
 * encoder.encode();
 * boolean hasViolation = encoder.solve();
 * }</pre>
 *
 * @see Encoder
 * @see MonoSATEncoder
 * @see InCompleteGraph
 * @see util.enumtypes.MODE
 * @since 1.0
 */
public class EncoderFactory {
//  private static Config cfg = Config.get();

  /**
   * Creates an encoder for multiple constraint graphs.
   *
   * <p>Multi-graph encoding is used when verification requires checking consistency across
   * multiple independent constraint graphs (e.g., per-key graphs in some PL-* modes).</p>
   *
   * @param graphs the list of constraint graphs to encode
   * @param tagPrefix prefix for SMT variable naming to avoid collisions in incremental solving
   * @param cfg the configuration object specifying the verification mode and encoder options
   * @return an encoder instance appropriate for the specified mode
   * @see MODE#A_SI2
   */
  public static Encoder getEncoder(List<InCompleteGraph> graphs, String tagPrefix, Config cfg) {
    if (cfg.RUNMODE == MODE.A_SI2) {
      return new KnownGraphEncoder(graphs, tagPrefix, cfg);
    }
    return new MonoSATEncoder(graphs, tagPrefix, cfg);
  }

  /**
   * Creates an encoder for a single constraint graph.
   *
   * <p>This is the common case for most isolation level checks, where a single global
   * dependency graph captures all inter-transaction constraints.</p>
   *
   * @param graph the constraint graph to encode
   * @param tagPrefix prefix for SMT variable naming
   * @param cfg the configuration object specifying the verification mode
   * @return an encoder instance appropriate for the specified mode
   */
  public static Encoder getEncoder(InCompleteGraph graph, String tagPrefix, Config cfg) {
    return getEncoder(List.of(graph), tagPrefix, cfg);
  }
}
