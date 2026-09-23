package history;

import static util.enumtypes.MODE.B_RC;
import static util.enumtypes.MODE.B_RU;
import static util.enumtypes.MODE.B_SER;
import static util.enumtypes.MODE.B_SI;
import static util.enumtypes.MODE.C;
import static util.enumtypes.MODE.P;
import static util.enumtypes.MODE.V;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import parse.LogsLoader;
import util.Context;
import util.Utils;
import util.enumtypes.HISTORY_FORMAT;
import util.enumtypes.MODE;

public class BlackboxWithSessionOrderTest {
  //  private Map<String, ISOLATION_LEVEL> groundTruth;
  static String BOOMSLANG_HOME;
  static List<MODE> TARGET_MODES = List.of(B_SER, B_SI, B_RC, B_RU, C, V, P);
  static Path cfgPath;

  @BeforeClass
  public static void init() {
    // load config file
    BOOMSLANG_HOME = System.getenv("BOOMSLANG_HOME");
    if (BOOMSLANG_HOME == null || BOOMSLANG_HOME.isEmpty()) {
      throw new SkipException("BOOMSLANG_HOME is not configured; skipping blackbox session-order tests");
    }
    cfgPath = Paths.get(BOOMSLANG_HOME, "config.yaml");
  }

  /**
   * Get the full log folder path and ground truth for a given subdirectory.
   * @param subDir the subdirectory name
   * @return pair containing the logs folder path and ground truth map
   */
  private Pair<String, Map<String, Boolean>> getPaths(String subDir) {
    String logsFolder =
      Paths.get(BOOMSLANG_HOME, "test_logs", "unittest2", subDir, "logs").toString();
    Path gtFile =
      Paths.get(BOOMSLANG_HOME, "test_logs", "unittest2", subDir, "groundtruth.json");
    Map<String, Boolean> groundTruth = LogsLoader.loadGroundTruthFile(gtFile);
    return Pair.of(logsFolder, groundTruth);
  }

  /**
   * Helper method to run a single isolation level test for a given subdirectory and mode.
   * @param subDir the subdirectory name (e.g., "bp-a", "bp-b")
   * @param mode the isolation mode to test
   * @param testName descriptive name for the test case
   */
  private void runSingleModeTest(String subDir, MODE mode, String testName) {
    var pair = getPaths(subDir);
    var logFolder = pair.getLeft();
    var groundTruths = pair.getRight();
    var cfg = Utils.createConfig(cfgPath.toString());
    cfg.H_FORMAT = HISTORY_FORMAT.JSON;
    cfg.HISTORY_FOLDER = logFolder;
    cfg.RUNMODE = mode;
    cfg.SESSION_ORDER = true;
    cfg.CONFIGFILE = BOOMSLANG_HOME + "/config.yaml";

    var gt = TestUtils.queryGT(mode, groundTruths);
    var actual = TestUtils.doTest(new Context(cfg.DEBUG), cfg);
    Assert.assertEquals(actual, gt,
      String.format("Test case '%s' failed for mode %s: expected %s, got %s",
        testName, mode, gt, actual));
  }

  // Test Case A - All Modes
  @Test void testA_B_SER() { runSingleModeTest("bp-a", B_SER, "Test Case A"); }
  @Test void testA_B_SI() { runSingleModeTest("bp-a", B_SI, "Test Case A"); }
  @Test void testA_B_RC() { runSingleModeTest("bp-a", B_RC, "Test Case A"); }
  @Test void testA_B_RU() { runSingleModeTest("bp-a", B_RU, "Test Case A"); }
  @Test void testA_C() { runSingleModeTest("bp-a", C, "Test Case A"); }
  @Test void testA_V() { runSingleModeTest("bp-a", V, "Test Case A"); }
  @Test void testA_P() { runSingleModeTest("bp-a", P, "Test Case A"); }

  // Test Case B - All Modes
  @Test void testB_B_SER() { runSingleModeTest("bp-b", B_SER, "Test Case B"); }
  @Test void testB_B_SI() { runSingleModeTest("bp-b", B_SI, "Test Case B"); }
  @Test void testB_B_RC() { runSingleModeTest("bp-b", B_RC, "Test Case B"); }
  @Test void testB_B_RU() { runSingleModeTest("bp-b", B_RU, "Test Case B"); }
  @Test void testB_C() { runSingleModeTest("bp-b", C, "Test Case B"); }
  @Test void testB_V() { runSingleModeTest("bp-b", V, "Test Case B"); }
  @Test void testB_P() { runSingleModeTest("bp-b", P, "Test Case B"); }

