package common.binaryhistory;

public enum OP_TYPE {
  START_TXN, READ, UPDATE, COMMIT_TXN, ABORT_TXN,
  INSERT, PUT, DELETE, RANGE, RMW, ITER,
  FINAL, INITIAL
}
