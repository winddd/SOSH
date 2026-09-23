package optimizations.reachability.pruner;

import util.Config;
import util.enumtypes.MODE;

/**
 * Factory for creating graph pruner instances based on verification mode.
 *
 * <p>Graph pruning is a critical optimization that reduces the search space by eliminating
 * edges that cannot participate in isolation violations. This factory selects the appropriate
 * pruning strategy based on the graph representation being used.</p>
 *
 * <h2>Pruning Strategies</h2>
 * <ul>
 *   <li><b>Polymorphic SI Pruner</b> ({@link PolySIPruner}): Specialized pruner for
 *       polymorphic snapshot isolation graphs (MODE.P). Exploits the unique structure
 *       of polymorphic graphs to perform aggressive edge elimination.</li>
 *   <li><b>Boomslang Pruner</b> ({@link BoomslangPruner}): Default pruner for standard
 *       Boomslang constraint graphs. Implements reachability-based pruning that removes
 *       edges redundant under transitive closure.</li>
 * </ul>
 *
 * <h2>Optimization Impact</h2>
 * <p>Pruning can dramatically reduce verification time by:</p>
 * <ul>
 *   <li>Removing transitive edges that don't affect cycle detection</li>
 *   <li>Eliminating edges that cannot participate in minimal violating cycles</li>
 *   <li>Reducing the number of SMT constraints generated during encoding</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * InCompleteGraph graph = compiler.compile(asg);
 * Pruner pruner = PrunerFactory.getPruner(cfg);
 * PruneResult result = pruner.prune(graph);
 * System.out.println("Removed " + result.getRemovedEdges() + " edges");
 * }</pre>
 *
 * @see Pruner
 * @see PolySIPruner
 * @see BoomslangPruner
 * @see util.enumtypes.MODE
 * @since 1.0
 */
public class PrunerFactory {
  /**
   * Creates a pruner instance appropriate for the verification mode.
   *
   * <p>The factory dispatches to mode-specific pruners that understand the semantics
   * of their respective graph representations.</p>
   *
   * @param cfg the configuration object specifying the verification mode
   * @return a pruner instance optimized for the active mode
   * @see MODE#P
   */
  public static Pruner getPruner(Config cfg) {
    if (cfg.RUNMODE == MODE.P) {
      return new PolySIPruner(cfg);
    } else {
      return new BoomslangPruner(cfg);
    }
  }
}
