package parse.json;

import java.util.List;

import lombok.Getter;

/**
 * represents all the transactions of a single thread.
 */
@Getter
public class ThreadHistoryPOJO {
  private final int tid;

  private final List<TransactionPOJO> txns;

  public ThreadHistoryPOJO(int tid, List<TransactionPOJO> txns) {
    this.tid = tid;
    this.txns = txns;
  }
}
