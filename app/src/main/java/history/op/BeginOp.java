package history.op;

import common.Key;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.Map;


public class BeginOp extends KvOperation {
  // TODO: remove these two constructors after all parsers support per-operation ctx.
  public BeginOp() {
    this(null);
  }
  public BeginOp(Map<String, Object> ctx) {
    super(KvOpType.BEGIN, ctx);
  }

  public String toString() {
    return "{Begin}";
  }

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
    return false;
  }

  @Override
  public boolean involvesKey(Key key) {
    return false;  // Transaction control operations don't involve any keys
  }

  @Override
  public Key getValueForKey(Key key) {
    return null;  // Transaction control operations don't have values
  }
}
