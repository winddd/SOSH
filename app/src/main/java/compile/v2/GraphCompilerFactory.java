package compile.v2;

import compile.v2.compilers.*;
import util.Config;
import util.enumtypes.MODE;

import compile.v1.AdyaSIWhiteboxGraphCompiler;
import compile.v1.AdyaSIWhiteboxGraphCompiler2;
import compile.v1.AdyaSerWhiteboxGraphCompiler;
import compile.v1.CobraSerGraphCompiler;
import compile.v1.PolySiGraphCompiler;
import compile.v1.ViperSiGraphCompiler;

/**
 * Factory for creating isolation-level-specific graph compilers.
 *
 * <p>GraphCompilerFactory implements the Factory pattern to instantiate the appropriate
 * compiler implementation based on the verification mode ({@link MODE}). It supports:
 * <ul>
 *   <li><b>Boomslang modes (B_*):</b> Modern v2 compilers (SER, SI, RC, RU, PL-*)</li>
 *   <li><b>Legacy modes (A_*, C, V, P):</b> Wrapped v1 compilers for compatibility</li>
 * </ul>
 *
 * <p><b>Supported Modes:</b>
 * <ul>
 *   <li>{@code B_SER}: Serializable (strictest)</li>
 *   <li>{@code B_SI}: Snapshot Isolation</li>
 *   <li>{@code B_RC}: Read Committed</li>
 *   <li>{@code B_RU}: Read Uncommitted</li>
 *   <li>{@code B_PL2P}, {@code B_PL299}, {@code B_PLFCV}, {@code B_PLCS}: Predicate Lock variants</li>
 *   <li>{@code B_SSER}: Strict Serializable</li>
 *   <li>{@code C, V, P, A_*}: Legacy modes (Cobra, Viper, PolySI, Adya)</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * GraphCompiler compiler = GraphCompilerFactory.getGraphCompiler(cfg.RUNMODE, cfg);
 * List<InCompleteGraph> graphs = compiler.compile(asg);
 * }</pre>
 *
 * @see GraphCompiler for the compiler interface
 * @see util.enumtypes.MODE for all supported modes
 */
public class GraphCompilerFactory {
  /**
   * Creates a graph compiler for the specified isolation level.
   *
   * @param compilerType the verification mode (isolation level)
   * @param cfg configuration object
   * @return a compiler instance for the specified mode
   * @throws IllegalArgumentException if the mode is not supported
   */
  public static GraphCompiler getGraphCompiler(MODE compilerType, Config cfg) {
    return switch (compilerType) {
      case B_SI -> new PlSiCompiler(cfg);
      case B_RC -> new RcCompiler(cfg);
      case B_RU -> new RuCompiler(cfg);
      case B_PL299 -> new Pl299Compiler(cfg);
      case P -> new LegacyGraphCompilerAdapter(new PolySiGraphCompiler(cfg));
      case C -> new LegacyGraphCompilerAdapter(new CobraSerGraphCompiler(cfg));
      case V -> new LegacyGraphCompilerAdapter(new ViperSiGraphCompiler(cfg));
      case A_SER -> new LegacyGraphCompilerAdapter(new AdyaSerWhiteboxGraphCompiler(cfg));
      case A_SI -> new LegacyGraphCompilerAdapter(new AdyaSIWhiteboxGraphCompiler(cfg));
      case A_SI2 -> new LegacyGraphCompilerAdapter(new AdyaSIWhiteboxGraphCompiler2(cfg));
      case B_SER, B_PL2P, B_PLCS, B_PLFCV -> new SerCompiler(cfg);
      case B_SSER -> new StrictSerCompiler(cfg);
      default -> throw new IllegalArgumentException("Unsupported mode: " + compilerType);
    };
  }
}
