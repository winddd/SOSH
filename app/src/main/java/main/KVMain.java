package main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import asg.ASG;
import asg.GraphIRDumper;
import buildASG.ASGConstructor;
import compile.v2.GraphCompilerFactory;
import graphs.BoomslangGraphDumper;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;

import lombok.extern.slf4j.Slf4j;
import optimizations.NormalSearch;
import optimizations.SearchResult;
import optimizations.prioritization.WeightGuidedOpt;
import optimizations.reachability.ReachabilityPruning;
import optimizations.unsat.ReverseSearch;
import parse.KVParserFactory;
import tools.anomaly_injector.ChengUnsatCoreInjector;
import util.Config;
import util.Context;
import util.Profiler;
import util.exception.RejectException;

/**
 * Main orchestration class for Boomslang transaction verification workflow.
 *
 * <p>KVMain coordinates the end-to-end verification pipeline:
 * <ol>
 *   <li><b>Parsing:</b> Converts raw transaction logs to {@link history.KVHistory}</li>
 *   <li><b>ASG Construction:</b> Builds {@link ASG} with metadata and dependencies</li>
 *   <li><b>Compilation:</b> Generates constraint graphs via {@link compile.v2.GraphCompilerFactory}</li>
 *   <li><b>Optimization:</b> Applies reachability pruning and weight-guided optimizations</li>
 *   <li><b>Solving:</b> Runs normal and reverse searches in parallel to detect cycles</li>
 * </ol>
 *
 * <p><b>Search Strategy:</b> Runs two solvers concurrently:
 * <ul>
 *   <li>{@link NormalSearch}: Standard forward cycle detection</li>
 *   <li>{@link ReverseSearch}: Unsat-core-based reverse search (optional)</li>
 * </ul>
 * The first solver to return a result wins, with a configurable timeout (default: 30 minutes).
 *
 * <p><b>Entry Point:</b> Called from {@link Main} after command-line argument parsing.
 *
 * @see Main for CLI entry point
 * @see ASG for core data structure
 * @see buildASG.ASGConstructor for ASG building
 */
@Slf4j
public class KVMain {
  /**
   * Creates a CompletableFuture that never completes.
   *
   * <p>Used in the solver race to disable a search branch: a future that never completes
   * can never win the race, effectively disabling that search path.
   *
   * @param <T> the result type
   * @return a CompletableFuture that will never complete
   */
  public static <T> CompletableFuture<T> never() {
    return new CompletableFuture<>(); // created but never completed
  }

