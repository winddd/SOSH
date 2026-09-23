package parse.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import common.Codec;
import common.Key;
import history.KVTxn;
import history.op.KvOperation;
import history.op.PutOp;
import util.MapFactory;

public class TransactionPOJO {
  // TODO: remove this field
  long preBeginTS;
  long postBeginTS;
  long preCommitTS;
  long postCommitTS;
  List<OneOpForAll> value;

  boolean isInitial;
  boolean isFinal;

  public KVTxn toTransaction(int threadId, boolean debug) {
    KVTxn txn = new KVTxn(0, threadId, isInitial, isFinal, debug);
    List<OneOpForAll> originalMops = value;

    if (!isInitial) { // this is not an initial txn
      for (OneOpForAll originalMop : originalMops) {
        List<KvOperation> kvMops = originalMop.toOperations(isInitial);
        txn.addOperations(kvMops);
      }
    } else { // this is an initial txn
      // TODO: support initial txn, convert this range query txn into a write txn T0
      // that write
      // all the initial values
      assert originalMops.size() == 1 || (originalMops.size() == 2 && originalMops.get(1).opType.equals("commit"));
      List<KvOperation> putOps = new ArrayList<>();
      Map<Key, Key> initialState = MapFactory.getEmptyMap(debug);

      for (var kv : originalMops.get(0).liveKVs) {
        Key key = Codec.encodeLong(kv.id), value = Codec.encodeLong(kv.val);
        putOps.add(new PutOp(key, value));

        // create a map of initial database state
        initialState.put(key, value);
      }

      txn.addOperations(putOps);
      txn.setState(initialState);
    }

    return txn;
  }
}
