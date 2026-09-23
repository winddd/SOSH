package history;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import main.KVMain;
import util.Config;
import util.Context;
import util.enumtypes.ISOLATION_LEVEL;
import util.enumtypes.MODE;

public class TestUtils {
  private static final Logger log = LoggerFactory.getLogger(TestUtils.class);
  private static String BOOMSLANG_HOME;
  // for a given isolation level, si type and anomaly, which answer to expect from
  // the checker.
  // true: allowed; false: forbidden.
  private static Map<ISOLATION_LEVEL, String> iso2Tag = Map.of(
      ISOLATION_LEVEL.SERIALIZABLE, "SER",
      ISOLATION_LEVEL.SNAPSHOT_ISOLATION, "SI",
      // ISOLATION_LEVEL.REPEATABLE_READ, "RR",
      ISOLATION_LEVEL.READ_COMMITTED, "RC",
      ISOLATION_LEVEL.READ_UNCOMMITTED, "RU");

  public static boolean queryGT(MODE mode, Map<String, Boolean> gt) {
    return gt.get(iso2Tag.get(mode.getIsolationLevel()));
  }

  public static boolean doTest(Context ctx, Config cfg) {
    return new KVMain().kvMain(ctx, cfg);
  }
}
