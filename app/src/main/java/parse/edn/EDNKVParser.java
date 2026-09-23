package parse.edn;

import static parse.LogsLoader.preProcess;
import static us.bpsm.edn.Keyword.newKeyword;
import static us.bpsm.edn.parser.Parsers.defaultConfiguration;

import common.Codec;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import parse.LogsLoader;
import parse.Parser;
import us.bpsm.edn.parser.Parseable;
import us.bpsm.edn.parser.Parsers;
import util.Config;
import util.Context;
import util.Profiler;

public class EDNKVParser implements Parser {
  private final Config cfg;

  public EDNKVParser(Config cfg) {
    this.cfg = cfg;
  }

  /**
   * @param history_folder
   * @return
   * @throws IOException
   */
  @Override
  public KVHistory parse(String history_folder) {
    return parseWithTruncation(history_folder, Integer.MAX_VALUE);
  }

  private KVTxn buildTransaction(List<?> mops, int threadId, int index) {
    // for jepsen's logs, we have only postCommitTS
    // temporary method
    KVTxn txn = KVTxn.createRegular(threadId, false);
    txn.setIndex(index);

    // add operatiosn to this txn
    for (Object mop : mops) {
      List<?> unparsedMop = (List<?>) mop;

      var opType = unparsedMop.get(0);

      if (opType == newKeyword("r")) {
        addReadOperation(txn, unparsedMop);
      } else if (opType == newKeyword("w")) {
        addWriteOperation(txn, unparsedMop);
      } else {
        throw new RuntimeException("Unsupported operation type: " + opType);
      }
    }

    return txn;
  }

  private void addReadOperation(KVTxn txn, List<?> unparsedMop) {
    if (unparsedMop.size() < 3) {
      throw new IllegalArgumentException("Malformed read operation: " + unparsedMop);
    }

    long keyLong = asLong(unparsedMop.get(1));
    var key = Codec.encodeLong(keyLong);
    Object valueObj = unparsedMop.get(2);
    Key valueKey;

    if (valueObj == null) {
      valueKey = Key.getNullKey();
    } else if (valueObj instanceof List<?>) {
      valueKey = encodeLongList((List<?>) valueObj);
    } else {
      valueKey = Codec.encodeLong(asLong(valueObj));
    }

    txn.addRead(key, valueKey);
  }

  private void addWriteOperation(KVTxn txn, List<?> unparsedMop) {
    if (unparsedMop.size() < 3) {
      throw new IllegalArgumentException("Malformed write operation: " + unparsedMop);
    }

    long keyLong = asLong(unparsedMop.get(1));
    Object valueObj = unparsedMop.get(2);
    var key = Codec.encodeLong(keyLong);
    Key valueKey;

    if (valueObj == null) {
      valueKey = Key.getNullKey();
    } else if (valueObj instanceof List<?>) {
      valueKey = encodeLongList((List<?>) valueObj);
    } else {
      valueKey = Codec.encodeLong(asLong(valueObj));
    }

    txn.addWrite(key, valueKey);
  }

  private long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }

  private Key encodeLongList(List<?> values) {
    if (values.isEmpty()) {
      return Key.getNullKey();
    }

    long[] longs = new long[values.size()];
    for (int i = 0; i < values.size(); i++) {
      longs[i] = asLong(values.get(i));
    }
    return new Key(Codec.encodeMultipleLong(longs));
  }

  private KVHistory parseWithTruncation(String logDir,
      int numTxnsWanted) {
    String inputFileName = "history.edn";
    Path preprocessedPath = Paths.get(logDir, "history_preprocessed.edn");
    Path historyPath = Paths.get(logDir, inputFileName);

    if (Files.notExists(preprocessedPath)) {
      if (Files.exists(historyPath)) {
        try {
          EDNPreprocessing.preprocessHistoryEdn(logDir, inputFileName, preprocessedPath.toString());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        throw new RuntimeException(
            "Missing EDN history. Expected either " + historyPath + " or " + preprocessedPath);
      }
    }

    KVHistory history = new KVHistory(cfg);
    int numTxnsAdded = 0;

    String text = LogsLoader.loadLogsFromFileWithoutPreProcessing(preprocessedPath.toString());
    // filter out failed txns
    List<String> successTxns = Arrays.asList(text.split("\n"));
    // We have already extracted sucessful txns, but to use the edn parser lib, we
    // need to convert it into a String.
    text = String.join("\n", successTxns);
    text = preProcess(text);
    Parseable pbr = Parsers.newParseable(text);
    us.bpsm.edn.parser.Parser p = Parsers.newParser(defaultConfiguration());
    List<Map<?, ?>> txns = (List<Map<?, ?>>) p.nextValue(pbr);

    Map<Integer, Integer> sessionId2Index = new HashMap<>();

    for (Map unparsedTxn : txns) {
      // Assert that the transaction is successful
      assert unparsedTxn.get(newKeyword("type")) == newKeyword("ok");
      List mops = (List) unparsedTxn.get(newKeyword("value"));
      int sessionId = ((Long) unparsedTxn.get(newKeyword("process"))).intValue();
      // int index = ((Long) unparsedTxn.get(newKeyword("index"))).intValue();
      int index = sessionId2Index.getOrDefault(sessionId, 0);
      KVTxn txn = buildTransaction(mops, sessionId, index);
      sessionId2Index.put(sessionId, index + 1);
      history.addRegularTxn(sessionId, txn);

      numTxnsAdded++;
      if (numTxnsAdded + 1 >= numTxnsWanted) {
        break;
      }
    }

    return history;
  }

  /**
   * Applies this function to the given arguments.
   *
   * @param ctx the first function argument
   * @param s   the second function argument
   * @return the function result
   */
  @Override
  public KVHistory apply(Context ctx, String s) {
    Profiler profiler = Profiler.getInstance();
    profiler.startTick("parsing");
    KVHistory history = this.parse(s);
    profiler.endTick("parsing");
    return history;
  }
}