  // Test Case C - All Modes
  @Test void testC_B_SER() { runSingleModeTest("bp-c", B_SER, "Test Case C"); }
  @Test void testC_B_SI() { runSingleModeTest("bp-c", B_SI, "Test Case C"); }
  @Test void testC_B_RC() { runSingleModeTest("bp-c", B_RC, "Test Case C"); }
  @Test void testC_B_RU() { runSingleModeTest("bp-c", B_RU, "Test Case C"); }
  @Test void testC_C() { runSingleModeTest("bp-c", C, "Test Case C"); }
  @Test void testC_V() { runSingleModeTest("bp-c", V, "Test Case C"); }
  @Test void testC_P() { runSingleModeTest("bp-c", P, "Test Case C"); }

  // Test Case D - All Modes
  @Test void testD_B_SER() { runSingleModeTest("bp-d", B_SER, "Test Case D"); }
  @Test void testD_B_SI() { runSingleModeTest("bp-d", B_SI, "Test Case D"); }
  @Test void testD_B_RC() { runSingleModeTest("bp-d", B_RC, "Test Case D"); }
  @Test void testD_B_RU() { runSingleModeTest("bp-d", B_RU, "Test Case D"); }
  @Test void testD_C() { runSingleModeTest("bp-d", C, "Test Case D"); }
  @Test void testD_V() { runSingleModeTest("bp-d", V, "Test Case D"); }
  @Test void testD_P() { runSingleModeTest("bp-d", P, "Test Case D"); }

  // Test Case E - All Modes
  @Test void testE_B_SER() { runSingleModeTest("bp-e", B_SER, "Test Case E"); }
  @Test void testE_B_SI() { runSingleModeTest("bp-e", B_SI, "Test Case E"); }
  @Test void testE_B_RC() { runSingleModeTest("bp-e", B_RC, "Test Case E"); }
  @Test void testE_B_RU() { runSingleModeTest("bp-e", B_RU, "Test Case E"); }
  @Test void testE_C() { runSingleModeTest("bp-e", C, "Test Case E"); }
  @Test void testE_V() { runSingleModeTest("bp-e", V, "Test Case E"); }
  @Test void testE_P() { runSingleModeTest("bp-e", P, "Test Case E"); }

  // Test Case F - All Modes
  @Test void testF_B_SER() { runSingleModeTest("bp-f", B_SER, "Test Case F"); }
  @Test void testF_B_SI() { runSingleModeTest("bp-f", B_SI, "Test Case F"); }
  @Test void testF_B_RC() { runSingleModeTest("bp-f", B_RC, "Test Case F"); }
  @Test void testF_B_RU() { runSingleModeTest("bp-f", B_RU, "Test Case F"); }
  @Test void testF_C() { runSingleModeTest("bp-f", C, "Test Case F"); }
  @Test void testF_V() { runSingleModeTest("bp-f", V, "Test Case F"); }
  @Test void testF_P() { runSingleModeTest("bp-f", P, "Test Case F"); }

  // Test Case G - All Modes
  @Test void testG_B_SER() { runSingleModeTest("bp-g", B_SER, "Test Case G"); }
  @Test void testG_B_SI() { runSingleModeTest("bp-g", B_SI, "Test Case G"); }
  @Test void testG_B_RC() { runSingleModeTest("bp-g", B_RC, "Test Case G"); }
  @Test void testG_B_RU() { runSingleModeTest("bp-g", B_RU, "Test Case G"); }
  @Test void testG_C() { runSingleModeTest("bp-g", C, "Test Case G"); }
  @Test void testG_V() { runSingleModeTest("bp-g", V, "Test Case G"); }
  @Test void testG_P() { runSingleModeTest("bp-g", P, "Test Case G"); }

  // Test Case H - All Modes
  @Test void testH_B_SER() { runSingleModeTest("bp-h", B_SER, "Test Case H"); }
  @Test void testH_B_SI() { runSingleModeTest("bp-h", B_SI, "Test Case H"); }
  @Test void testH_B_RC() { runSingleModeTest("bp-h", B_RC, "Test Case H"); }
  @Test void testH_B_RU() { runSingleModeTest("bp-h", B_RU, "Test Case H"); }
  @Test void testH_C() { runSingleModeTest("bp-h", C, "Test Case H"); }
  @Test void testH_V() { runSingleModeTest("bp-h", V, "Test Case H"); }
  @Test void testH_P() { runSingleModeTest("bp-h", P, "Test Case H"); }

  // Test Case I - All Modes
  @Test void testI_B_SER() { runSingleModeTest("bp-i", B_SER, "Test Case I"); }
  @Test void testI_B_SI() { runSingleModeTest("bp-i", B_SI, "Test Case I"); }
  @Test void testI_B_RC() { runSingleModeTest("bp-i", B_RC, "Test Case I"); }
  @Test void testI_B_RU() { runSingleModeTest("bp-i", B_RU, "Test Case I"); }
  @Test void testI_C() { runSingleModeTest("bp-i", C, "Test Case I"); }
  @Test void testI_V() { runSingleModeTest("bp-i", V, "Test Case I"); }
  @Test void testI_P() { runSingleModeTest("bp-i", P, "Test Case I"); }

