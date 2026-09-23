package main;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import lombok.extern.slf4j.Slf4j;
import util.Config;
import util.Context;
import util.Utils;
import util.enumtypes.HISTORY_FORMAT;
import util.enumtypes.MODE;
import util.enumtypes.SMT_SOLVER;
import util.isolation.RYOWPolicy;

/**
 * Main entry point for the Boomslang black-box checking tool.
 * This class handles command line argument parsing and configuration setup.
 *
 * Usage examples:
 * - Basic usage: java -jar boomslang.jar -m B_SER -h /path/to/history
 * - Debug mode: java -jar boomslang.jar -m B_SER -h /path/to/history -d true
 * - Custom solver: java -jar boomslang.jar -m B_SER -h /path/to/history
 * --solver monosat
 */
@Slf4j
public class Main {
  private static final Set<String> VALID_MODES = Arrays.stream(MODE.values())
      .map(Enum::name)
      .collect(Collectors.toSet());

  private static final Set<String> VALID_FORMATS = Arrays.stream(HISTORY_FORMAT.values())
      .map(Enum::name)
      .collect(Collectors.toSet());

  private static final Set<String> VALID_SOLVERS = Arrays.stream(SMT_SOLVER.values())
      .map(Enum::name)
      .collect(Collectors.toSet());

  private static final Set<String> VALID_RYOW_POLICIES = Arrays.stream(RYOWPolicy.values())
      .map(Enum::name)
      .collect(Collectors.toSet());

  private static Options createOptions() {
    return new Options()
        .addOption(Option.builder("checkSubgraph")
            .hasArg()
            .desc("Check subgraph configuration")
            .build())
        .addOption(Option.builder("m")
            .longOpt("mode")
            .hasArg()
            .required()
            .desc("Operation mode (B_SER/B_SI/B_RC/B_RU/P/C/V)")
            .build())
        .addOption(Option.builder("h")
            .longOpt("history_folder")
            .hasArg()
            .required()
            .desc("Folder containing history files")
            .build())
        .addOption(Option.builder("fromFile")
            .longOpt("loadFromSingleFile")
            .hasArg()
            .desc("Load logs from a single file instead of a folder")
            .build())
        .addOption(Option.builder("config")
            .longOpt("configFile")
            .hasArg()
            .desc("Path to configuration file")
            .build())
        .addOption(Option.builder("format")
            .longOpt("historyFormat")
            .hasArg()
            .desc("History format (edn/json/binary/binary_rmw/hybrid_snapshot_binary/hybrid_snapshot_json/cobra)")
            .build())
        .addOption(Option.builder("solver")
            .longOpt("SMT_solver")
            .hasArg()
            .desc("SMT solver to use")
            .build())
        .addOption(Option.builder("d")
            .longOpt("debug")
            .hasArg(false)
            .desc("Enable debug mode")
            .build())
        .addOption(Option.builder("session")
            .longOpt("session_order")
            .hasArg(false)
            .desc("Enable session order")
            .build())
        .addOption(Option.builder("no_weight")
            .longOpt("no_weight_guided_search")
            .hasArg(false)
            .desc("Disable priority for edges")
            .build())
        .addOption(Option.builder("no_reach")
            .longOpt("no_reachability_pruning")
            .hasArg(false)
            .desc("Disable reachability pruning")
            .build())
        .addOption(Option.builder("no_hashmap")
            .longOpt("disable_hashmap")
            .hasArg(false)
            .desc("Disable hashmap in Encoder1")
            .build())
        .addOption(Option.builder("disable_topoClosure")
            .hasArg(false)
            .desc("Disable topological-sort/BFS-based transitive closure optimizations")
            .build())
        .addOption(Option.builder("no_cobra_coalesce")
            .longOpt("no_cobra_coalesce_constraints")
            .hasArg(false)
            .desc("Disable cobra's coalesce constraints optimization")
            .build())
        .addOption(Option.builder("output")
            .longOpt("outputFileName")
            .hasArg()
            .desc("File to store performance metrics")
            .build())
        .addOption(Option.builder("run_name")
            .hasArg()
            .desc("Name for this run (used for performance metrics)")
            .build())
        .addOption(Option.builder("unsat_search")
            .hasArg(false)
            .desc("Enable reverse unsat search")
            .build())
        .addOption(Option.builder("matrix_partition")
            .hasArg(false)
            .desc("Partition reachability matrix into submatrix")
            .build())
        .addOption(Option.builder("rto")
            .longOpt("realtime_order")
            .hasArg(false)
            .desc("Enable realtime edges")
            .build())
        .addOption(Option.builder("disable_t0")
            .longOpt("disable_initial_txn")
            .hasArg(false)
            .desc("Assume history doesn't have initial transaction")
            .build())
        .addOption(Option.builder("expected_order")
            .longOpt("enable_expected_order")
            .hasArg(false)
            .desc("Assume execution order by KVTxn.txnTS")
            .build())
        .addOption(Option.builder("hcm")
            .longOpt("hint_compatible_mode")
            .hasArg(false)
            .desc("enable this mode to be compatible with baselines like CobraBench")
            .build())
        .addOption(Option.builder("ryow")
            .longOpt("ryow_policy")
            .hasArg()
            .desc("Read-Your-Own-Writes policy (MUST_RYOW/MUST_EXT/EITHER)")
            .build())
        .addOption(Option.builder("num_cycles")
            .longOpt("num_cycles_to_print")
            .hasArg()
            .desc("Number of cycles to print when debug mode is enabled (default: 1)")
            .build())
        .addOption(Option.builder("timeout")
            .longOpt("solver_timeout_minutes")
            .hasArg()
            .desc("Timeout for solver in minutes (default: 30)")
            .build());
  }

