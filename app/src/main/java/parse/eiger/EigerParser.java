package parse.eiger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

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
import util.Config;
import util.Context;
import util.Profiler;
import util.exception.InvalidInputException;

public class EigerParser implements Parser {
  private static final Pattern THREAD_ID_PATTERN = Pattern.compile("Thread ID: (\\d+)");
  private static final Pattern KEY_PATTERN = Pattern.compile("Key: (\\d+)");
  private static final Pattern COLUMN_PATTERN = Pattern.compile("Column: C(\\d+), Value: ([a-f0-9]+)");
  private static final int NUM_COLUMNS = 1;
  private Config cfg;
  private KVHistory history;
  private BiMap<Long, Integer> txnMap = HashBiMap.create();
  private Map<Long, String> keyMap = new HashMap<>();
  private Map<String, String> valueMap = new HashMap<>();

  public EigerParser(Config cfg) {
    this.cfg = cfg;
  }

  public static Key encodeString(String s) {
    if (s == null)
      return Key.getNullKey();
    return new Key(s.getBytes(StandardCharsets.US_ASCII));
  }

  private void processInsertOperation(BufferedReader br, Map<Key, Key> initWrites) throws IOException {
    String line;
    KVTxn currentTxn = null;
    int threadId = -1;
    // Read keys and their columns
    while ((line = br.readLine()) != null && !line.isEmpty()) {
      var threadMatcher = THREAD_ID_PATTERN.matcher(line);
      assert threadMatcher.find();
      threadId = Integer.parseInt(threadMatcher.group(1));
      threadId += 100; // avoid conflict with RW threads
      // Start new transaction
      currentTxn = KVTxn.createRegular(threadId, cfg.DEBUG);
      currentTxn.setStatus(KVTxn.TransactionStatus.ONGOING);

      line = br.readLine();
      var keyMatcher = KEY_PATTERN.matcher(line);
      assert keyMatcher.find();
//      if (keyMatcher.find()) {
        var keyStr = keyMatcher.group(1);
        var key = Codec.encodeLong(Long.parseLong(keyStr));
        keyMap.put(Long.parseLong(keyStr), key.toString());

        // Read columns for this key
        for (int i = 0; i < NUM_COLUMNS; i++) {
          // skip the line "Columns:"
          br.readLine();
          line = br.readLine();
          var tmp = line.split(": ");
          var valStr = tmp[1];
          var val = encodeString(valStr);
          valueMap.put(valStr, val.toString());
          currentTxn.addOperations(new PutOp(key, val));
//          initWrites.put(key, val);
        }
//      }
    }

    currentTxn.setStatus(KVTxn.TransactionStatus.COMMIT);
    history.addRegularTxn(threadId, currentTxn);
//    txnMap.put()
  }

  private void processOperation(BufferedReader br, String operationType,
                                BiFunction<Key, Key, KvOperation> operationFactory,
                                Map<Key, Key> initWrites) throws IOException {
    String line = br.readLine();
    var threadMatcher = THREAD_ID_PATTERN.matcher(line);
    assert threadMatcher.find();
    int threadId = Integer.parseInt(threadMatcher.group(1));

    // Start new transaction
    var currentTxn = KVTxn.createRegular(threadId, cfg.DEBUG);
    currentTxn.setStatus(KVTxn.TransactionStatus.ONGOING);

    if (operationType.equals("WRITE")) {
      // Skip "Creating 2 columns per key" line for write operations
      br.readLine();
    }

    // Read keys and their columns
    while ((line = br.readLine()) != null && !line.isEmpty()) {
      var keyMatcher = KEY_PATTERN.matcher(line);
      assert keyMatcher.find();

      var keyStr = keyMatcher.group(1);
      var key = Codec.encodeLong(Long.parseLong(keyStr));
      keyMap.put(Long.parseLong(keyStr), key.toString());

      // Read columns for this key
      for (int i = 0; i < NUM_COLUMNS; i++) {
        line = br.readLine();
        int index = line.indexOf("Value: ");
        var valStr = line.substring(index+7);
        Key val = null;
        valueMap.put(valStr, valStr);
        if (valStr.equals("")) {
          val = Key.getNullKey();
          initWrites.put(key, val);
        } else {
          val = encodeString(valStr);
        }
        currentTxn.addOperations(operationFactory.apply(key, val));
      }
    }

    currentTxn.setStatus(KVTxn.TransactionStatus.COMMIT);
    history.addRegularTxn(threadId, currentTxn);
  }

  @Override
  @SneakyThrows
  public KVHistory parse(String folder) {
    String postfixI = "_I.txt";
    String postgixRW = "_RW.txt";
    String[] postfixes = new String[]{postfixI, postgixRW};
    history = new KVHistory(cfg);
    var initWrites = new HashMap<Key, Key>();

    for (var postfix : postfixes) {
      var path = Paths.get(folder+postfix);
      if (!Files.exists(path)) {
        throw new InvalidInputException("Path does not exist: " + folder);
      }
      if (Files.isDirectory(path)) {
        throw new InvalidInputException("Eiger traces should be in a single file, not a directory");
      }
      if (!Files.isRegularFile(path)) {
        throw new InvalidInputException("Path is not a regular file: " + folder);
      }

      try (var br = new BufferedReader(new FileReader(path.toAbsolutePath().toString()))) {
        String line;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("=== WRITE ===")) {
            processOperation(br, "WRITE", PutOp::new, initWrites);
          } else if (line.startsWith("=== READ ===")) {
            processOperation(br, "READ", ReadOp::new, initWrites);
          } else if (line.startsWith("=== INSERT ====")) {
            processInsertOperation(br, initWrites);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to read Eiger trace file: " + folder, e);
      }
    }

    // Process T_0
    var initTxn = history.getKthTxn(0);
    initTxn.setState(initWrites);
    for (var p : initWrites.entrySet()) {
      initTxn.addOperations(new PutOp(p.getKey(), p.getValue()));
    }
    initTxn.setStatus(KVTxn.TransactionStatus.COMMIT);

    if (cfg.DEBUG) {
      // dump key map
      try (var pw = new PrintWriter(new FileWriter("key_map.txt"))) {
        pw.println("Eiger key: Boomslang key");
        for (var p : keyMap.entrySet()) {
          pw.println(p.getKey() + ": " + p.getValue());
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to write key map", e);
      }

      // dump value map
      try (var pw = new PrintWriter(new FileWriter("value_map.txt"))) {
        pw.println("Eiger value: Boomslang value");
        for (var p : valueMap.entrySet()) {
          pw.println(p.getKey() + ": " + p.getValue());
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to write value map", e);
      }

      // dump txnMap
      try (var pw = new PrintWriter(new FileWriter("txn_map.txt"))) {
        pw.println("Eiger txn: Boomslang txn");
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
