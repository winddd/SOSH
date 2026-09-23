package util;

import common.Key;
import util.enumtypes.HISTORY_FORMAT;
import util.enumtypes.MODE;
import util.enumtypes.SMT_SOLVER;
import util.isolation.IsolationSpec;
import util.isolation.RYOWPolicy;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.StringJoiner;

/**
 * Configuration container for Boomslang verification settings.
 *
 * <p>Config holds all runtime configuration parameters that control:
 * <ul>
 *   <li><b>Input/Output:</b> History format, input folder, output files</li>
 *   <li><b>Verification Mode:</b> Isolation level to check (SER, SI, RC, RU, PL-*)</li>
 *   <li><b>Optimizations:</b> Reachability pruning, weight-guided search, constraint coalescing</li>
 *   <li><b>Solver Selection:</b> SMT solver backend (MonoSAT, etc.)</li>
 *   <li><b>Compatibility Modes:</b> Flags for Cobra, PolySI, and other tool compatibility</li>
 *   <li><b>Debugging:</b> Debug mode, graph dumping, performance profiling</li>
 * </ul>
 *
 * <p><b>Key Configuration Categories:</b>
 * <ul>
 *   <li><b>Input:</b> {@code HISTORY_FOLDER}, {@code H_FORMAT}, {@code LOADFROMSINGLEFILE}</li>
 *   <li><b>Mode:</b> {@code RUNMODE} (isolation level), {@code SESSION_ORDER}, {@code REALTIME_ORDER}</li>
 *   <li><b>Optimizations:</b> {@code WEIGHT_GUIDED_SEARCH}, {@code REACHABILITY_PRUNING}, {@code BFS_PRUNING}</li>
 *   <li><b>Constraints:</b> {@code COBRA_COALESCE_CONSTRAINTS}, {@code VIPER_COALESCE_CONSTRAINTS}</li>
 *   <li><b>Solver:</b> {@code SMTSOLVER}, {@code ENABLE_REVERSE_SEARCH}</li>
 *   <li><b>Output:</b> {@code PERF_FILE}, {@code GRAPHIR_FOLDER}, {@code DEBUG}</li>
 * </ul>
 *
 * <p><b>Usage:</b> Typically populated by {@link main.Main#cmdParse(String[])} from
 * command-line arguments or config files.
 *
 * <p><b>Mutability:</b> This is a mutable configuration object. All fields are public
 * for backward compatibility. Future refactoring may introduce a builder pattern.
 *
 * @see main.Main for CLI parsing
 * @see util.enumtypes.MODE for isolation level options
 * @see util.enumtypes.HISTORY_FORMAT for input format options
 */
public class Config implements Serializable {
//  private static Config instance = null;
  public int NUM_SUBGRAPHS = 10;
  public boolean SESSION_ORDER = false;
  public MODE RUNMODE = MODE.B_SER;
  public String HISTORY_FOLDER = "";
  public boolean LOADFROMSINGLEFILE = false;
  public String PERF_FILE = null;
  public HISTORY_FORMAT H_FORMAT = HISTORY_FORMAT.BINARY;
  public SMT_SOLVER SMTSOLVER = SMT_SOLVER.MONOSAT;
  public boolean DEBUG = false;
  public String CONFIGFILE = "";
  public String EXP_NAME;
  public String GRAPHIR_FOLDER = "/tmp/";
  public boolean WEIGHT_GUIDED_SEARCH = true;
  public boolean REACHABILITY_PRUNING = true;
  /**
   * USE BFS topological sort for reachability pruning.
   */
  public boolean BFS_PRUNING = true;
  public boolean COBRA_COALESCE_CONSTRAINTS = true;
  public boolean VIPER_COALESCE_CONSTRAINTS = true;
  public boolean COBRA_COMPATIBLE_MODE = false;
  public boolean ENABLE_EDGES_REDUCTION = false;
  public boolean ENABLE_REVERSE_SEARCH = false;
  public boolean MATRIX_PARTITION = false;
  public boolean POLYSI_COMPATIBLE_MODE = false;
  public boolean MULTIPLE_ROUNDS_PRUNING = false;
  public boolean DISABLE_HASHMAP = false;
  public boolean INJECT_UNSATCORE = false;
  public int SUB_MATRIX_SIZE = 5000;
  public Key obj = Key.getNullKey();
  public boolean REALTIME_ORDER = false;
  public boolean DISABLE_INITIALTXN = false;
  public boolean EXPECTED_ORDER = false;
  public boolean HINTS_COMPATIBLE_MODE = false;
  public RYOWPolicy RYOW_POLICY = null; // null means use default from isolation level
  public int NUM_CYCLES_TO_PRINT = 1; // Number of cycles to print when DEBUG is enabled
  public int SOLVER_TIMEOUT_MINUTES = 30; // Timeout for solver in minutes (default: 30)

  /**
   * Returns the isolation specification for the current run mode.
   *
   * <p>This is a convenience method equivalent to {@code RUNMODE.spec()}.
   * The returned spec is a cached singleton instance.
   *
   * @return the isolation specification describing the current mode's isolation guarantees
   */
  public IsolationSpec getIsolationSpec() {
    return RUNMODE.spec();
  }
  // FIXME: store history here globally, this is ugly, fix this after osdi submission
//  public static Config get() {
//    assert instance != null;
//    return instance;
//  }

  /**
   * Dynamically sets a configuration field by name using reflection.
   *
   * <p>This method allows runtime modification of configuration fields without
   * direct field access. Primarily used for config file parsing.
   *
   * @param varName the field name (must match exactly, case-sensitive)
   * @param varValue the value to set
   * @throws RuntimeException if field doesn't exist or value type is incompatible
   */
  public void set(String varName, Object varValue) {
    try {
      Field field = Config.class.getDeclaredField(varName);
      field.set(this, varValue);
    } catch (IllegalArgumentException | NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString() {
    StringJoiner joiner = new StringJoiner(", ", "Config{", "}");
    for (Field field : Config.class.getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      try {
        joiner.add(field.getName() + "=" + field.get(this));
      } catch (IllegalAccessException ignored) {
        // fall back to placeholder when inaccessible
        joiner.add(field.getName() + "=<inaccessible>");
      }
    }
    return joiner.toString();
  }
}
