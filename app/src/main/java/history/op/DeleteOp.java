package history.op;

import common.Key;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.NotImplementedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
public class DeleteOp extends KvOperation {
  Key key;
  Boolean succ;
  boolean succKnown = false;

  // TODO: remove these two constructors after all parsers support per-operation ctx.
  public DeleteOp(Key key) {
    this(key, new HashMap<>());
  }

  public DeleteOp(byte[] key) {
    this(key, null);
  }

  public DeleteOp(Key key, Boolean succ) {
    this(key, succ, null);
  }

  public DeleteOp(Key key, Map<String, Object> ctx) {
    super(KvOpType.DELETE, ctx);
    this.key = key;
    // If we don't know if this deletion op actually deletes a key or deletes nothing,
    // we assume it actually deletes a key.
    this.succ = true;
    this.succKnown = false;
  }

  public DeleteOp(byte[] key, Map<String, Object> ctx) {
    this(new Key(key), ctx);
  }

  public DeleteOp(Key key, Boolean succ, Map<String, Object> ctx) {
    super(KvOpType.DELETE, ctx);
    this.key = key;
    this.succ = succ;
  }

  public String toString() {
    return String.format("{Delete, key=%s, succ=%b}", key.toString(), succ);
  }

  @Override
  public Key getValue() { // trick: logically regard a deletion op writes a null value.
    return Key.getNullKey();
  }

  @Override
  public Key getKey() {
    return key;
  }

  @Override
  public Key getReadValue() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isSucc() {
    return succ;
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
    return this.key.equals(key) ? Key.getNullKey() : null;
  }
}
