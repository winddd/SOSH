package util.enumtypes;

import util.isolation.*;

public enum ISOLATION_LEVEL {
  SERIALIZABLE,
  SNAPSHOT_ISOLATION,
  READ_COMMITTED,
  READ_UNCOMMITTED,
  PL_299,
  PL_FCV,
  PL_2P,
  PL_CS,
  STRICT_SERIALIZABLE;

  /** Maps numeric config values to their isolation level. */
  public static ISOLATION_LEVEL fromInteger(int i) {
    ISOLATION_LEVEL[] isolations = new ISOLATION_LEVEL[] { SERIALIZABLE, SNAPSHOT_ISOLATION,
        READ_COMMITTED, READ_UNCOMMITTED, PL_299, PL_FCV, PL_2P, PL_CS,
        STRICT_SERIALIZABLE };
    return isolations[i];
  }

  /** Returns true only for levels that enforce real-time order. */
  public boolean enforceRealtimeOrder() {
    return spec().resprectRealtimeOrder();
  }

  /** Indicates whether reads must see their own writes. */
  public RYOWPolicy getReadOwnWritePolicy() {
    return spec().getReadOwnWritePolicy();
  }

  /** Returns true for levels that permit non-repeatable reads. */
  public boolean allowNonRepeatableReads() {
    return spec().allowNonRepeatableReads();
  }

  /** Whether we can derive version order from RMW edges under this isolation. */
  public boolean canInferVersionOrderFromRMW() {
    return spec().canInferVersionOrderFromRMW();
  }

  /** Returns a policy object that describes this isolation level's guarantees. */
  public IsolationSpec spec() {
    return IsolationSpecHolder.SPEC_BY_LEVEL.get(this);
  }

  public boolean allowIntermediateReads() {
    return spec().allowIntermediateReads();
  }

  private static final class IsolationSpecHolder {
    private static final java.util.EnumMap<ISOLATION_LEVEL, IsolationSpec> SPEC_BY_LEVEL = buildSpecs();

    private static java.util.EnumMap<ISOLATION_LEVEL, IsolationSpec> buildSpecs() {
      var map = new java.util.EnumMap<ISOLATION_LEVEL, IsolationSpec>(ISOLATION_LEVEL.class);
      map.put(ISOLATION_LEVEL.SERIALIZABLE, new SerSpec());
      map.put(ISOLATION_LEVEL.SNAPSHOT_ISOLATION, new SiSpec());
      map.put(ISOLATION_LEVEL.READ_COMMITTED, new RcSpec());
      map.put(ISOLATION_LEVEL.READ_UNCOMMITTED, new RuSpec());
      map.put(ISOLATION_LEVEL.PL_299, new Pl299Spec());
      map.put(ISOLATION_LEVEL.PL_FCV, new PlFcvSpec());
      map.put(ISOLATION_LEVEL.PL_2P, new Pl2pSpec());
      map.put(ISOLATION_LEVEL.PL_CS, new PlCsSpec());
      map.put(ISOLATION_LEVEL.STRICT_SERIALIZABLE, new StrictSerSpec());
      return map;
    }
  }
}
