package util.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import history.KVHistory;
import history.KVTxn;
import history.op.KvOperation;
import history.op.KvOpType;
import history.op.RangeOp;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openhft.hashing.LongHashFunction;

public class ViperLogWriter {
  private static Map<KvOpType, String> opType2Tag
      = Map.of(KvOpType.READ, "r", KvOpType.PUT, "w");
  private KVHistory history;
  private Path folder;

  public ViperLogWriter(KVHistory history, Path folder) {
    this.history = history;
    this.folder = folder;
  }

  public void dump() {
    // initialState, we don't need special processing of initialState,
    // because in CobraHistoryLoader, we already created Put operations for those keys.
//    var initialState = history.getInitialState();
//    for (var kv : initialState.entrySet()) {
//
//    }

    // normal txns
    List<Long> tids = history.getThreadIds();

    for (var tid: tids) {
      List<KVTxn> txns = history.getTxnsByTid(tid);
      StringBuilder builder = new StringBuilder();

      for (var txn: txns) {
        builder.append(kvTxn2String(txn)).append("\n");
      }

      DumpResult.writeToFile(builder.toString(),
          Paths.get(folder.toString(), "J" + tid + ".log").toString(), true);
    }

    // initial txn
    var t0 = history.getKthTxn(0);
    String s = kvTxn2String(t0) + "\n";
    DumpResult.writeToFile(s,
        Paths.get(folder.toString(), "J105.log").toString(), true);
  }

  private String kvTxn2String(KVTxn txn) {
    StringBuilder txnStr = new StringBuilder("{");

    if (txn.isInitial()) {
      txnStr.append("\"isInitial\": true, ");
//      List<KVOperation> mops = txn.getMops();
//      assert mops.size() == 1;
//      var mop = mops.get(0);
//      assert mop.getOpType() == KvOpType.RANGE;
//
//      String mopStr = rangeOp2String((RangeOp) mop);
//      txnMap.put("value", mopStr);
    } else {
      txnStr.append("\"isInitial\": false, ");
    }

    List<String> txnValue = new ArrayList<>();
    for (var mop: txn.getMops()) {
      txnValue.add(rwOp2String(mop));
    }

    String txnValueStr = String.join(", ", txnValue.toArray(new String[0]));
    txnValueStr = "[" + txnValueStr + "]";
    txnStr.append(String.format("\"value\": %s", txnValueStr));

    txnStr.append("}");
    // convert `txnMap` into string
    return txnStr.toString();
  }

  private String map2json(Map m) {
    ObjectMapper objectMapper = new ObjectMapper();
    String json = null;
    try {
      json = objectMapper.writeValueAsString(m);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    return json;
  }

  private String rangeOp2String(RangeOp mop) {
    // TODO: double check
    Object[] rangeObj = new Object[5];
    rangeObj[0] = "range";
    rangeObj[1] = "nil";
    rangeObj[2] = "nil";
    rangeObj[4] = "[]";

    //
    var kvPairs = mop.getKvPairs();
    List<String> kvPairStrs = new ArrayList<>();
    for (var kv: kvPairs.entrySet()) {
      kvPairStrs.add(
          String.format(
              "{\"id\": %d, \"sk\": %d, \"val\": %d}",
              kv.getKey(), kv.getKey(), kv.getValue()));
    }

    String kvPairStr = "[" + String.join(", ",
        kvPairStrs.toArray(new String[0])) + "]";
    rangeObj[4] = kvPairStr;

    return "[" + String.join(", ", (String) rangeObj[0],
        (String) rangeObj[1], (String) rangeObj[2],
        kvPairStr, (String) rangeObj[4]) + "]";
  }

  private String rwOp2String(KvOperation op) {
    String opTypeTag = opType2Tag.get(op.getOpType());
    long key_hash = LongHashFunction.xx().hashBytes(op.getKey().getBytes());
    long val_hash = LongHashFunction.xx().hashBytes(op.getValue().getBytes());

    assert op.getOpType() == KvOpType.READ || op.getOpType() == KvOpType.PUT;
    boolean b = op.getOpType() == KvOpType.READ? false : true;

    return "[" + String.join(", ",
        String.format("\"%s\"", opTypeTag),
        String.valueOf(key_hash), String.valueOf(val_hash), String.valueOf(b)) + "]";
  }
}

