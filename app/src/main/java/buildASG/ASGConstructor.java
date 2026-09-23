package buildASG;

import java.util.ArrayList;
import java.util.List;

import asg.ASG;
import buildASG.builder.CommitOrderBuilder;
import buildASG.builder.ExpectedOrderBuilder;
import buildASG.builder.GraphBuilder;
import buildASG.builder.HintsBuilder;
import buildASG.builder.IteratorBuilder;
import buildASG.builder.RangeQueryBuilder;
import buildASG.builder.RealtimeOrderBuilder;
import buildASG.builder.SessionOrderBuilder;
import buildASG.builder.WriteReadDependencyBuilder;
import history.KVHistory;
import lombok.extern.slf4j.Slf4j;
import util.Config;
import util.Profiler;
import util.exception.RejectException;

/**
 * REFACTORED ASGConstructor using Strategy Pattern for maintainability.
 *
 * Key improvements:
 * 1. Single Responsibility: Each builder handles one concern
 * 2. Easy Testing: Each component can be unit tested independently
 * 3. Clear Orchestration: Main method coordinates but doesn't implement
 * 4. Type Safety: Structured options instead of boolean parameters
 * 5. Better Errors: Each builder provides specific failure context
 */
@Slf4j
public class ASGConstructor {
  protected final Config config;
  protected final List<GraphBuilder> builders;

  public ASGConstructor(Config config) {
    this.config = config;
    this.builders = createBuilders(config);
  }

  // Factory method for creating builders - easy to customize and test
  private List<GraphBuilder> createBuilders(Config config) {
    List<GraphBuilder> builders = new ArrayList<>();

    builders.add(new HintsBuilder());
    builders.add(new CommitOrderBuilder(config));
    builders.add(new WriteReadDependencyBuilder());
    builders.add(new RangeQueryBuilder(config));
    builders.add(new IteratorBuilder(config));
    builders.add(new SessionOrderBuilder());
    builders.add(new RealtimeOrderBuilder(config));
    builders.add(new ExpectedOrderBuilder());

    return builders;
  }

  // Main orchestration method - simple and testable
  public ASG buildAsg(KVHistory history, boolean sessionOrder, boolean realtimeOrder,
      boolean enableExpectedExecutionOrder) throws RejectException {
    return buildAsg(history, createOptions(sessionOrder, realtimeOrder, enableExpectedExecutionOrder));
  }

  /**
   * Creates GraphBuildOptions from boolean flags.
   *
   * @param sessionOrder                 enable session order constraints
   * @param realtimeOrder                enable real-time order constraints
   * @param enableExpectedExecutionOrder enable expected execution order
   *                                     constraints
   * @return configured GraphBuildOptions
   */
  private GraphBuildOptions createOptions(boolean sessionOrder, boolean realtimeOrder,
      boolean enableExpectedExecutionOrder) {
    GraphBuildOptions.Builder optionsBuilder = new GraphBuildOptions.Builder();

    if (sessionOrder) {
      optionsBuilder.withSessionOrder();
    }
    if (realtimeOrder) {
      optionsBuilder.withRealtimeOrder();
    }
    if (enableExpectedExecutionOrder) {
      optionsBuilder.withExpectedExecutionOrder();
    }

    return optionsBuilder.build();
  }

  // New type-safe API
  public ASG buildAsg(KVHistory history, GraphBuildOptions options) throws RejectException {
    log.info("Start graphIR building...");

    // 1. Create and initialize ASG
    ASG asg = new ASG(history, config);
    asg.preBuild();

    // 2. Build graph incrementally using strategy pattern
    GraphBuildContext context = new GraphBuildContext(asg, history, options);

    for (GraphBuilder builder : builders) {
      if (builder.isApplicable(context)) {
        try {
          log.debug("Running builder: {}", builder.getName());
          builder.build(context);
        } catch (RejectException e) {
          throw new RejectException(
              String.format("Failed in %s: %s", builder.getName(), e.getMessage()));
        }
      }
    }

    // 3. Build derived indexes after all DirectDeps have been populated
    asg.buildVersionOrder();

    return asg;
  }

  public ASG apply(KVHistory history, Boolean sessionOrder, boolean realtimeOrder, boolean expectedOrder) {
    return buildWithProfiling(history, sessionOrder, realtimeOrder, expectedOrder);
  }

  /**
   * Builds an ASG with profiling enabled.
   *
   * <p>
   * This method wraps {@link #buildAsg} with profiling instrumentation,
   * recording the time spent in ASG construction under the "building graphir"
   * tag.
   *
   * @param history       the transaction history
   * @param sessionOrder  enable session order constraints
   * @param realtimeOrder enable real-time order constraints
   * @param expectedOrder enable expected execution order constraints
   * @return the constructed ASG
   */
  public ASG buildWithProfiling(KVHistory history, Boolean sessionOrder, boolean realtimeOrder, boolean expectedOrder) {
    Profiler profiler = Profiler.getInstance();
    profiler.startTick("building graphir");
    ASG asg = buildAsg(history, sessionOrder, realtimeOrder, expectedOrder);
    profiler.endTick("building graphir");
    return asg;
  }
}
