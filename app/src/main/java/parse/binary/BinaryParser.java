package parse.binary;

import common.binaryhistory.LOG_TXN;
import common.binaryhistory.SessionHistory;
import history.KVHistory;
import history.KVTxn;
import java.io.File;
import java.util.Map;
import parse.Parser;
import util.Config;
import util.Context;
import util.MapFactory;
import util.Profiler;

/**
 *
 */
public class BinaryParser implements Parser {
  private Config cfg;

  public BinaryParser(Config cfg) {
    this.cfg = cfg;
  }

//  public static void main(String[] args) {
//    BinaryParser parser = new BinaryParser();
//    String folder = "/home/windkl/viper_logs" +
//        "/100txn_4op_4threads_I0_D10_R20_U0_RANGEE10_Isolation0_2024_02_08_17_17_15";
//    parser.parse(folder, false);
//  }

  @Override
  public KVHistory parse(String folder) {
    File dir = new File(folder);
    File[] logFiles = dir.listFiles();

    Map<Integer, SessionHistory> tid2SessionTxns = MapFactory.getEmptyMap(cfg.DEBUG);
    KVHistory history = new KVHistory(cfg);
    KVTxn finalTxn = null; // Defer final txn until after all regular txns

    if (logFiles != null) {
      for (File logFile : logFiles) {
        // if this file is not log file, skip
        if (!logFile.getName().startsWith("J") || !logFile.getName().endsWith(".log")) {
          continue;
        }

        // load the binary file and create `SessionHistory` object.
        SessionHistory sessionHistory = SessionHistory.loadFromFile(logFile);
        String fileName = logFile.getName();
        int threadId = Integer.parseInt(fileName.substring(1, fileName.length() - 4));
        tid2SessionTxns.put(threadId, sessionHistory);
      }

      history.setTid2SessionTxns(tid2SessionTxns);

      // try to combine multiple session history as a History
      for (var entry : tid2SessionTxns.entrySet()) {
        int sessionId = entry.getKey();
//        System.out.println(String.format("parsing log %d", sessionId));
        SessionHistory sessionHistory = tid2SessionTxns.get(sessionId);

        int numTxns = sessionHistory.size();
        for (int i = 0; i < numTxns; i++) {
//          System.out.println(String.format("parsing txn %d", i));
          LOG_TXN logTxn = sessionHistory.getKthTxn(i);
          KVTxn txn = KVTxn.fromLogTxn(sessionId, logTxn, cfg.DEBUG);

          if (txn.isInitial()) {
            history.setInitialTxn(txn);
          } else if (txn.isFinal()) {
            // Defer final txn to ensure it's added last
            if (finalTxn != null) {
              System.err.println("WARNING: Multiple final transactions detected. Using the last one.");
            }
            finalTxn = txn;
          } else {
            history.addRegularTxn(sessionId, txn);
          }
        }
      }

      // Add final transaction at the end, after all regular transactions
      if (finalTxn != null) {
        history.setFinalTxn(finalTxn);
      }
    } else {
      // Handle the case where dir is not really a directory.
      // Checking dir.isDirectory() above would not be sufficient
      // to avoid race conditions with another process that deletes
      // directories.
      System.err.println("Cannot find logs");
      System.exit(-1);
    }

//    System.out.println(String.format("Finish parsing"));
    return history;
  }

  @Override
  public KVHistory apply(Context ctx, String s) {
    Profiler profiler = Profiler.getInstance();
    profiler.startTick("parsing");
    KVHistory history = this.parse(s);
    profiler.endTick("parsing");

    return history;
  }
}
