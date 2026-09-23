package buildASG.builder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import asg.ASG;
import buildASG.GraphBuildContext;
import common.Key;
import graphs.DirectDep;
import graphs.edges.EdgeType;
import history.KVHistory;
import history.Pos;
import history.op.IterOp;
import util.Config;
import util.Utils;
import util.exception.RejectException;

/**
 * Builds dependencies for iterator operations.
 * Single responsibility: Handle non-returned keys in iterator scans.
 */
public class IteratorBuilder implements GraphBuilder {
  private final Config config;

  public IteratorBuilder(Config config) {
    this.config = config;
  }

  @Override
  public String getName() {
    return "Iterator";
  }

  @Override
  public boolean isApplicable(GraphBuildContext context) {
    return !context.getAsg().getReadMetadata().iterOps().isEmpty();
  }

  @Override
  public void build(GraphBuildContext context) throws RejectException {
    ASG asg = context.getAsg();
    KVHistory history = context.getHistory();

    for (var iterOpPos : asg.getReadMetadata().iterOps()) {
      processIteratorOperation(asg, history, iterOpPos);
    }
  }

  private void processIteratorOperation(ASG asg, KVHistory history, Pos iterOpPos) throws RejectException {
    int iterTxnId = iterOpPos.getTxnId();
    int iterMopIndex = iterOpPos.getMopIndex();
    var txn = asg.getHistory().getKthTxn(iterTxnId);
    IterOp iterOp = (IterOp) txn.getKthOperation(iterMopIndex);

    Map<Key, Key> returnedKvPairs = iterOp.getKvPairs();
    Set<Key> nonReturnedKeys = Utils.subsetWithExclude(asg.getKeys(), iterOp.getKey1(), iterOp.getKey2(),
        returnedKvPairs.keySet());

    for (Key key : nonReturnedKeys) {
      if (!asg.getInitialState().containsKey(key)) {
        throw new RuntimeException("InitialState should contain key %s " + key.toString());
      }

      processNonReturnedKey(asg, iterTxnId, iterMopIndex, key, returnedKvPairs);
    }
  }

  private void processNonReturnedKey(ASG asg, int iterTxnId, int iterMopIndex,
      Key key, Map<Key, Key> returnedKvPairs) throws RejectException {
    // key meets the predicate, and it isn't returned.
    Key maxKey = Utils.getMaxKey(returnedKvPairs.keySet());
    var kWrites = new HashSet<>(asg.getWriteMetadata().getAllWriteTxnIds(key));
    kWrites.remove(iterTxnId);
    var history = asg.getHistory();
    var txn = history.getKthTxn(iterTxnId);
    boolean hasLocalWrite = txn.hasPriorWriteToKey(key, iterMopIndex);

    if (!kWrites.contains(0)) {
      throw new RuntimeException("kWrites should contain T0.");
    }

    if (key.compareTo(maxKey) > 0) { // the iteration hasn't reached `key` and stops.
      // (1) `key` exists in the db, but it is greater than all returned keys.
      /*
       * Can read from any write, no matter from itself or other txns. Since we
       * already give RU a fast path in KvMain.java
       */
      // var candidateWrites = new
      // ArrayList<>(asg.getWriteMetadata().getAllWrites(key));
      // boolean isRMW = WriteReadUtils.isReadModifyWritePattern(txn, iterTxnId,
      // iterMopIndex, key, candidateWrites, kWrites);
      // var dep = new DirectDep(iterTxnId, iterMopIndex, key, EdgeType.PWR,
      // candidateWrites, kWrites, isRMW, hasLocalWrite);
      // asg.getDependencyStore().addDirectDep(dep);
    } else {
      // (2) `key` doesn't exist in the current database state.
      if (asg.getInitialState().get(key).equals(Key.getNullKey())) {
        if (!asg.getWriteMetadata().hasExternalDelete(key, 0)) {
          throw new RuntimeException("kDels should contain T0.");
        }
      }
      // Candidate writes. No need to consider intermediate writes because RU is
      // already handled in kvMain.java.
      var candidateWrites = new ArrayList<>(asg.getWriteMetadata().getAllWritesByValue(key, Key.getNullKey()));
      boolean isRMW = WriteReadUtils.isReadModifyWritePattern(txn, iterTxnId,
          iterMopIndex, key, candidateWrites, kWrites);
      var keyNotExists = new DirectDep(iterTxnId, iterMopIndex, key, EdgeType.PWR,
          candidateWrites, kWrites, isRMW, hasLocalWrite);
      asg.getDependencyStore().addDirectDep(keyNotExists);
    }
  }
}
