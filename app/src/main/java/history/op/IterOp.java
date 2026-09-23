package history.op;

import common.Key;
import asg.VersionSet;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
public class IterOp extends RangeOp {
  public IterOp(Key key1, Key key2, Map<Key, Key> kvPairs) {
    this(key1, key2, kvPairs, null, false, null);
  }

  public IterOp(Key key1, Key key2, Map<Key, Key> kvPairs, VersionSet versionSet) {
    this(key1, key2, kvPairs, versionSet, false, null);
  }

  public IterOp(Key key1, Key key2, Map<Key, Key> kvPairs, VersionSet versionSet, Map<String, Object> ctx) {
    this(key1, key2, kvPairs, versionSet, false, ctx);
  }

  public IterOp(Key key1, Key key2, Map<Key, Key> kvPairs, VersionSet versionSet, boolean forUpdate, Map<String, Object> ctx) {
    super(key1, key2, kvPairs, versionSet, forUpdate, ctx);
    this.opType = KvOpType.ITER;
  }

//  public IterOp(Key key1, Key key2, Map<Key, Key> kvPairs, Map<String, Object> ctx) {
//    this(key1, key2, kvPairs, null, ctx);
//  }
//  public IterOp(byte[] startKey, byte[] endKey, byte[][] keys, byte[][] vals) {
//    super(startKey, endKey, keys, vals);
//    this.opType = KvOpType.ITER;
//  }
//
//  public IterOp(byte[] startKey, byte[] endKey, List<byte[]> keys, List<byte[]> vals) {
//    super(startKey, endKey, keys, vals);
//    this.opType = KvOpType.ITER;
//  }

  public void addKeyValuePair(Key key, Key value) {
    this.kvPairs.put(key, value);
  }

  public String toString() {
    String valStr = kvPairs.toString();
    return String.format("{Iter: k1=%s, k2=%s, KVs=%s}",
        key1, key2, valStr);
  }
}
