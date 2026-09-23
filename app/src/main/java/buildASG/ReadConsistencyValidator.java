//package buildASG;
//
//import asg.ASG;
//import asg.ReadEvent;
//import common.Key;
//import history.op.KvOperation;
//import history.op.RangeOp;
//import util.exception.RejectException;
//
///**
// * Validates read consistency for transactions.
// * Ensures reads follow isolation level guarantees and internal consistency
// * rules.
// */
//class ReadConsistencyValidator {
//  private final ASG asg;
//
//  ReadConsistencyValidator(ASG asg) {
//    this.asg = asg;
//  }
//
//  /**
//   * Validates all consistency constraints for a read event.
//   *
//   * @param event The read event to validate
//   * @throws RejectException if any consistency constraint is violated
//   */
//  void validateReadConsistency(ReadEvent event) throws RejectException {
//    validateInternalConsistency(event);
//    // Note: validateNoFutureWrites is called separately in
//    // createWriteReadDependency
//    // only when needed (when transaction reads its own writes)
//  }
//
//  /**
//   * Validates that a read does not read a value from a future write in the same
//   * transaction. A transaction cannot read value V for key K if it will write V
//   * to K later.
//   *
//   * @param event the read event to validate
//   * @throws RejectException if the read violates the future-write constraint
//   */
//  void validateNoFutureWrites(ReadEvent event) throws RejectException {
//    int readTxnId = event.getTxnId();
//    int readOpIndex = event.getOpIndex();
//    Key readKey = event.getKey();
//    Key readValue = event.getValue();
//
//    // Skip validation if transaction ID is out of bounds (may happen in test mocks)
//    if (readTxnId >= asg.getHistory().length()) {
//      return;
//    }
//
//    var txn = asg.getHistory().getKthTxn(readTxnId);
//    var ops = txn.getMops();
//
//    // Check all operations after the read
//    for (int i = readOpIndex + 1; i < ops.size(); i++) {
//      var op = ops.get(i);
//      if (op.isWrite() && operationInvolvesKey(op, readKey)) {
//        Key writtenValue = getValueForKey(op, readKey);
//        if (writtenValue != null && writtenValue.equals(readValue)) {
//          throw WriteReadDependencyBuilder.DependencyExceptions.futureWriteViolation(
//              readTxnId, readKey, readValue, readOpIndex, i);
//        }
//      }
//    }
//  }
//
//  /**
//   * Validates internal consistency: a read must see the value from the most
//   * recent
//   * read or write to the same key within the transaction.
//   *
//   * @param event the read event to validate
//   * @throws RejectException if the read violates internal consistency
//   */
//  private void validateInternalConsistency(ReadEvent event) throws RejectException {
//    int readTxnId = event.getTxnId();
//    int readOpIndex = event.getOpIndex();
//    Key readKey = event.getKey();
//    Key readValue = event.getValue();
//
//    // Skip validation if transaction ID is out of bounds (may happen in test mocks)
//    if (readTxnId >= asg.getHistory().length()) {
//      return;
//    }
//
//    var isolationLevel = asg.getConfig().RUNMODE.getIsolationLevel();
//    var txn = asg.getHistory().getKthTxn(readTxnId);
//    var ops = txn.getMops();
//
//    // Scan backwards to find the most recent read or write to the same key
//    for (int i = readOpIndex - 1; i >= 0; i--) {
//      var op = ops.get(i);
//
//      // if `op` is a special operation like commit/abort/begin
//      if ((!op.isWrite()) && (!op.isRead())) {
//        continue;
//      }
//
//      if (!operationInvolvesKey(op, readKey)) {
//        continue; // Different key, skip
//      }
//
//      if (op.isWrite()) {
//        // Found a write to the same key - the read MUST see this value (RYOWPolicy)
//        if (isolationLevel.getReadOwnWritePolicy().requiresPreviousWriteMatch()) {
//          Key writtenValue = getValueForKey(op, readKey);
//          if (!writtenValue.equals(readValue)) {
//            throw WriteReadDependencyBuilder.DependencyExceptions.readOwnWriteViolation(
//                readTxnId, readKey, writtenValue, i, readValue, readOpIndex);
//          }
//        }
//        // Validation passed - read sees its own write
//        return;
//      } else {
//        // Found a prior read to the same key
//        Key priorValue = getValueForKey(op, readKey);
//        // For READ_UNCOMMITTED and READ_COMMITTED, non-repeatable reads are allowed
//        if (isolationLevel.allowNonRepeatableReads()) {
//          return; // Non-repeatable reads allowed, no validation needed
//        }
//
//        // For other isolation levels, the read must see the same value
//        if (!priorValue.equals(readValue)) {
//          throw WriteReadDependencyBuilder.DependencyExceptions.nonRepeatableReadViolation(
//              readTxnId, readKey, readValue, readOpIndex, priorValue, i, isolationLevel);
//        }
//      }
//    }
//    // No prior read or write to this key - this is the first access, always valid
//  }
//
//  /**
//   * Checks if an operation involves a specific key.
//   */
//  private boolean operationInvolvesKey(KvOperation op, Key key) {
//    if (op instanceof RangeOp) {
//      RangeOp rangeOp = (RangeOp) op;
//      return rangeOp.getKvPairs().containsKey(key);
//    } else {
//      try {
//        return op.getKey().equals(key);
//      } catch (Exception e) {
//        return false;
//      }
//    }
//  }
//
//  /**
//   * Gets the value associated with a specific key from an operation.
//   */
//  private Key getValueForKey(KvOperation op, Key key) {
//    if (op instanceof RangeOp) {
//      RangeOp rangeOp = (RangeOp) op;
//      return rangeOp.getKvPairs().get(key);
//    } else if (op.isRead()) {
//      return op.getReadValue();
//    } else if (op.isWrite()) {
//      return op.getValue();
//    }
//    return null;
//  }
//}
