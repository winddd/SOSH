package parse.cobra;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.Codec;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import history.op.*;
import parse.ParserUtils;
import parse.loader.InvalidHistoryError;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import parse.Parser;
import util.Config;
import util.Context;
import util.Profiler;
import util.exception.RejectException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CobraHistoryParser implements Parser {
  public static long INIT_WRITE_ID = 0xbebeebeeL;
  public static long INIT_TXN_ID = 0xbebeebeeL;
  public static long NULL_TXN_ID = 0xdeadbeefL;
  public static long GC_WID_TRUE = 0x23332333L;
  public static long GC_WID_FALSE = 0x66666666L;
  private Map<Long, Integer> oldTxnId2NewTxnId = new HashMap<>();
  private Map<Integer, Long> newTxnId2OldTxnId = new HashMap<>();
  private Config cfg;

  public CobraHistoryParser(Config cfg) {
    this.cfg = cfg;
  }
  public KVHistory loadHistory(String logDir) {
    var files = ParserUtils.findLogs(logDir, "", ".log");
    return loadLogs(files);
  }

  @SneakyThrows
  private KVHistory loadLogs(ArrayList<File> opfiles) {
    var initWrites = new HashMap<Long, CobraValue>();
    var history = new KVHistory(cfg);

    for (File f : opfiles) {
      var fileName = f.getName();
      // T55.log
      String threadIdStr = fileName.substring(1, fileName.length() - 4);
      int threadId = Integer.parseInt(threadIdStr);

      try (var in = new DataInputStream(new FileInputStream(f))) {
        extractLog(in, history, threadId, initWrites);
      }
    }

    // process T_0
    var initTxn = history.getKthTxn(0);
    Map<Key, Key> initialState = new HashMap<>();
    var ctx = new HashMap<String, Object>();
    ctx.put(OpUtil.THREADID_TAG, OpUtil.INIT_SESSION_ID);
    ctx.put(OpUtil.TXNINDEX_TAG, OpUtil.INIT_TXN_INDEX);

    for (var p : initWrites.entrySet()) {
      var cobraValue = p.getValue();
      Key key = Codec.encodeLong(p.getKey());
      Key value = new Key(Codec.encodeMultipleLong(
          cobraValue.transactionId,
          cobraValue.writeId,
          cobraValue.value));

      initTxn.addOperations(new PutOp(key, value, ctx));
      initialState.put(key, value);
      // history.addEvent(initTxn, WRITE, p.getKey(), p.getValue());
    }

    initTxn.setStatus(KVTxn.TransactionStatus.COMMIT);
    history.getKthTxn(0).setState(initialState);

    for (var txn : history) {
      if (txn.getStatus() != KVTxn.TransactionStatus.COMMIT) {
        throw new InvalidHistoryError();
      }

      // map some originalTxnId to newTxnId for some ReadOp
      for (var mop : txn.getMops()) {
        if (mop.getOpType() == KvOpType.READ && ((ReadOp) mop).originalTxnId()) {
          ReadOp readOp = (ReadOp) mop;
          long origTxnId = readOp.cobraValue.transactionId;
          long wid = readOp.cobraValue.writeId;
          long value = readOp.cobraValue.value;

          if (!oldTxnId2NewTxnId.containsKey(origTxnId)) {
            throw new RejectException("read from non-existing txn");
          }

          int newTxnId = oldTxnId2NewTxnId.get(origTxnId);
          readOp.setValue(new Key(Codec.encodeMultipleLong(newTxnId, wid, value)));
          //
//          readOp.cobraValue = null;
        }
      }
    }

    if (cfg.DEBUG) {
      // dump original txn id and new txn id mapping
      String fileName = "txnIdMapping_new2old.txt";
      var newTxnId2OldTxnId = new HashMap<Integer, String>();
      for(var oldTxnId: oldTxnId2NewTxnId.keySet()) {
        newTxnId2OldTxnId.put(oldTxnId2NewTxnId.get(oldTxnId), Long.toHexString(oldTxnId));
      }
      ObjectMapper objectMapper = new ObjectMapper();
      try {
        var json = objectMapper.writeValueAsString(newTxnId2OldTxnId);
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        writer.write(json);
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return history;
  }

  @SneakyThrows
  public void extractLog(DataInputStream in, KVHistory history,
                         int threadId, Map<Long, CobraValue> initWrites) {
    KVTxn current = null;
    Map<String, Object> ctx = null;
    int txnIndex = 0; // txn index in this session
//        Long oldTxnId = null;
    // keep reading characters from cobra logs and create txns
    while (true) {
      // break if end (for file)
      char c;
      try {
        c = (char) in.readByte();
      } catch (EOFException e) {
        break;
      }

      switch (c) {
        case 'S': {
          // TxnStart
          // assert current == null;
          assert current == null && ctx == null;
          var originalTxnId = in.readLong();
          ctx = new HashMap<>();

          // NOTE: because of inconsistency of the logs,
          // the node might be created already.
          // There are two possibilities:
          // 1. in graph and ongoing: continue (previous txn reads this txn)
          // 2. never seen: new TxnNode & continue
          if (!oldTxnId2NewTxnId.containsKey(originalTxnId)) {
            current = KVTxn.createRegular(threadId, false);
            current.setStatus(KVTxn.TransactionStatus.ONGOING);
            history.addRegularTxn(threadId, current);
            oldTxnId2NewTxnId.put(originalTxnId, current.getTxnId());
            newTxnId2OldTxnId.put(current.getTxnId(), originalTxnId);
            ctx.put(OpUtil.THREADID_TAG, threadId);
            ctx.put(OpUtil.TXNINDEX_TAG, txnIndex++);
          } else {
            assert false;
            // assert current.getMops().size() == 0;
          }
          break;
        }
        case 'C': {
          // TxnCommit
          var originalTxnId = in.readLong();
          assert current != null && current.getTxnId() == oldTxnId2NewTxnId.get(originalTxnId);
          KvOperation op = new CommitOp(ctx);
          current.setStatus(KVTxn.TransactionStatus.COMMIT);
          current = null;
          ctx = null;
          break;
        }
        case 'W': {
          // (write, writeId, key, val): ?B <br>
          assert current != null;
          var writeId = in.readLong();
          var key = in.readLong();
          var value = in.readLong();
          ctx.put(OpUtil.THREADID_TAG, threadId);
          ctx.put(OpUtil.TXNINDEX_TAG, txnIndex);

          // use writeId as value because cobra guarantees its uniqueness
          KvOperation op = new PutOp(Codec.encodeLong(key),
              // (txn id, write id, value)
              new Key(Codec.encodeMultipleLong(current.getTxnId(), writeId, value)),
              ctx);
          current.addOperations(op);
          break;
        }
        case 'R': {
          // (read, write_TxnId, writeId, key, value) : ?B <br>
          assert current != null;
          var writeTxnId = in.readLong();
          var writeId = in.readLong();
          var key = in.readLong();
          var value = in.readLong();
          ctx.put(OpUtil.THREADID_TAG, threadId);
          ctx.put(OpUtil.TXNINDEX_TAG, txnIndex);
          KvOperation op;

          // NOTE: if the prev_txnid == INIT_TXNID, then we update it to keyhash as wid
          // FIXME: separate NULL and INIT?
          if (writeTxnId == INIT_TXN_ID || writeTxnId == NULL_TXN_ID) {
            if (writeId == INIT_WRITE_ID || writeId == NULL_TXN_ID) {
              // NOTE: val_hash is 0 for NULL; but an arbitrary value for INIT
              writeId = key; // ?
//                            writeTxnId = INIT_TXN_ID;
              writeTxnId = 0;
              // if there is no such write op in init txn, add one
              // initWrites.computeIfAbsent(Codec.encodeLong(key),
              // // (txn id, write id, value)
              // k -> new Key(Codec.encodeMultipleLong(INIT_TXN_ID, key, value)));
              initWrites.computeIfAbsent(key,
                  k -> new CobraValue(key, 0L, value));
            } else {
              // else if ((writeId != GC_WID_FALSE && writeId != GC_WID_TRUE) || writeTxnId !=
              // INIT_TXN_ID) {
              // otherwise, it should be the GC read op
              throw new InvalidHistoryError();
            }

            op = new ReadOp(Codec.encodeLong(key),
                new Key(Codec.encodeMultipleLong(writeTxnId, writeId, value)), ctx);
          } else {
            op = new ReadOp(Codec.encodeLong(key),
                new CobraValue(writeId, writeTxnId, value), ctx);
          }

          // FIXME: the writeTxnId should be remapped new txnId.
//                    KVOperation op = new ReadOp(Codec.encodeLong(key),
//                            new Key(Codec.encodeMultipleLong(writeTxnId, writeId, value)));
          current.addOperations(op);
          break;
        }
        default:
          throw new InvalidHistoryError();
      }
    }
  }

  @Override
  public KVHistory parse(String folder) {
    log.info("Start history parsing...");
    return loadHistory(folder);
  }

  @Override
  public KVHistory apply(Context context, String s) {
    Profiler profiler = Profiler.getInstance();
    profiler.startTick("parsing");
    KVHistory history = this.parse(s);
    profiler.endTick("parsing");

    return history;
  }

  @Data
  public static class CobraValue {
    private final long writeId;
    private final long transactionId;
    private final long value;
  }
}