  private static void validateArguments(CommandLine cmd) {
    String mode = cmd.getOptionValue("m").toUpperCase();
    if (!VALID_MODES.contains(mode)) {
      throw new IllegalArgumentException("Invalid mode: " + mode
          + ". Valid modes are: " + VALID_MODES);
    }

    if (cmd.hasOption("format")) {
      String format = cmd.getOptionValue("format").toUpperCase();
      if (!VALID_FORMATS.contains(format)) {
        throw new IllegalArgumentException("Invalid format: " + format
            + ". Valid formats are: " + VALID_FORMATS);
      }
    }

    if (cmd.hasOption("solver")) {
      String solver = cmd.getOptionValue("solver").toUpperCase();
      if (!VALID_SOLVERS.contains(solver)) {
        throw new IllegalArgumentException("Invalid solver: " + solver
            + ". Valid solvers are: " + VALID_SOLVERS);
      }
    }

    if (cmd.hasOption("ryow")) {
      String ryowPolicy = cmd.getOptionValue("ryow").toUpperCase();
      if (!VALID_RYOW_POLICIES.contains(ryowPolicy)) {
        throw new IllegalArgumentException("Invalid RYOW policy: " + ryowPolicy
            + ". Valid policies are: " + VALID_RYOW_POLICIES);
      }
    }
  }

  private static Config cmdParse(final String[] args) {
    Options options = createOptions();
    CommandLineParser cmdParser = new DefaultParser();
    HelpFormatter helpFormatter = new HelpFormatter();

    try {
      CommandLine cmd = cmdParser.parse(options, args);
      validateArguments(cmd);

      // Load config file
      String configFile = cmd.getOptionValue("config");
      var cfg = Utils.createConfig(configFile);

      // Set configuration values
      cfg.set("CONFIGFILE", configFile);
      cfg.set("RUNMODE", MODE.valueOf(cmd.getOptionValue("m").toUpperCase()));
      cfg.set("HISTORY_FOLDER", cmd.getOptionValue("h"));
      cfg.set("LOADFROMSINGLEFILE", Boolean.parseBoolean(
          cmd.getOptionValue("fromFile", "false")));

      if (cmd.hasOption("format")) {
        cfg.set("H_FORMAT", HISTORY_FORMAT.valueOf(
            cmd.getOptionValue("format").toUpperCase()));
      }

      cfg.set("SMTSOLVER", SMT_SOLVER.valueOf(
          cmd.getOptionValue("solver", "monosat").toUpperCase()));
      cfg.set("DEBUG", cmd.hasOption("debug"));
      cfg.set("SESSION_ORDER", cmd.hasOption("session"));
      cfg.set("PERF_FILE", cmd.getOptionValue("output", "perf.json"));
      cfg.set("EXP_NAME", cmd.getOptionValue("run_name", "test.json"));
      // Set boolean flags
      cfg.set("WEIGHT_GUIDED_SEARCH", !cmd.hasOption("no_weight_guided_search"));
      cfg.set("REACHABILITY_PRUNING", !cmd.hasOption("no_reachability_pruning"));
      cfg.set("COBRA_COALESCE_CONSTRAINTS", !cmd.hasOption("no_cobra_coalesce_constraints"));
      boolean topoClosureEnabled = !cmd.hasOption("disable_topoClosure");
      cfg.set("BFS_PRUNING", topoClosureEnabled);
      cfg.set("ENABLE_REVERSE_SEARCH", cmd.hasOption("unsat_search"));
      cfg.set("MATRIX_PARTITION", cmd.hasOption("matrix_partition"));
      cfg.set("DISABLE_HASHMAP", cmd.hasOption("disable_hashmap"));
      cfg.set("REALTIME_ORDER", cmd.hasOption("rto"));
      cfg.set("DISABLE_INITIALTXN", cmd.hasOption("disable_t0"));
      cfg.set("EXPECTED_ORDER", cmd.hasOption("expected_order"));
      cfg.set("HINTS_COMPATIBLE_MODE", cmd.hasOption("hcm"));

      if (cmd.hasOption("ryow")) {
        cfg.set("RYOW_POLICY", RYOWPolicy.valueOf(cmd.getOptionValue("ryow").toUpperCase()));
      }

      if (cmd.hasOption("num_cycles")) {
        cfg.set("NUM_CYCLES_TO_PRINT", Integer.parseInt(cmd.getOptionValue("num_cycles")));
      }

      if (cmd.hasOption("timeout")) {
        cfg.set("SOLVER_TIMEOUT_MINUTES", Integer.parseInt(cmd.getOptionValue("timeout")));
      }

      return cfg;
    } catch (ParseException e) {
      log.error("Failed to parse command line arguments", e);
      helpFormatter.printHelp("boomslang", options);
      throw new RuntimeException("Invalid command line arguments", e);
    } catch (IllegalArgumentException e) {
      log.error("Invalid argument value", e);
      helpFormatter.printHelp("boomslang", options);
      throw e;
    }
  }

  public static void main(String[] args) {
    log.info("Starting Boomslang: {}", String.join(" ", args));
    try {
      log.info("Starting Boomslang with arguments: {}", Arrays.toString(args));
      var cfg = cmdParse(args);
      Context ctx = new Context(cfg.DEBUG);
      KVMain.kvMain(ctx, cfg);
      log.info("Boomslang completed successfully");
    } catch (Exception e) {
      log.error("Boomslang failed", e);
      System.exit(1);
    }
  }
}
