package parse.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import history.KVHistory;
import history.KVTxn;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import parse.Parser;
import util.Config;
import util.Context;
import util.Profiler;
import parse.LogsLoader;

public class JsonKvParser implements Parser {
  private Config cfg;

  public JsonKvParser(Config cfg) {
    this.cfg = cfg;
  }
  /**
   * reserve only the first `numTxn` transactions.
   *
   * @param logPath
   * @return
   * @throws IOException
   */
  public KVHistory parse(String logPath) {
    KVHistory history = new KVHistory(cfg);
    if (!cfg.LOADFROMSINGLEFILE) { // load from a folder
      List<Pair<Integer, String>> logFiles = LogsLoader.loadJsonLogsFromFolder(logPath);
      List<ThreadHistoryPOJO> rawLogs = new ArrayList<>();
      Gson gson = new Gson();
      Type type = new TypeToken<List<TransactionPOJO>>() {
      }.getType();

      // traverse each log file
      for (var tid2txns : logFiles) {
        int threadId = tid2txns.getLeft();
        String text = tid2txns.getRight();
        List<TransactionPOJO> txns = gson.fromJson(text, type);

        ThreadHistoryPOJO sessionLog =
            new ThreadHistoryPOJO(threadId, txns);
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
            history.addRegularTxn(threadId, txn);
          }
        }
      }
    } else { // load from a single file, keep this for comparison with other checking tools.
      String text = LogsLoader.loadLogsFromFile(logPath);
      Gson gson = new Gson();
      Type collectionType = new TypeToken<List<TransactionPOJO>>() {}.getType();
      List<TransactionPOJO> txnPOJOS = gson
          .fromJson(text, collectionType);

      for (TransactionPOJO txnPOJO : txnPOJOS) {
        KVTxn txn = txnPOJO.toTransaction(-1, cfg.DEBUG);
        // TODO: use the client info in jepsen logs. Currently, assume this info is unknown
        // If we need to support strong session SI, this must be supported.
        history.addRegularTxn(-1, txn);
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
}
