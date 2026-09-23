package parse.tapir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import common.Codec;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import history.op.KvOperation;
import history.op.PutOp;
import history.op.ReadOp;
import lombok.SneakyThrows;
import parse.Parser;
import parse.ParserUtils;
import util.Config;
import util.Context;
import util.Profiler;

public class TapirHistoryParser implements Parser {
  private BiMap<Long, Integer> txnMap = HashBiMap.create();
  private Map<String, String> keyMap = new HashMap<>();
  private Config cfg;

  public TapirHistoryParser(Config cfg) {
    this.cfg = cfg;
  }

  public static boolean isLong(String str) {
    try {
      Long.parseLong(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public static Key encodeString(String s) {
    if (s == null)
      return Key.getNullKey();
    return new Key(s.getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  @SneakyThrows
  public KVHistory parse(String folder) {
    var history = new KVHistory(cfg);
    var files = ParserUtils.findLogs(folder, "T", ".log");
    var initWrites = new HashMap<Key, Key>();
    for (File file : files) {
      extractLog(folder, file, history, initWrites);
    }

    // process T_0
    var initTxn = history.getKthTxn(0);
    initTxn.setState(initWrites);
    for (var p : initWrites.entrySet()) {
      initTxn.addOperations(new PutOp(p.getKey(), p.getValue()));
    }
    initTxn.setStatus(KVTxn.TransactionStatus.COMMIT);

    if (cfg.DEBUG) {
      // dump key map
      try (var pw = new PrintWriter(new FileWriter("key_map.txt"))) {
        pw.println("Tapir key: Boomslang key");
        for (var p : keyMap.entrySet()) {
          pw.println(p.getKey() + ": " + p.getValue());
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

      // dump original txn id and new txn id mapping
      String fileName = "txn_map.txt";
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
        for(var oldTxnId: txnMap.keySet()) {
          writer.write(oldTxnId.toString());
          writer.write(": ");
          writer.write(txnMap.get(oldTxnId).toString());
          writer.newLine();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return history;
  }

  /**
   * Extract the transactions in `file` and add them to `history`.
   * @param folder
   * @param file
   * @param history
   * @param initWrites
   */
  @SneakyThrows
  public void extractLog(String folder,
                         File file,
                         KVHistory history,
                         Map<Key, Key> initWrites) {
    var fileName = file.getName();
    // T55.log
    long threadId = Long.parseLong(fileName.substring(1, fileName.length() - 4));

    KVTxn current = null;
    try (BufferedReader br = new BufferedReader(new FileReader(Paths.get(folder, fileName).toString()))) {
      String line;

      while ((line = br.readLine()) != null) {
        char event = line.charAt(0);

        switch (event) {
          case 'b': // begin
          case 'c': { // commit
            // begin/commit timestamps
            // e.g., b(773913498450,1743195162971890)
            String[] tmp = line.substring(2, line.length() - 1).split(",");
//            assert tmp.length == 3;
            long originalTxnId = Long.parseLong(tmp[0]);
            long timestamp = Long.parseLong(tmp[1]);

            if (event == 'b') {
              assert current == null && !txnMap.containsKey(originalTxnId);
              current = KVTxn.createRegular(threadId, false);
              current.setBeginTS(timestamp);
              current.setStatus(KVTxn.TransactionStatus.ONGOING);
              // NOTE: `addRegularTxn` will assign a txnId to `current`.
              history.addRegularTxn(threadId, current);
              txnMap.put(originalTxnId, current.getTxnId());
            } else if (event == 'c') {
              assert current != null && current.getTxnId() == txnMap.get(originalTxnId);
              current.setStatus(KVTxn.TransactionStatus.COMMIT);
              current.setCommitTS(timestamp);
              if (tmp.length >= 3)
                current.setTxnTS(Long.parseLong(tmp[2]));
              current = null;
            }
            break;
          }
          case 'r':
          case 'w': {
            // read/write operations
            // e.g., r(10439084,16238331074,566183564115,566183564117)
            String[] tmp = line.substring(2, line.length() - 1).split(",");
            assert tmp.length == 3;
            Key key, val;
            if (isLong(tmp[0])) { // key is a number
              var keyLong = Long.parseLong(tmp[0]);
              key = Codec.encodeLong(keyLong);
            } else { // key is string
              key = encodeString(tmp[0]);
            }
            keyMap.put(tmp[0], key.toString());

            var valueStr = tmp[1];
            if (valueStr.equals("null")) {
              val = Key.getNullKey();
              initWrites.put(key, val);
            } else {
              if (isLong(valueStr)) {
                val = Codec.encodeLong(Long.parseLong(valueStr));
              } else {
                val = encodeString(valueStr);
              }
            }
            Long originalTxnId = Long.parseLong(tmp[2]);
            assert originalTxnId != null;
//            if (current.getTxnId() == 2079) {
//              System.out.println();
//            }
            assert current != null && current.getTxnId() == txnMap.get(originalTxnId);

            // use writeId as value because cobra guarantees its uniqueness
            KvOperation op = null;
            if (event == 'r') {
              op = new ReadOp(key, val);
            } else {
              op = new PutOp(key, val);
            }

            current.addOperations(op);
            break;
          }
        }
      }
    }
  }

  @Override
  public KVHistory apply(Context context, String s) {
    Profiler profiler = Profiler.getInstance();
    profiler.startTick("parsing");
    KVHistory history = this.parse(s);
    profiler.endTick("parsing");
    return history;
  }
}
