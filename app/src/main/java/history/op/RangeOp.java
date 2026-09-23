package history.op;

import common.Key;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import asg.VersionSet;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.NotImplementedException;

@EqualsAndHashCode(callSuper = false)
public class RangeOp extends KvOperation {
  @Getter
  protected Map<Key, Key> kvPairs;
  @Getter
  protected Key key1;
  @Getter
  protected Key key2;
  @Setter
  @Getter
  protected VersionSet versionSet;
  @Setter
  @Getter
  protected boolean forUpdate; // Indicates if this is a FOR UPDATE range query

  public RangeOp(Key key1, Key key2, Map<Key, Key> returnedKvPairs) {
    this(key1, key2, returnedKvPairs, null, false, null);
  }

  public RangeOp(byte[] startKey, byte[] endKey, byte[][] keys, byte[][] vals) {
    this(new Key(startKey), new Key(endKey), new HashMap<>());
    assert keys.length == vals.length;
    for (int i = 0; i < keys.length; ++i){
      this.kvPairs.put(new Key(keys[i]), new Key(vals[i]));
    }
  }

  public RangeOp(byte[] startKey, byte[] endKey, List<byte[]> keys, List<byte[]> vals) {
    this(new Key(startKey), new Key(endKey), new HashMap<>());
    assert keys.size() == vals.size();
    for (int i = 0; i < keys.size(); ++i){
      this.kvPairs.put(new Key(keys.get(i)), new Key(vals.get(i)));
    }
  }

  public RangeOp(Key key1, Key key2, Map<Key, Key> returnedKvPairs, VersionSet versionSet) {
    this(key1, key2, returnedKvPairs, versionSet, false, null);
  }

  // with ctx
  public RangeOp(Key key1, Key key2, Map<Key, Key> returnedKvPairs, VersionSet versionSet, Map<String, Object> ctx) {
    this(key1, key2, returnedKvPairs, versionSet, false, ctx);
  }

  // with forUpdate and ctx
  public RangeOp(Key key1, Key key2, Map<Key, Key> returnedKvPairs, VersionSet versionSet, boolean forUpdate, Map<String, Object> ctx) {
    super(KvOpType.RANGE, ctx);
    this.key1 = key1;
    this.key2 = key2;
    this.kvPairs = returnedKvPairs;
    this.versionSet = versionSet;
    this.forUpdate = forUpdate;
  }

  public String toString() {
    String val_str = kvPairs.toString();
    String opType = forUpdate ? "RangeForUpdate" : "Range";
    return String.format("{%s: k1=%s, k2=%s, KVs=%s}",
        opType, key1, key2, val_str);
  }

  /**
   * @return for logging
   */
  @Override
  public Key getValue() {
    throw new NotImplementedException();
  }

  @Override
  public Key getKey() {
    throw new NotImplementedException();
  }

  @Override
  public Key getReadValue() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isSucc() {
    throw new NotImplementedException();
  }

  @Override
  public List<Key> getValues() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isWrite() {
    return false;
  }

  @Override
  public boolean isRead() {
    return true;
  }

  @Override
  public boolean involvesKey(Key key) {
    return kvPairs.containsKey(key);
  }

  @Override
  public Key getValueForKey(Key key) {
    return kvPairs.get(key);
  }
}
