package history.op;

import common.Key;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;

/**
 * This is for the Put semantics in real KV store.
 * For those KV stores that are simulated by SQL databases, we need to
 * distinguish insert and update.
 * But for real KV stores, we don't.
 */
@EqualsAndHashCode(callSuper = false)
public class PutOp extends KvOperation {
  @Getter
  Key key;
  @Getter
  Key value;

  // TODO: remove these two constructors after all parsers support per-operation ctx.
  public PutOp(Key key, Key value) {
    this(key, value, new HashMap<>());
  }

  public PutOp(byte[] key, byte[] value) {
    this(new Key(key), new Key(value), new HashMap<>());
  }

  public PutOp(Key key, Key value, Map<String, Object> ctx) {
    super(KvOpType.PUT, ctx);
    this.key = key;
    this.value = value;
  }

  public PutOp(byte[] key, byte[] value, Map<String, Object> ctx) {
    this(new Key(key), new Key(value), ctx);
  }

//  public PutOp(Key key,
//               Key value,
//               long reqTimestamp,
//               long resTimestamp,
//               KVTxn txn) {
//    super(KvOpType.PUT, txn);
//    this.key = key;
//    this.value = value;
//    this.reqTS = reqTimestamp;
//    this.resTS = resTimestamp;
//  }

  public String toString() {
    return String.format("{Put, key=%s, value=%s}", key.toString(), value.toString());
  }

  @Override
  public Key getReadValue() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isSucc() {
    return true;
  }

  @Override
  public List<Key> getValues() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isWrite() {
    return true;
  }

  @Override
  public boolean isRead() {
    return false;
  }

  @Override
  public boolean involvesKey(Key key) {
    return this.key.equals(key);
  }

  @Override
  public Key getValueForKey(Key key) {
    return this.key.equals(key) ? this.value : null;
  }
}
