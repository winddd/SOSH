package parse.plume;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import common.Codec;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import history.op.PutOp;
import history.op.ReadOp;
import lombok.SneakyThrows;
import parse.Parser;
import util.Config;
import util.Context;
import util.Profiler;
import util.exception.InvalidInputException;

public class PlumeParser implements Parser {
  private static final Pattern regex = Pattern.compile("([rw])\\((\\d++),(\\d++),(\\d++),(-?\\d++)\\)");
  private BiMap<Long, Integer> txnMap = HashBiMap.create();
  private Config cfg;
  private Map<Long, String> keyMap = new HashMap<>();

  public PlumeParser(Config cfg) {
    this.cfg = cfg;
  }

  @Override
  @SneakyThrows
  public KVHistory parse(String folder) {
    Path path = Paths.get(folder);
    if (!Files.exists(path)) {
      throw new InvalidInputException("Path does not exist: " + folder);
    }
    if (Files.isDirectory(path)) {
      throw new InvalidInputException("Plume traces should be in a single file, not a directory");
    }
    if (!Files.isRegularFile(path)) {
      throw new InvalidInputException("Path is not a regular file: " + folder);
    }

    var history = new KVHistory(cfg);
    var initWrites = new HashMap<Key, Key>();
    String line = null;
    try (var br = new BufferedReader(new FileReader(folder))) {
      while ((line = br.readLine()) != null) {
        var match = regex.matcher(line);
        if (!match.matches()) {
          throw new Error("Invalid format");
        }

        var op = match.group(1);
        var keyLong = Long.parseLong(match.group(2));
        var key = Codec.encodeLong(keyLong);
        if (!keyMap.containsKey(keyLong)) {
          keyMap.put(keyLong, key.toString());
        }
        Long valLong = Long.parseLong(match.group(3));
        Key value = null;
        var sessionId = Long.parseLong(match.group(4));
        var originalTxnId = Long.parseLong(match.group(5));
        // txn == -1 => aborted
        if (originalTxnId == -1) {
          continue;
        }

        switch (op) {
          case "r": {
            if (valLong.equals(0L)) {
              value = Key.getNullKey();
              initWrites.put(key, value);
            } else {
              value = Codec.encodeLong(valLong);
            }
          } break;
          case "w": {
            value = Codec.encodeLong(valLong);
          } break;
          default:
            throw new InvalidInputException("Not supported operation");
        }

        KVTxn txn = null;
        // retrieve existing txn or create a new one
        boolean isSeenTxn = txnMap.containsKey(originalTxnId);
        if (isSeenTxn) {
          txn = history.getKthTxn(txnMap.get(originalTxnId));
        } else {
          txn = KVTxn.createRegular(sessionId, cfg.DEBUG);
        }

        txn.addOperations(op.equals("r") ? new ReadOp(key, value) : new PutOp(key, value));

        if (!isSeenTxn) {
          history.addRegularTxn(sessionId, txn);
          txnMap.put(originalTxnId, txn.getTxnId());
        }
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Plume trace file not found: " + folder, e);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read Plume trace file: " + folder, e);
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
        pw.println("Plume key: Boomslang key");
        for (var p : keyMap.entrySet()) {
          pw.println(p.getKey() + ": " + p.getValue());
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to write key map", e);
      }

      // dump txnMap
      try (var pw = new PrintWriter(new FileWriter("txn_map.txt"))) {
        pw.println("Plume txn: Boomslang txn");
        for (var p : txnMap.entrySet()) {
          pw.println(p.getKey() + ": " + p.getValue());
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to write txn map", e);
      }
    }

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
