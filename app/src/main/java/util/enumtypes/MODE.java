package util.enumtypes;

import java.util.Map;
import java.util.Set;

import util.isolation.IsolationSpec;

/**
 * B_SER, B_SI, B_RC, B_RU: 4 isolation level checkers of Boomslang
 * B_PL2P: PL2+, use BoomslangGraph
 * B_PLCS: Cursor stability, use BoomslangGraph
 * P: PolySI SI checker
 * V: Viper SI checker
 * C: Cobra SER checker
 * A_SER: Adya SER checker
 */
public enum MODE {B_SER, B_SI, B_RC, B_RU, B_PL299, B_PLFCV, P, C, V, A_SER, A_SI, A_SI2, B_PL2P, B_PLCS, B_SSER;
  private static final Set<MODE> SER_MODES = Set.of(B_SER, C, A_SER);
  private static final Set<MODE> SI_MODES = Set.of(B_SI, P, V, A_SI);
  private static final Set<MODE> RC_MODES = Set.of(B_RC);
  private static final Set<MODE> RU_MODES = Set.of(B_RU);
  private static final Set<MODE> BLACKBOX_MODES = Set.of(
      B_SER, B_SI, B_RC, B_RU, B_PL299, B_PLFCV, P, C, V,
      B_PL2P, B_PLCS, B_SSER);
  private static final Map<MODE, ISOLATION_LEVEL> MODE_TO_ISOLATION = Map.ofEntries(
      Map.entry(B_SER, ISOLATION_LEVEL.SERIALIZABLE),
      Map.entry(C, ISOLATION_LEVEL.SERIALIZABLE),
      Map.entry(A_SER, ISOLATION_LEVEL.SERIALIZABLE),
      Map.entry(B_SI, ISOLATION_LEVEL.SNAPSHOT_ISOLATION),
      Map.entry(P, ISOLATION_LEVEL.SNAPSHOT_ISOLATION),
      Map.entry(V, ISOLATION_LEVEL.SNAPSHOT_ISOLATION),
      Map.entry(A_SI, ISOLATION_LEVEL.SNAPSHOT_ISOLATION),
      Map.entry(A_SI2, ISOLATION_LEVEL.SNAPSHOT_ISOLATION),
      Map.entry(B_RC, ISOLATION_LEVEL.READ_COMMITTED),
      Map.entry(B_RU, ISOLATION_LEVEL.READ_UNCOMMITTED),
      Map.entry(B_PL299, ISOLATION_LEVEL.PL_299),
      Map.entry(B_PLFCV, ISOLATION_LEVEL.PL_FCV),
      Map.entry(B_PL2P, ISOLATION_LEVEL.PL_2P),
      Map.entry(B_PLCS, ISOLATION_LEVEL.PL_CS),
      Map.entry(B_SSER, ISOLATION_LEVEL.STRICT_SERIALIZABLE)
  );

  public static Set<MODE> getBlackboxModes() {
    return BLACKBOX_MODES;
  }

  public ISOLATION_LEVEL getIsolationLevel() {
    return MODE_TO_ISOLATION.get(this);
  }

  /**
   * Returns the isolation specification for this mode.
   *
   * <p>This is a convenience method equivalent to {@code getIsolationLevel().spec()}.
   * The returned spec is a cached singleton instance.
   *
   * @return the isolation specification describing this mode's isolation guarantees
   */
  public IsolationSpec spec() {
    return getIsolationLevel().spec();
  }
}
