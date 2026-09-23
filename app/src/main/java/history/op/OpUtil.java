package history.op;

import java.util.Map;

public class OpUtil {
  public static final int INIT_SESSION_ID = -1;
  public static final int INIT_TXN_INDEX = 0;
  public static final String THREADID_TAG = "SessionId";
  public static final String TXNINDEX_TAG = "TxnIndex";
  public static Map<String, Object> getInitialTxnCtx() {
    return Map.of(THREADID_TAG, INIT_SESSION_ID,
        TXNINDEX_TAG, INIT_TXN_INDEX);
  }
}
