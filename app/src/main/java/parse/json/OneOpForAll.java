package parse.json;

import common.Codec;
import history.op.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import common.Key;
import util.MapFactory;
import util.exception.InvalidInputException;

public class OneOpForAll {
  // {"opType": "w", "key": 264, "value": 8750677177074757140, "isDead": true}
  // {"size": 3, "value": [{"opType": "range", "key1": 4, "key2": 1202, " +
  //                "liveKVs": [{"id": 513, "sk": 513, "val": 5628397431914201033}], " +
  //                "deadKVs": [{"id": 65, "sk": 65, "val": 4545511989561494064}, " +
  //                "{"id": 193, "sk": 193, "val": 6242250761567886848}], "isFinal": false}]}\n
  // {"opType": "d", "key": 16, "value": -8964206372042397882, "readValue": -5326653975847958112, "isDead": false, "succ": true}
  String opType;
  // TODO: gson cannot automatically parse integers into Key object, we still need to modify the
  //  component of json parsing module.
  Long key;
  Long value;
  Long key1;
  Long key2;
  // TODO: to support string keys
  Long val1;
  Long val2;
  boolean succ;
  Boolean forUpdate; // Indicates if this is a FOR UPDATE read/range operation (defaults to false if not present)
  List<RangeQuerySingleItem> liveKVs;

//  long request_timestamp;
//  long response_timestamp;

  /**
   * convert the unified `OneOpForAll` to the corresponding concrete KVOperations.
   * @param isInitial
   * @return
   */
  public List<KvOperation> toOperations(boolean isInitial) {
    KvOperation op = null;
    List<KvOperation> ops = new ArrayList<>();

    switch (opType) {
      case "begin": {
        op = new BeginOp();
        ops.add(op);
        break;
      }
      case "commit": {
        op = new CommitOp();
        ops.add(op);
        break;
      }
      case "abort": {
        op = new AbortOp();
        ops.add(op);
        break;
      }
      case "d": {
        op = new DeleteOp(Codec.encodeLong(key), succ);
        ops.add(op);
        break;
      }
      case "r": {
        boolean isForUpdate = (forUpdate != null) ? forUpdate : false;
        op = new ReadOp(Codec.encodeLong(key), Codec.encodeLong(value), isForUpdate, null);
        ops.add(op);
        break;
      }
      case "w": {
        op = new PutOp(Codec.encodeLong(key), Codec.encodeLong(value));
        ops.add(op);
        break;
      }
      case "range": {
        Map<Key, Key> liveValsMap = null;
        liveValsMap = convertRangeQueryResultsIntoMap(liveKVs);

        if (!isInitial) {
          boolean isForUpdate = (forUpdate != null) ? forUpdate : false;
          op = new RangeOp(
              Codec.encodeLong(key1),
              Codec.encodeLong(key2),
              liveValsMap,
              null,
              isForUpdate,
              null);
          ops.add(op);
        } else {
          // parse the initial txn as a write txn that creates the initial state.
          for (Map.Entry<Key, Key> kv : liveValsMap.entrySet()) {
            op = new PutOp(kv.getKey(), kv.getValue());
            ops.add(op);
          }
        }
        break;
      }
      default:
        throw new InvalidInputException("Unexpected op type: " + opType);
    }
    return ops;
  }

  private Map<Key, Key> convertRangeQueryResultsIntoMap(List<RangeQuerySingleItem> kvs) {
    Map<Key, Key> kvsMap = new HashMap<>();
    for (RangeQuerySingleItem kvItem : kvs) {
      kvsMap.put(Codec.encodeLong(kvItem.id), Codec.encodeLong( kvItem.val));
    }
    return kvsMap;
  }

  class RangeQuerySingleItem {
    Long id;
    Long val;
  }
}
