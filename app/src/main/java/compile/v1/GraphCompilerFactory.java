package compile.v1;

import util.Config;
import util.enumtypes.MODE;

/**
 * Legacy factory for creating v1 graph compiler instances.
 *
 * <p>
 * <b>DEPRECATION NOTICE:</b> This is the legacy v1 compiler factory. New code
 * should use
 * {@link compile.v2.GraphCompilerFactory} instead. The v1 compilers are being
 * phased out
 * in favor of the modular v2 architecture.
 * </p>
 *
 * <p>
 * This factory creates mode-specific graph compilers that transform Abstract
 * Syntax Graphs
 * (ASG) into constraint graphs suitable for SMT solving. Each compiler
 * implements the logic
 * for a specific isolation level or verification mode.
 * </p>
 *
 * <h2>Supported Modes (Legacy)</h2>
 * <ul>
 * <li><b>P</b>: Polymorphic SI using {@link PolySiGraphCompiler}</li>
 * <li><b>V</b>: Viper SI via {@link ViperSiGraphCompiler}</li>
 * <li><b>C</b>: Cobra Serializable using {@link CobraSerGraphCompiler}</li>
 * <li><b>A_SER, A_SI, A_SI2</b>: Adya whitebox verification modes</li>
 * </ul>
 *
 * <h2>Migration Path</h2>
 * <p>
 * The v2 compiler architecture provides:
 * </p>
 * <ul>
 * <li>Modular dependency generation via pluggable
 * {@link compile.v2.modules.GenDepModule}s</li>
 * <li>Cleaner separation between graph construction and constraint
 * generation</li>
 * <li>Extensible support for new isolation levels without deep inheritance
 * hierarchies</li>
 * </ul>
 *
 * <p>
 * Code using this factory should migrate to v2 compilers when possible. The v1
 * compilers
 * remain active for modes not yet ported to the v2 architecture.
 * </p>
 *
 * @see compile.v2.GraphCompilerFactory
 * @see GraphCompiler
 * @see util.enumtypes.MODE
 * @since 1.0
 */
public class GraphCompilerFactory {
  /**
   * Creates a v1 compiler instance for the specified verification mode.
   *
   * @param compilerType the isolation level or verification mode to compile for
   * @param cfg          the configuration object containing compilation options
   * @return a v1 compiler instance appropriate for the specified mode
   * @throws IllegalArgumentException if the mode is not supported by v1 compilers
   */
  public static GraphCompiler getGraphCompiler(MODE compilerType, Config cfg) {
    return switch (compilerType) {
      case P -> new PolySiGraphCompiler(cfg);
      case V -> new ViperSiGraphCompiler(cfg);
      case C -> new CobraSerGraphCompiler(cfg);
      case A_SER -> new AdyaSerWhiteboxGraphCompiler(cfg);
      case A_SI -> new AdyaSIWhiteboxGraphCompiler(cfg);
      case A_SI2 -> new AdyaSIWhiteboxGraphCompiler2(cfg);
      default -> throw new IllegalArgumentException("Unsupported legacy compiler mode: " + compilerType);
    };
  }
}
