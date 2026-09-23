package parse.juicefs.operation;

import java.util.List;

public class LogTxn {
  public long txn_id;
  public List<LogOp> ops;
}
