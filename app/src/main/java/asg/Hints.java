package asg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Hints {
  // hint for all txns
  // key: txn id
  // value: list of ctx for each op
  // The ctx of each op is a Map<String, Object>, essentially just extract the ctx of each op and put them in this map.
  private final Map<Integer, List<Map<String, Object>>> m;

  public Hints() {
    m = new HashMap<>();
  }

  public void initializeForTxn(int txnId, int numOps) {
    List<Map<String, Object>> list = new ArrayList<>(numOps);
    for (int i = 0; i < numOps; i++) {
      list.add(null);
    }
    m.put(txnId, list);
  }

  public void addHint(int txnId, int opIdx, Map<String, Object> ctx) {
    m.get(txnId).set(opIdx, ctx);
  }

  public Map<String, Object> getHint(int txnId, int opIdx) {
    return m.get(txnId).get(opIdx);
  }
}
