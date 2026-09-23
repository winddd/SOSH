package parse.timekiller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import common.Codec;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import history.op.ReadOp;
import history.op.PutOp;
import parse.Parser;
import util.Config;
import util.Context;
import util.Profiler;
import util.exception.InvalidInputException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class TimekillerParser implements Parser {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private Config cfg;
  private BiMap<Long, Integer> txnMap = HashBiMap.create();

  public TimekillerParser(Config cfg) {
    this.cfg = cfg;
  }

  @Override
  public KVHistory parse(String folder) {
    Path path = Paths.get(folder);
    if (!Files.exists(path)) {
      throw new InvalidInputException("Path does not exist: " + folder);
    }
    if (Files.isDirectory(path)) {
      throw new InvalidInputException("Timekiller traces should be in a single file, not a directory");
    }
    if (!Files.isRegularFile(path)) {
      throw new InvalidInputException("Path is not a regular file: " + folder);
    }

    // Create a history
    var history = new KVHistory(cfg);
    var initWrites = new HashMap<Key, Key>();

    try (var br = new BufferedReader(new FileReader(folder))) {
      String line = br.readLine();
      if (line == null) {
        throw new InvalidInputException("Empty trace file");
      }

      JsonNode root = objectMapper.readTree(line);
      if (!root.isArray()) {
        throw new InvalidInputException("Trace file should contain a JSON array of transactions");
      }

      for (JsonNode txnNode : root) {
        // Parse transaction metadata
        long sessionId = Long.parseLong(txnNode.get("sid").asText());
        long originalTxnId = Long.parseLong(txnNode.get("tid").asText());

        // Create new transaction
        var txn = KVTxn.createRegular(sessionId, cfg.DEBUG);

        // Parse operations
        JsonNode ops = txnNode.get("ops");
        if (!ops.isArray()) {
          throw new InvalidInputException("Operations should be an array");
        }

        for (JsonNode op : ops) {
          String type = op.get("t").asText();
          long key = op.get("k").asLong();
          Key keyObj = Codec.encodeLong(key);

          switch (type) {
            case "r": {
              Key value = null;
//              assert op.has("v");
              if (op.has("v")) {
                if (op.get("v").equals("null")) {
                  value = Key.getNullKey();
                  initWrites.put(keyObj, value);
                } else {
                  value = Codec.encodeLong(op.get("v").asLong());
                }
              } else {
//                value = Key.getNullKey();
//                initWrites.put(keyObj, value);
                continue;
              }
              txn.addOperations(new ReadOp(keyObj, value));
              break;
            }
            case "w": {
              if (!op.has("v")) {
                throw new InvalidInputException("Write operation must have a value");
              }
              Key value = Codec.encodeLong(op.get("v").asLong());
              txn.addOperations(new PutOp(keyObj, value));
              break;
            }
            default:
              throw new InvalidInputException("Unsupported operation type: " + type);
          }
        }

        history.addRegularTxn(sessionId, txn);
        txnMap.put(originalTxnId, txn.getTxnId());
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Timekiller trace file not found: " + folder, e);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read Timekiller trace file: " + folder, e);
    }

    // Process T_0
    var initTxn = history.getKthTxn(0);
    initTxn.setState(initWrites);
    for (var p : initWrites.entrySet()) {
      initTxn.addOperations(new PutOp(p.getKey(), p.getValue()));
    }
    initTxn.setStatus(KVTxn.TransactionStatus.COMMIT);
    return history;
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
