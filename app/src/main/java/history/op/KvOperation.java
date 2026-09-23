package history.op;

import common.Key;
import common.binaryhistory.Mop;
import history.KVTxn;

import java.util.List;
import java.util.Map;

import lombok.Data;
import util.exception.InvalidInputException;

@Data
public abstract class KvOperation {
  protected KvOpType opType;
  // per-operation context, used for hints generation
  protected Map<String, Object> ctx;

  public KvOperation(){}

  protected KvOperation(KvOpType opType, Map<String, Object> ctx) {
    this.opType = opType;
    this.ctx = ctx;
  }

  private static Key handleIfNull(Key key) {
    return key == null ? Key.getNullKey() : key;
  }

  public static KvOperation createKVOperation(Mop mop, Map<String, Object> ctx) {
    KvOperation kvMop;

    switch (mop.op_type) {
      case PUT: {
        assert mop.key1 != null;
        assert mop.value != null;
        kvMop = new PutOp(handleIfNull(mop.key1), mop.value, ctx);
        break;
      }
      case READ: {
        kvMop = new ReadOp(handleIfNull(mop.key1), handleIfNull(mop.value), mop.forUpdate, ctx);
        break;
      }
      case DELETE: {
        kvMop = new DeleteOp(handleIfNull(mop.key1));
        break;
      }
      case RANGE: {
        assert mop.key1 != null && mop.key2 != null;
        kvMop = new RangeOp(mop.key1, mop.key2, mop.kvPairs, null, mop.forUpdate, ctx);
        break;
      }
      case ITER: {
        assert mop.key1 != null && mop.key2 != null;
        kvMop = new IterOp(mop.key1, mop.key2, mop.kvPairs, null, mop.forUpdate, ctx);
        break;
      }
      case START_TXN: {
        kvMop = new BeginOp(ctx);
        break;
      }
      case COMMIT_TXN: {
        kvMop = new CommitOp(ctx);
        break;
      }
      default:
        throw new InvalidInputException("Invalid Mop type");
    }

    return kvMop;
  }

  public KvOpType getOpType() {
    return opType;
  }

  /**
   * @return for logging
   */
  public abstract Key getValue();

  public abstract Key getKey();

  public abstract Key getReadValue();

  public abstract boolean isSucc();

  public abstract List<Key> getValues();

  public abstract boolean isWrite();

  public abstract boolean isRead();

  /**
   * Checks whether this operation involves the specified key.
   * For range operations, checks if the key appears in the returned key-value pairs.
   * For point operations, checks if the operation's key equals the specified key.
   *
   * @param key The key to check
   * @return true if the operation involves the key, false otherwise
   */
  public abstract boolean involvesKey(Key key);

  /**
   * Gets the value associated with a key from this operation.
   * For range operations, retrieves the value from the key-value pairs map.
   * For read operations, returns the read value.
   * For write operations, returns the written value.
   * For operations that don't involve the specified key, returns null.
   *
   * @param key The key to get the value for
   * @return the value for the key, or null if not applicable
   */
  public abstract Key getValueForKey(Key key);
}
