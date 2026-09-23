package buildASG.builder;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import asg.ASG;
import asg.WriteEvent;
import buildASG.GraphBuildContext;
import common.Key;
import graphs.DirectDep;
import graphs.edges.EdgeType;
import history.Pos;
import history.op.KvOpType;
import history.op.RangeOp;
import util.Config;
import util.Utils;
import util.exception.InvalidInputException;
import util.exception.RejectException;

/**
 * Builds dependencies for range query operations.
 * Single responsibility: Handle phantom reads and non-returned keys in range
 * queries.
 */
public class RangeQueryBuilder implements GraphBuilder {
  private final Config config;

  public RangeQueryBuilder(Config config) {
    this.config = config;
  }

  @Override
  public String getName() {
    return "RangeQuery";
  }

  @Override
  public boolean isApplicable(GraphBuildContext context) {
    return !context.getAsg().getReadMetadata().rangeQueries().isEmpty();
  }

  @Override
  public void build(GraphBuildContext context) throws RejectException {
    ASG asg = context.getAsg();

    for (Pos pos : asg.getReadMetadata().rangeQueries()) {
      processRangeQuery(asg, pos);
    }
  }

  private void processRangeQuery(ASG asg, Pos pos) throws RejectException {
    int rangeTxnId = pos.getTxnId();
    int rangeQueryIndex = pos.getMopIndex();
    var history = asg.getHistory();
    RangeOp query = (RangeOp) history.getKthTxn(rangeTxnId).getKthOperation(rangeQueryIndex);

    Map<Key, Key> returnedKvPairs = query.getKvPairs();
    Set<Key> subset = Utils.subsetWithExclude(asg.getKeys(), query.getKey1(), query.getKey2(),
        returnedKvPairs.keySet());

    // iterate through those non-returned keys `subset`, but belong to [key1, key2).
    for (Key k : subset) {
      if (!asg.getInitialState().containsKey(k)) {
        throw new RuntimeException("InitialState should contain key %s " + k.toString());
      }
      if (query.getOpType() != KvOpType.RANGE) {
        throw new InvalidInputException("Unexpected Op Type.");
      }

      // Process deletions and dependencies
      if (asg.getInitialState().get(k).equals(Key.getNullKey())) {
        if (!asg.getWriteMetadata().hasExternalDelete(k, 0)) {
          throw new RuntimeException("kDels should contain T0.");
        }
      }

      var txn = asg.getHistory().getKthTxn(rangeTxnId);
      // Candidate writes. No need to consider intermediate writes because RU is
      // already handled in kvMain.java.
      Set<WriteEvent> kDels = asg.getWriteMetadata().getAllWritesByValue(k, Key.getNullKey());
      // all the writes of key k
      Set<Integer> kWrites = asg.getWriteMetadata().getAllWriteTxnIds(k);
      // make a copy
      var candidateWrites = new ArrayList<>(kDels);

      boolean isRMW = WriteReadUtils.isReadModifyWritePattern(txn, rangeTxnId,
          rangeQueryIndex, k, candidateWrites, kWrites);
      boolean hasLocalWrite = txn.hasPriorWriteToKey(k, rangeQueryIndex);
      var keyNotExists = new DirectDep(rangeTxnId, rangeQueryIndex, k, EdgeType.PWR,
          candidateWrites, kWrites, isRMW, hasLocalWrite);
      asg.getDependencyStore().addDirectDep(keyNotExists);
    }
  }
}
