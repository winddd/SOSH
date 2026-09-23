package history.op;
import common.Key;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.Map;

public class AbortOp extends KvOperation {
  // TODO: remove these two constructors after all parsers support per-operation ctx.
  public AbortOp() {
    this(null);
  }

  public AbortOp(Map<String, Object> ctx) {
    super(KvOpType.ABORT, ctx);
  }

  public String toString() {
    return "Abort";
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

