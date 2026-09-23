package common.binaryhistory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import common.Key;

public class Mop implements Serializable {
  private static final long serialVersionUID = 6529685098267757690L;
  public OP_TYPE op_type;
  public Key key1;
  public Key key2;
  public Key value;
  public Key read_v;
  public boolean update_succ;
  public boolean forUpdate; // Indicates if this is a FOR UPDATE read/range query
  public Key v1;
  public Key v2;
  public Map<Key, Key> kvPairs = new HashMap<>(); // ["k1:v1", "k2:v2"]

  private Map<OP_TYPE, String> op2template = Map.of(
      OP_TYPE.START_TXN, "",
      OP_TYPE.COMMIT_TXN, "",
      OP_TYPE.READ, """
          {"opType": "r", "key": %s, "value": %s, "forUpdate": %b}""",
      OP_TYPE.PUT, """
          {"opType": "p", "key": %s, "value": %s, "succ": %b}""",
      OP_TYPE.DELETE, """
          {"opType": "d", "key": %s, "succ": %b}""",
      OP_TYPE.RANGE, """
          {"opType": "range", "key1": %s, "key2": %s, "val1": %s, "val2": %s, "forUpdate": %b, "liveKVs": %s},""",
      OP_TYPE.ITER, """
          {"opType": "iter", "key1": %s, "key2": %s, "liveKVs": %s}""");

  public Mop(OP_TYPE op, Key key, Key value, Key read_v,
      boolean update_succ, boolean forUpdate, Key key2, Key v1, Key v2) {
    this.op_type = op;
    this.key1 = key;
    this.key2 = key2;
    this.value = value;
    this.read_v = read_v;
    this.update_succ = update_succ;
    this.forUpdate = forUpdate;
    this.v1 = v1;
    this.v2 = v2;
  }

  public String toString() {
    String ret = null;

    switch (this.op_type) {
      case READ:
        ret = String.format(op2template.get(this.op_type), this.key1, this.value, this.forUpdate);
        break;
      case PUT:
        ret = String.format(op2template.get(this.op_type), this.key1, this.value, this.update_succ);
        break;
      case DELETE:
        ret = String.format(op2template.get(this.op_type), this.key1, this.update_succ);
        break;
      case RANGE: {
        String real_vals_str = values2String(kvPairs);

        String template = op2template.get(this.op_type);
        ret = String.format(template,
            (key1 == null) ? Long.MIN_VALUE : key1,
            (key2 == null) ? Long.MAX_VALUE : key2,
            (v1 == null) ? Long.MIN_VALUE : v1,
            (v2 == null) ? Long.MAX_VALUE : v2,
            this.forUpdate,
            real_vals_str);
        break;
      }
      case ITER: {
        String real_vals_str = values2String(kvPairs);
        String template = op2template.get(this.op_type);

        ret = String.format(template,
            (key1 == null) ? "null" : key1,
            (key2 == null) ? "null" : key2,
            real_vals_str);
        break;
      }
      case START_TXN:
      case COMMIT_TXN:
        ret = String.format(op2template.get(this.op_type));
        break;
      default:
        assert false;
        break;
    }

    return ret;
  }

  public void setValuesArray(Map<Key, Key> kvPairs) {
    this.kvPairs = kvPairs;
  }

  protected String values2String(Map<Key, Key> vals) {
    if (vals == null) {
      return "[]";
    }
    StringBuilder s = new StringBuilder();
    s.append("[");

    boolean isFirstKVPair = true;
    String kvItem = """
        {"id": %s, "val": %s}""";
    for (var entry : vals.entrySet()) {
      String tmp = String.format(kvItem,
          entry.getKey(), entry.getValue());
      if (isFirstKVPair) {
        isFirstKVPair = false;
      } else {
        tmp = ", " + tmp;
      }
      s.append(tmp);
    }
    s.append("]");

    return s.toString();
  }
}
