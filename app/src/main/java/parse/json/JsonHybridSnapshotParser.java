package parse.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import history.op.*;
import java.lang.reflect.Type;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;
import parse.LogsLoader;
import parse.Parser;
import util.Config;
import util.Context;
import util.Profiler;

/**
 * JSON parser with hybrid snapshot epoch splitting.
 *
 * This parser reads JSON transaction logs and splits each physical transaction
 * into multiple mini-transactions (epochs) following hybrid snapshot semantics
 * where transactions mix snapshot reads with current reads (as used in MySQL, MariaDB, TiDB, etc.).
 *
 * @see parse.binary.BinaryTidbParser for the binary version
 */
public class JsonHybridSnapshotParser implements Parser {
  private Config cfg;

  public JsonHybridSnapshotParser(Config cfg) {
    this.cfg = cfg;
  }

  /**
   * Parse JSON logs and split transactions into epochs.
   *
   * @param logPath path to the log folder or file
   * @return KVHistory with epoch-split transactions
   */
  public KVHistory parse(String logPath) {
    KVHistory history = new KVHistory(cfg);

    if (!cfg.LOADFROMSINGLEFILE) { // load from a folder
      List<Pair<Integer, String>> logFiles = LogsLoader.loadJsonLogsFromFolder(logPath);
      List<ThreadHistoryPOJO> rawLogs = new ArrayList<>();
      Gson gson = new Gson();
      Type type = new TypeToken<List<TransactionPOJO>>() {}.getType();

      // traverse each log file
      for (var tid2txns : logFiles) {
        int threadId = tid2txns.getLeft();
        String text = tid2txns.getRight();
        List<TransactionPOJO> txns = gson.fromJson(text, type);

        ThreadHistoryPOJO sessionLog = new ThreadHistoryPOJO(threadId, txns);
        rawLogs.add(sessionLog);
      }

      for (var threadHistoryPOJO : rawLogs) {
        int threadId = threadHistoryPOJO.getTid();

        for (TransactionPOJO txnPOJO : threadHistoryPOJO.getTxns()) {
          KVTxn txn = txnPOJO.toTransaction(threadId, cfg.DEBUG);

          if (txn.isInitial()) {
            history.setInitialTxn(txn);
          } else {
            // If this is an aborted txn
            if (!txn.isCommitted()) {
              continue;
            }

            // Split the physical transaction into mini-transactions (epochs)
            List<KVTxn> epochs = splitIntoEpochs(txn, threadId);
            for (KVTxn epoch : epochs) {
              history.addRegularTxn(threadId, epoch);
            }
          }
        }
      }
    } else { // load from a single file
      String text = LogsLoader.loadLogsFromFile(logPath);
      Gson gson = new Gson();
      Type collectionType = new TypeToken<List<TransactionPOJO>>() {}.getType();
      List<TransactionPOJO> txnPOJOS = gson.fromJson(text, collectionType);

      for (TransactionPOJO txnPOJO : txnPOJOS) {
        KVTxn txn = txnPOJO.toTransaction(-1, cfg.DEBUG);

        // Split the physical transaction into mini-transactions (epochs)
        List<KVTxn> epochs = splitIntoEpochs(txn, -1);
        for (KVTxn epoch : epochs) {
          history.addRegularTxn(-1, epoch);
        }
      }
    }

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
