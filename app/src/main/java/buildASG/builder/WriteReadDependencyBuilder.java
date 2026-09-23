package buildASG.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import asg.ASG;
import asg.ReadEvent;
import asg.WriteEvent;
import buildASG.GraphBuildContext;
import common.Key;
import graphs.DirectDep;
import graphs.edges.EdgeType;
import util.exception.RejectException;

/**
 * Builds write-read dependencies from read events.
 * Single responsibility: Create WR dependencies for all read operations.
 */
public class WriteReadDependencyBuilder implements GraphBuilder {

  /** Identifies this builder within the pipeline. */
  @Override
  public String getName() {
    return "WriteReadDependency";
  }

  /** Always runs because every history needs WR edges. */
  @Override
  public boolean isApplicable(GraphBuildContext context) {
    return true; // Always needed
  }

  /** Builds write-read dependency candidates for every recorded read event. */
  @Override
  public void build(GraphBuildContext context) throws RejectException {
    ASG asg = context.getAsg();

    // Iterate over every read operation and construct the corresponding WR edge
    // candidates.
    for (ReadEvent event : asg.getReadMetadata().readEvents()) {
      var deps = createWriteReadDependency(asg, event);
      for (var dep : deps) {
        asg.getDependencyStore().addDirectDep(dep);
      }
    }
  }

  /**
   * Creates a DirectDep object for a write-read dependency.
   *
   * @param externalWriters  The set of external transactions that could have
   *                         written the value
   * @param allWritersForKey All transactions that wrote to this key (used for RMW
   *                         detection)
   * @return A new DirectDep representing the write-read dependency
   */
  /**
   * Emits the WR (and PWR for multi-key reads) dependencies for a single read and
   * its writers.
   * TODO: remove the argument `externalWriters` and only use `candidateWrites`.
   */
  private List<DirectDep> createDirectDependencies(
      ASG asg,
      ReadEvent event,
      Set<Integer> allWritersForKey,
      List<WriteEvent> writeEvents) {
    List<DirectDep> deps = new ArrayList<>();

    // Get transaction for utility calls
    var txn = asg.getHistory().getKthTxn(event.getTxnId());

    boolean isReadModifyWrite = WriteReadUtils.isReadModifyWritePattern(
        txn, event.getTxnId(), event.getOpIndex(), event.getKey(), writeEvents, allWritersForKey);
    boolean hasLocalWrite = txn.hasPriorWriteToKey(event.getKey(), event.getOpIndex());

    // No matter this read event is from a ReadOp or RangeOp/IterOp,
    // always add a WR edge.
    deps.add(new DirectDep(
        event.getTxnId(),
        event.getOpIndex(),
        event.getKey(),
        EdgeType.WR,
        writeEvents,
        allWritersForKey,
        isReadModifyWrite,
        hasLocalWrite));

    // If this is a read from RangeOp/IterOp, then we also add a PWR edge
    // I don't think we need this because it will be always covered by normal WR
    // dependencies.
    // if (!event.isFromReadOp()) {
    // deps.add(new DirectDep(
    // event.getTxnId(),
    // event.getOpIndex(),
    // event.getKey(),
    // EdgeType.PWR,
    // writeEvents,
    // allWritersForKey,
    // isReadModifyWrite,
    // hasLocalWrite));
    // }

    return deps;
  }

  /**
   * Creates a write-read dependency for a given read event. The builder is now
   * isolation agnostic:
   * it simply records every plausible writer (latest intra-txn write plus all
   * external writes that
   * produced the observed value) and leaves policy decisions to later phases.
   */
  /**
   * Resolves candidate writers for a read and builds dependencies when external
   * writers exist.
   */
  private List<DirectDep> createWriteReadDependency(ASG asg, ReadEvent event) throws RejectException {
    int readTxnId = event.getTxnId();
    Key readKey = event.getKey();
    Key readValue = event.getValue();
    var candidateWrites = WriteReadUtils.collectCandidateWrites(asg, event);

    if (candidateWrites.isEmpty()) {
      // This is not allowed by any isolation level.
      throw DependencyExceptions.noWritersFound(readTxnId, event.getOpIndex(), readKey, readValue);
    }

    var allExternalWritersForKey = asg.getWriteMetadata().getExternalWriteTxnIds(readKey);
    return createDirectDependencies(asg, event, allExternalWritersForKey, candidateWrites);
  }

  /**
   * Factory for creating consistent exception messages for dependency violations.
   */
  static class DependencyExceptions {
    static RejectException noWritersFound(int readTxnId, int readOpIndex, Key readKey, Key readValue) {
      return new RejectException(String.format(
          "WriteReadDependency: No candidate writers found for read of key='%s', value='%s' " +
              "in transaction T%d at operation %d",
          readKey, readValue, readTxnId, readOpIndex));
    }
  }
}
