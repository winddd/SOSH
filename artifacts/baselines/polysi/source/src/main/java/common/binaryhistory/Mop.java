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
  public Key v1;
  public Key v2;

  public Map<Key, Key> kvPairs = new HashMap<>(); // ["k1:v1", "k2:v2"]

  public Mop(OP_TYPE op, Key key, Key value, Key read_v,
             boolean update_succ, Key key2, Key v1, Key v2) {
    this.op_type = op;
    this.key1 = key;
    this.key2 = key2;
    this.value = value;
    this.read_v = read_v;
    this.update_succ = update_succ;
    this.v1 = v1;
    this.v2 = v2;
  }

  public static Mop of(OP_TYPE op_type, Key key,
                       Key value, Key readV, boolean isDead,
                       boolean update_cuss,
                       Key key2, Key v1, Key v2) {
    Mop op = new Mop(op_type, key, value, readV, update_cuss, key2, v1, v2);
    return op;
  }
}
