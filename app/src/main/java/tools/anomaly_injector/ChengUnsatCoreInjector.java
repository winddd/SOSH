package tools.anomaly_injector;

import com.microsoft.z3.Log;
import common.Codec;
import common.Key;
import common.binaryhistory.LOG_TXN;
import common.binaryhistory.Mop;
import common.binaryhistory.OP_TYPE;
import common.binaryhistory.SessionHistory;
import history.KVHistory;
import history.KVTxn;

import java.util.*;

public class ChengUnsatCoreInjector{
  private int numTxns = 5;
  private int numKeys = 4;

  private Key createKey(long n) {
    byte[] keyBytes = Codec.internalEncodeLong(n);
    keyBytes[0] = 'k';
    return new Key(keyBytes);
  }

  private Key[] getFiveKeys(int unsatCoreId) {
    var keys = new Key[numKeys];

    for (int i = 0; i < numKeys; i++) {
      keys[i] = createKey(unsatCoreId * numKeys + i + 1);
    }

    return keys;
  }

  private List<LOG_TXN> createLogTxns(Key[] keys) {
    Key k1 = keys[0];
    Key k2 = keys[1];
    Key k3 = keys[2];
    Key k4 = keys[3];

    var v1 = createKey(1);
    var v2 = createKey(2);

    var T1 = new LOG_TXN(false, false,
        List.of(createMop(OP_TYPE.PUT, k1, v1), createMop(OP_TYPE.READ, k2, v1)));
    var T2 = new LOG_TXN(false, false,
        List.of(createMop(OP_TYPE.READ, k1, v1), createMop(OP_TYPE.PUT, k4, v1)));
    var T3 = new LOG_TXN(false, false,
        List.of(createMop(OP_TYPE.READ, k4, v1), createMop(OP_TYPE.PUT, k3, v2)));
    var T4 = new LOG_TXN(false, false,
        List.of(createMop(OP_TYPE.PUT, k2, v1), createMop(OP_TYPE.PUT, k3, v1)));
    var T5 = new LOG_TXN(false, false,
        List.of(createMop(OP_TYPE.READ, k3, v1), createMop(OP_TYPE.PUT, k1, v2),
            createMop(OP_TYPE.PUT, k2, v2), createMop(OP_TYPE.PUT, k4, v2)));
    return List.of(T1, T2, T3, T4, T5);
  }

  private List<KVTxn> createKvTxns(Key[] keys, int minSessionId, int maxSessionId) {
    var LOGTXNS = createLogTxns(keys);
    var kvTxns = new ArrayList<KVTxn>();
    int sessionId = minSessionId;
    for (var logTxn : LOGTXNS) {
      assert sessionId <= maxSessionId;
      kvTxns.add(KVTxn.fromLogTxn(sessionId, logTxn, false));
      sessionId ++;
    }
    return kvTxns;
  }

  public KVHistory injectUnsatCores(KVHistory history, int numOfUnsatCores) {
    var tids = new ArrayList<>(history.getThreadIds());
    tids.add(0L);
    int maxTid = (int) (long) Collections.max(tids);

    for (int unsatCoreId = 0; unsatCoreId < numOfUnsatCores; unsatCoreId++) {
      int minThreadId = maxTid + 5*unsatCoreId + 1;
      int maxThreadId = maxTid + 5*unsatCoreId + 5;
      var keys = getFiveKeys(unsatCoreId);
      var kvTxns = createKvTxns(keys, minThreadId, maxThreadId);
      for (var txn: kvTxns) {
        history.addRegularTxn(txn.getThreadId(), txn);
      }
    }

    return history;
  }

  protected Mop createMop(OP_TYPE op_type, Key key, Key value) {
    return new Mop(op_type, key, value, null,
        false, false, null, null, null);
  }
}
