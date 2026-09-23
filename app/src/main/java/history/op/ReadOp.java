package history.op;

import common.Key;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.NotImplementedException;
import parse.cobra.CobraHistoryParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
public class ReadOp extends KvOperation {
  public CobraHistoryParser.CobraValue cobraValue;
  //    "r", 2820, -2350820168853512233, false
  @Getter
  @Setter
  Key key;
  @Getter
  @Setter
  Key value;
  @Getter
  @Setter
  boolean forUpdate; // Indicates if this is a FOR UPDATE read

  // TODO: remove these two constructors after all parsers support per-operation ctx.
  public ReadOp(Key key, Key value){
    this(key, value, false, new HashMap<>());
  }

  public ReadOp(byte[] key, byte[] val) {
    this(new Key(key), new Key(val));
  }

  public ReadOp(Key key, Key value, Map<String, Object> ctx){
    this(key, value, false, ctx);
  }

  public ReadOp(Key key, Key value, boolean forUpdate, Map<String, Object> ctx){
    super(KvOpType.READ, ctx);
    this.key = key;
    this.value = value;
    this.forUpdate = forUpdate;
  }

  public ReadOp(byte[] key, byte[] val, Map<String, Object> ctx) {
    this(new Key(key), new Key(val), ctx);
  }

  // especially for Cobra logs
//  public ReadOp(Key key, CobraHistoryParser.CobraValue cobraValue) {
//    super(KvOpType.READ);
//    this.key = key;
//    this.cobraValue = cobraValue;
//  }

  public ReadOp(Key key, CobraHistoryParser.CobraValue cobraValue, Map<String, Object> ctx) {
    super(KvOpType.READ, ctx);
    this.key = key;
    this.cobraValue = cobraValue;
  }

  public boolean originalTxnId() {
    return cobraValue != null;
  }

//  public ReadOp(Key key, Key value, long reqTimestamp, long resTimestamp) {
//    super(KvOpType.READ);
//    this.key = key;
//    this.value = value;
//    this.reqTS = reqTimestamp;
//    this.resTS = resTimestamp;
//  }

  public String toString() {
    String opType = forUpdate ? "ReadForUpdate" : "Read";
    return String.format("{%s: key=%s, value=%s}", opType, key.toString(), value.toString());
  }

  @Override
  public Key getReadValue() {
    return value;
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
    return this.key.equals(key);
  }

  @Override
  public Key getValueForKey(Key key) {
    return this.key.equals(key) ? this.value : null;
  }
}