  /**
   * Main verification workflow: parse → build ASG → compile → optimize → solve.
   *
   * <p>This method orchestrates the entire verification pipeline for checking whether
   * a transaction history satisfies a specified isolation level. It:
   * <ol>
   *   <li>Parses the transaction history from the configured format</li>
   *   <li>Optionally injects unsat cores for evaluation purposes</li>
   *   <li>Constructs the Abstract Syntax Graph (ASG) with dependencies</li>
   *   <li>Compiles the ASG into constraint graphs</li>
   *   <li>Applies optimization passes (reachability pruning, weight-guided search)</li>
   *   <li>Runs solver(s) with timeout and returns SAT/UNSAT result</li>
   * </ol>
   *
   * <p><b>Error Handling:</b>
   * <ul>
   *   <li>{@link RejectException}: Indicates UNSAT (isolation violation detected early)</li>
   *   <li>{@link TimeoutException}: Solver timeout - profiling data for completed phases is still saved</li>
   *   <li>Other exceptions: Logged and treated as errors (result may be undefined)</li>
   * </ul>
   *
   * <p><b>Profiling on Timeout:</b>
   * When the solver times out, profiling data is still saved for all completed phases
   * (parsing, ASG construction, compilation, optimization). Only the solving phase timing
   * will be missing. The output JSON will have {@code id=-1} to indicate timeout.
   * Timeout duration is configurable via {@code --timeout} flag (default: 30 minutes).
   *
   * @param ctx execution context containing runtime information
   * @param cfg configuration specifying isolation level, input format, optimizations, etc.
   * @return {@code true} if SAT (history satisfies isolation level), {@code false} if UNSAT or timeout
   */
  public static boolean kvMain(Context ctx, Config cfg) {
    // Config cfg = cfg;
    log.info("Config: " + cfg.toString());
    Profiler profiler = Profiler.getInstance();
    profiler.start();

    var parser = KVParserFactory.getParser(cfg.H_FORMAT, cfg);
    var asgBuilder = new ASGConstructor(cfg);
    String checkMessasge = String.format(
        "Checking %s, isolation=%s, cfg.H_FORMAT=%s, fromFile=%b, cfg.CONFIGFILE=%s%n",
        cfg.HISTORY_FOLDER, cfg.RUNMODE.getIsolationLevel().name(), cfg.H_FORMAT,
        cfg.LOADFROMSINGLEFILE, cfg.CONFIGFILE);
    System.out.printf(checkMessasge);
    log.info(checkMessasge);

    boolean sat = true;
    // ReverseSearch reverseThread;
    // NormalSearch normalThread;
    int id = -1;

    try {
      var history = parser.apply(ctx, cfg.HISTORY_FOLDER);
      log.info("There are " + history.getAllTxns().size() + " txns");
      // only for eval
      if (cfg.INJECT_UNSATCORE) {
        ChengUnsatCoreInjector injector = new ChengUnsatCoreInjector();
        // we inject 20 unsat cores in the history
        history = injector.injectUnsatCores(history, 20);
      }

      ASG asg = asgBuilder.apply(history, cfg.SESSION_ORDER, cfg.REALTIME_ORDER,
          cfg.EXPECTED_ORDER);

      // Read Uncommitted: if ASG builds successfully, it's always SAT
      // (no isolation constraints to check)
      if (cfg.RUNMODE.getIsolationLevel() == util.enumtypes.ISOLATION_LEVEL.READ_UNCOMMITTED) {
        log.info("Read Uncommitted: ASG built successfully, returning SAT without graph compilation");
        sat = true;
        id = 1;
        // Skip to cleanup and return (at end of method)
      } else {
        if (cfg.DEBUG) {
          GraphIRDumper graphIRDumper = new GraphIRDumper(asg);
          graphIRDumper.dumpToFile("ir_" + cfg.RUNMODE.name() + ".txt");
        }

        // GraphCompiler compiler = GraphCompilerFactory.getGraphCompiler(cfg.RUNMODE,
        // cfg);
        // InCompleteGraph g = compiler.compile(ir);
        var compiler = GraphCompilerFactory.getGraphCompiler(cfg.RUNMODE, cfg);
        List<InCompleteGraph> graphs = new ArrayList<>(compiler.compile(asg));
        if (graphs.isEmpty()) {
          throw new IllegalStateException("Graph compiler returned no graphs");
        }

        for (int index = 0; index < graphs.size(); index++) {
          InCompleteGraph graph = graphs.get(index);
          graph.setHistory(history);

          if (cfg.DEBUG) {
            var outputFile = String.format("pregraph_%s_%d.txt", cfg.RUNMODE.name(), index);
            BoomslangGraphDumper dumper = new BoomslangGraphDumper((BoomslangGraph) graph);
            dumper.dumpToFile(outputFile);
          }

          if (cfg.REACHABILITY_PRUNING) {
            ReachabilityPruning reachabilityOptimizer = new ReachabilityPruning(
                graph, cfg);
            graph = reachabilityOptimizer.optimize();
            if (graph == null) {
              throw new RejectException("Cycles detected when pruning");
            }
          }

          log.info(
              "graph {} after preprocess: {} nodes, {} known edges, {} unique known edges, {} general cons, {} superpositions, {} implies",
              index,
              graph.getNodeIds().size(), graph.numOfEdges().getLeft(), graph.numOfEdges().getRight(),
              graph.numGeneralCons(), graph.numSuperpositions(), graph.numImplies());

          if (cfg.WEIGHT_GUIDED_SEARCH) {
            WeightGuidedOpt weightGuidedOptimizer = new WeightGuidedOpt(graph, cfg);
            graph = weightGuidedOptimizer.optimize();
            if (graph == null) {
              throw new RejectException("Cycles detected in known graph");
            }
          }

          graphs.set(index, graph);

          if (cfg.DEBUG) {
            var outputFile = String.format("postpruning_graph_%s_%d.txt", cfg.RUNMODE.name(), index);
            BoomslangGraphDumper dumper = new BoomslangGraphDumper((BoomslangGraph) graph);
            dumper.dumpToFile(outputFile);
          }
        }

        final List<InCompleteGraph> finalGraphs = Collections.unmodifiableList(graphs);

        CompletableFuture<SearchResult> normal = CompletableFuture.supplyAsync(() -> {
          NormalSearch n = new NormalSearch(finalGraphs, 1, cfg);
          return n.search();
        });

        CompletableFuture<SearchResult> reverseFalseOnly = cfg.ENABLE_REVERSE_SEARCH
            ? CompletableFuture.supplyAsync(() -> {
              ReverseSearch r = new ReverseSearch(finalGraphs, cfg, 2);
              return r.search();
            }).thenCompose(r -> r.isSat()
                ? never() // don't let SAT win the race
                : CompletableFuture.completedFuture(r)) // UNSAT triggers early finish
            : never(); // if disabled, it can't win

        CompletableFuture<SearchResult> race = normal.applyToEither(reverseFalseOnly, r -> r);

        SearchResult searchResult = race.orTimeout(cfg.SOLVER_TIMEOUT_MINUTES, TimeUnit.MINUTES).join();

        var tag2Time = searchResult.getTag2Time();
        for (var tag : tag2Time.keySet()) {
          profiler.putTagRuntime(tag, tag2Time.get(tag));
        }

        sat = searchResult.isSat();
        id = searchResult.getId();
      }
    } catch (RejectException e) {
      sat = false;
      id = 1;
       e.printStackTrace(); // for debugging
    } catch (CompletionException e) {
      // Check if this is a timeout exception
      if (e.getCause() instanceof TimeoutException) {
        System.out.println("Solver timed out after " + cfg.SOLVER_TIMEOUT_MINUTES + " minutes");
        System.out.println("Profiling data will still be saved for completed phases");

        // Merge profiling data from all threads (main + solver threads)
        // This ensures we capture parsing, ASG, compilation, optimization timing
        // even though the solver threads timed out
        profiler.mergeAllThreadProfilers();

        sat = false;  // Treat timeout as UNSAT (unknown result)
        id = -1;      // -1 indicates timeout
      } else {
        System.out.println("Exception: " + e.getMessage());
         e.printStackTrace(); // for debugging
      }
    } catch (Exception | Error e) {
      System.out.println("Exception: " + e.getMessage());
       e.printStackTrace(); // for debugging
    }

    // Cleanup and return
    System.out.printf("%d: %b\n", id, sat);
    profiler.endAll();
    profiler.recordResults();
    profiler.printProfilingResults(cfg.PERF_FILE, cfg.EXP_NAME, sat);

    // Save results to history folder
    saveResultsToHistoryFolder(cfg, sat, id, profiler);

    return sat;
  }

