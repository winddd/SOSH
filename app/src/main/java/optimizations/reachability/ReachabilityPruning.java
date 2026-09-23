package optimizations.reachability;

import graphs.graphs.InCompleteGraph;
import lombok.extern.slf4j.Slf4j;
import optimizations.GraphOptimizer;
import optimizations.reachability.pruner.Pruner;
import optimizations.reachability.pruner.PrunerFactory;
import util.Config;
import util.Profiler;

@Slf4j
public class ReachabilityPruning implements GraphOptimizer {
  private static final double stopThreshold = 0.01;
  Profiler profiler;
  private InCompleteGraph graph;
  private Config cfg;

  public ReachabilityPruning(InCompleteGraph graph, Config cfg) {
    this.graph = graph;
    this.profiler = Profiler.getInstance();
    this.cfg = cfg;
  }

  @Override
  public InCompleteGraph optimize() {
    assert cfg.REACHABILITY_PRUNING;
    profiler.startTick("OPT");
    log.info("Starting pruning");
    int numNodes = graph.getNodeIds().size();
    int numEdges = graph.numOfEdges().getLeft();
    int numGeneralCons = graph.numGeneralCons();
    int numSuperpositions = graph.numSuperpositions();
    int initialTotalCons = graph.numGeneralCons() + graph.numSuperpositions();

    boolean hasCycle = false;
    int numSolvedGeneralCons = 0;
    int numSolvedSuperpositions = 0;
    int numSimplifiedSuperpositions = 0;
    int solvedTotalCons = 0;
    int rounds = 0;
    String str = String.format(
        "Initially, there are %d nodes, %d known edges, %d general cons, %d superpositions, %d implies",
        numNodes, numEdges, numGeneralCons, numSuperpositions, graph.numImplies());
    log.info(str);
    System.out.println(str);

    Pruner pruner = PrunerFactory.getPruner(cfg);
    while (!hasCycle) {
      // If there is no cons initially, skip pruning.
      if (initialTotalCons == 0)
        break;
      // reconstruct reachability
      log.info("Pruning...");
      var pruneResult = pruner.prune(graph);
      hasCycle = pruneResult.hasCycle;

      numSolvedGeneralCons += pruneResult.numSolvedGeneralCons;
      numSolvedSuperpositions += pruneResult.numSolvedSuperpositions;
      numSimplifiedSuperpositions += pruneResult.numSimplifiedSuperpositions;
      str = String.format("Round %d: solved %d general cons, %d superpositions, simplified %d superpositions",
          rounds,
          pruneResult.numSolvedGeneralCons,
          pruneResult.numSolvedSuperpositions,
          pruneResult.numSimplifiedSuperpositions);
      log.info(str);
      // System.out.println(str);
      var currProgress = pruneResult.solvedProgress();
      solvedTotalCons += currProgress;
      rounds++;
      if (cfg.MULTIPLE_ROUNDS_PRUNING) {
        if (currProgress <= stopThreshold * initialTotalCons
            || (initialTotalCons - solvedTotalCons) <= stopThreshold * initialTotalCons) {
          break;
        }
      } else {
        break;
      }
    }

    log.info("End pruning");
    profiler.endTick("OPT");
    log.info(String.format("%d Rounds: solved %d general cons, %d superpositions, simplified %d superpositions",
        rounds, numSolvedGeneralCons, numSolvedSuperpositions, numSimplifiedSuperpositions));
    log.info(
        "Finally, there are {} nodes, {} known edges, {} unique known edges, {} general cons, {} superpositions, {} implies",
        graph.getNodeIds().size(), graph.numOfEdges().getLeft(), graph.numOfEdges().getRight(),
        graph.numGeneralCons(), graph.numSuperpositions(), graph.numImplies());
    return graph;
  }
}
