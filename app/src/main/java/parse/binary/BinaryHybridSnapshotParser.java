package parse.binary;

import common.Key;
import common.binaryhistory.LOG_TXN;
import common.binaryhistory.SessionHistory;
import history.KVHistory;
import history.KVTxn;
import history.op.*;
import parse.Parser;
import util.Config;
import util.Context;
import util.MapFactory;
import util.Profiler;

import java.io.File;
import java.util.*;

/**
 * Binary parser with hybrid snapshot epoch splitting.
 *
 * This parser reads binary transaction logs and splits each physical transaction
 * into multiple mini-transactions (epochs) following hybrid snapshot semantics
 * where transactions mix snapshot reads with current reads (as used in MySQL, MariaDB, TiDB, etc.).
 *
 * @see parse.json.JsonHybridSnapshotParser for the JSON version
 */
public class BinaryHybridSnapshotParser implements Parser {
  private Config cfg;

  public BinaryHybridSnapshotParser(Config cfg) {
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
            // Split the physical transaction into mini-transactions (epochs)
            List<KVTxn> epochs = splitIntoEpochs(txn, sessionId);
            for (KVTxn epoch : epochs) {
              history.addRegularTxn(sessionId, epoch);
            }
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

  /**
   * Splits a physical transaction into mini-transactions (epochs) following hybrid snapshot
   * semantics with per-statement current reads.
   *
   * Rules:
   * - E0 (base epoch): Contains Get operations that don't read from prior writes
   * - Each current-read operation (GetForUpdate, Put, Delete, RangeForUpdate) creates a new epoch
   * - Get operations attach to the most recent epoch that wrote the key, or E0 if none
   * - Snapshot range queries (forUpdate=false) are NOT supported and will throw an exception
   *
   * @param physicalTxn the physical transaction to split
   * @param sessionId the session/thread ID
   * @return list of mini-transactions in ascending order
   * @throws IllegalArgumentException if a snapshot range query is encountered
   */
  private List<KVTxn> splitIntoEpochs(KVTxn physicalTxn, int sessionId) {
    List<KVTxn> epochs = new ArrayList<>();

    // E0: base epoch (snapshot reads that don't read from writes in this transaction)
    KVTxn e0 = KVTxn.createRegular(sessionId, cfg.DEBUG);
    epochs.add(e0);

    // Track which keys have been written and in which epoch
    // Key -> epoch index in the epochs list
    Map<Key, Integer> keyToWriteEpoch = new HashMap<>();

    // Process each operation in program order
    for (KvOperation op : physicalTxn.getMops()) {
      KvOpType opType = op.getOpType();

      switch (opType) {
        case READ: {
          ReadOp readOp = (ReadOp) op;
          Key key = readOp.getKey();

          if (readOp.isForUpdate()) {
            // GetForUpdate: creates a new epoch with this read
            KVTxn newEpoch = KVTxn.createRegular(sessionId, cfg.DEBUG);
            newEpoch.addOperations(readOp);
            epochs.add(newEpoch);
          } else {
            // Plain Get: attach to the most recent epoch that wrote this key, or E0
            Integer writeEpochIdx = keyToWriteEpoch.get(key);
            if (writeEpochIdx != null) {
              epochs.get(writeEpochIdx).addOperations(readOp);
            } else {
              e0.addOperations(readOp);
            }
          }
          break;
        }

        case PUT: {
          PutOp putOp = (PutOp) op;
          Key key = putOp.getKey();

          // Put creates a new epoch and writes the key
          KVTxn newEpoch = KVTxn.createRegular(sessionId, cfg.DEBUG);
          newEpoch.addOperations(putOp);
          int epochIdx = epochs.size();
          epochs.add(newEpoch);

          // Track that this key was written in this epoch
          keyToWriteEpoch.put(key, epochIdx);
          break;
        }

        case DELETE: {
          DeleteOp deleteOp = (DeleteOp) op;
          Key key = deleteOp.getKey();

          // Delete creates a new epoch and writes the key
          KVTxn newEpoch = KVTxn.createRegular(sessionId, cfg.DEBUG);
          newEpoch.addOperations(deleteOp);
          int epochIdx = epochs.size();
          epochs.add(newEpoch);

          // Track that this key was written in this epoch
          keyToWriteEpoch.put(key, epochIdx);
          break;
        }

        case RANGE: {
          RangeOp rangeOp = (RangeOp) op;

          // Validate that this is a RangeForUpdate (forUpdate=true)
          if (!rangeOp.isForUpdate()) {
            throw new IllegalArgumentException(
                "Snapshot range queries (forUpdate=false) are NOT supported in hybrid snapshot parser. " +
                "Only RangeForUpdate operations are allowed.");
          }

          // RangeForUpdate creates a new epoch
          KVTxn newEpoch = KVTxn.createRegular(sessionId, cfg.DEBUG);
          newEpoch.addOperations(rangeOp);
          epochs.add(newEpoch);
          break;
        }

        case ITER: {
          // ITER is treated similarly to RANGE
          throw new IllegalArgumentException(
              "ITER operations are not supported in hybrid snapshot parser. Use RANGE_FOR_UPDATE instead.");
        }

        case BEGIN:
        case COMMIT:
        case ABORT:
          // Transaction control operations - skip them in epoch splitting
          // They don't participate in dependency analysis
          break;

        default:
          throw new IllegalArgumentException("Unsupported operation type: " + opType);
      }
    }

    return epochs;
  }
}