  // Test Case J - All Modes
  @Test void testJ_B_SER() { runSingleModeTest("bp-j", B_SER, "Test Case J"); }
  @Test void testJ_B_SI() { runSingleModeTest("bp-j", B_SI, "Test Case J"); }
  @Test void testJ_B_RC() { runSingleModeTest("bp-j", B_RC, "Test Case J"); }
  @Test void testJ_B_RU() { runSingleModeTest("bp-j", B_RU, "Test Case J"); }
  @Test void testJ_C() { runSingleModeTest("bp-j", C, "Test Case J"); }
  @Test void testJ_V() { runSingleModeTest("bp-j", V, "Test Case J"); }
  @Test void testJ_P() { runSingleModeTest("bp-j", P, "Test Case J"); }

  // Test Case K - All Modes
  @Test void testK_B_SER() { runSingleModeTest("bp-k", B_SER, "Test Case K"); }
  @Test void testK_B_SI() { runSingleModeTest("bp-k", B_SI, "Test Case K"); }
  @Test void testK_B_RC() { runSingleModeTest("bp-k", B_RC, "Test Case K"); }
  @Test void testK_B_RU() { runSingleModeTest("bp-k", B_RU, "Test Case K"); }
  @Test void testK_C() { runSingleModeTest("bp-k", C, "Test Case K"); }
  @Test void testK_V() { runSingleModeTest("bp-k", V, "Test Case K"); }
  @Test void testK_P() { runSingleModeTest("bp-k", P, "Test Case K"); }

  // Test Case L - All Modes
  @Test void testL_B_SER() { runSingleModeTest("bp-l", B_SER, "Test Case L"); }
  @Test void testL_B_SI() { runSingleModeTest("bp-l", B_SI, "Test Case L"); }
  @Test void testL_B_RC() { runSingleModeTest("bp-l", B_RC, "Test Case L"); }
  @Test void testL_B_RU() { runSingleModeTest("bp-l", B_RU, "Test Case L"); }
  @Test void testL_C() { runSingleModeTest("bp-l", C, "Test Case L"); }
  @Test void testL_V() { runSingleModeTest("bp-l", V, "Test Case L"); }
  @Test void testL_P() { runSingleModeTest("bp-l", P, "Test Case L"); }

  // Test Case L - All Modes
  @Test void testM_B_SER() { runSingleModeTest("bp-m", B_SER, "Test Case M"); }
  @Test void testM_B_SI() { runSingleModeTest("bp-m", B_SI, "Test Case M"); }
  @Test void testM_B_RC() { runSingleModeTest("bp-m", B_RC, "Test Case M"); }
  @Test void testM_B_RU() { runSingleModeTest("bp-m", B_RU, "Test Case M"); }
  @Test void testM_C() { runSingleModeTest("bp-m", C, "Test Case M"); }
  @Test void testM_V() { runSingleModeTest("bp-m", V, "Test Case M"); }
  @Test void testM_P() { runSingleModeTest("bp-m", P, "Test Case M"); }

  @Test void testN_B_SER() { runSingleModeTest("bp-n", B_SER, "Test Case N"); }
  @Test void testN_B_SI() { runSingleModeTest("bp-n", B_SI, "Test Case N"); }
  @Test void testN_B_RC() { runSingleModeTest("bp-n", B_RC, "Test Case N"); }
  @Test void testN_B_RU() { runSingleModeTest("bp-n", B_RU, "Test Case N"); }
  @Test void testN_C() { runSingleModeTest("bp-n", C, "Test Case N"); }
  @Test void testN_V() { runSingleModeTest("bp-n", V, "Test Case N"); }
  @Test void testN_P() { runSingleModeTest("bp-n", P, "Test Case N"); }

  // Test Case O - All Modes
  @Test void testO_B_SER() { runSingleModeTest("bp-o", B_SER, "Test Case O"); }
  @Test void testO_B_SI() { runSingleModeTest("bp-o", B_SI, "Test Case O"); }
  @Test void testO_B_RC() { runSingleModeTest("bp-o", B_RC, "Test Case O"); }
  @Test void testO_B_RU() { runSingleModeTest("bp-o", B_RU, "Test Case O"); }
  @Test void testO_C() { runSingleModeTest("bp-o", C, "Test Case O"); }
  @Test void testO_V() { runSingleModeTest("bp-o", V, "Test Case O"); }
  @Test void testO_P() { runSingleModeTest("bp-o", P, "Test Case O"); }
}
