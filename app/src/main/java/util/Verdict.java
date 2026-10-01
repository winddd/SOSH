package util;

/**
 * Outcome of one checker run.
 *
 * <p>Only {@link #SAT} and {@link #UNSAT} say something about the history. {@link #TIMEOUT}
 * (solver exceeded {@code -timeout}) and {@link #ERROR} (the run crashed) carry no verdict and
 * must never be reported as SAT or UNSAT.
 */
public enum Verdict {
  SAT,
  UNSAT,
  TIMEOUT,
  ERROR;

  /** Value of the {@code "sat"} field in result JSON: {@code null} when there is no verdict. */
  public Boolean satOrNull() {
    return switch (this) {
      case SAT -> true;
      case UNSAT -> false;
      case TIMEOUT, ERROR -> null;
    };
  }

  /** Value after {@code "<id>: "} in the stdout result line: true/false, or TIMEOUT/ERROR. */
  public String resultLineValue() {
    return switch (this) {
      case SAT -> "true";
      case UNSAT -> "false";
      case TIMEOUT, ERROR -> name();
    };
  }

  /** Process exit code: 0 for SAT and UNSAT (both are answers), non-zero otherwise. */
  public int exitCode() {
    return switch (this) {
      case SAT, UNSAT -> 0;
      case ERROR -> 2;
      case TIMEOUT -> 3;
    };
  }
}