  /**
   * Saves verification results to a JSON file in the history folder.
   *
   * <p>Creates a result file named "boomslang_result.json" in the same directory as the
   * input history. This allows each history to have its own local result file alongside
   * the transaction logs.
   *
   * @param cfg configuration containing history folder path
   * @param sat whether the history satisfies the isolation level
   * @param id result identifier (-1 for timeout, 1 for early rejection, etc.)
   * @param profiler profiler instance containing timing statistics
   */
  private static void saveResultsToHistoryFolder(Config cfg, boolean sat, int id, Profiler profiler) {
    try {
      String historyPath = cfg.HISTORY_FOLDER;
      java.io.File historyFile = new java.io.File(historyPath);

      // Determine the directory to save results
      java.io.File resultDir;
      if (cfg.LOADFROMSINGLEFILE) {
        // If single file, save in the parent directory
        resultDir = historyFile.getParentFile();
      } else {
        // If directory, save in the history directory itself
        resultDir = historyFile.isDirectory() ? historyFile : historyFile.getParentFile();
      }

      if (resultDir == null || !resultDir.exists()) {
        log.warn("Cannot save result to history folder: directory does not exist: {}", resultDir);
        return;
      }

      // Create result filename based on history name
      String historyName = historyFile.getName();
      // Remove extension if it's a file
      if (cfg.LOADFROMSINGLEFILE && historyName.contains(".")) {
        historyName = historyName.substring(0, historyName.lastIndexOf('.'));
      }
      String resultFileName = historyName + "_boomslang_result.json";
      java.io.File resultFile = new java.io.File(resultDir, resultFileName);

      // Generate JSON content
      String runtimeStats = profiler.getRuntimeStatistics().replace("\n", "");
      String json = String.format(
          "{\n  \"sat\": %b,\n  \"id\": %d,\n  \"isolation_level\": \"%s\",\n  \"format\": \"%s\",\n  \"fromFile\": %b,\n  \"history_path\": \"%s\",\n  \"timing\": %s\n}\n",
          sat,
          id,
          cfg.RUNMODE.getIsolationLevel().name(),
          cfg.H_FORMAT.name(),
          cfg.LOADFROMSINGLEFILE,
          historyPath.replace("\\", "\\\\"),
          runtimeStats
      );

      // Write to file
      try (java.io.FileWriter writer = new java.io.FileWriter(resultFile)) {
        writer.write(json);
      }

      log.info("Saved verification result to: {}", resultFile.getAbsolutePath());
      System.out.println("Result saved to: " + resultFile.getAbsolutePath());

    } catch (Exception e) {
      log.warn("Failed to save result to history folder: {}", e.getMessage());
      // Don't fail the whole verification if we can't save the result file
    }
  }
}
