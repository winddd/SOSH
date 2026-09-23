package asg.build;

import asg.ReadEvent;
import asg.ReadMetadata;
import history.Pos;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import history.op.IterOp;
import history.op.KvOperation;
import history.op.RangeOp;
import history.op.ReadOp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import util.Config;
import util.MapFactory;

/**
 * Collects read events and update-transaction metadata for an {@link asg.ASG} instance.
 */
public final class AccessMetadataCollector {

  private final Config cfg;

  public AccessMetadataCollector(Config cfg) {
    this.cfg = cfg;
  }

  private static void recordReadEvent(List<ReadEvent> readEvents,
                                      Map<Integer, List<ReadEvent>> readEventsByTxn,
                                      int txnId,
                                      int opIndex,
                                      Key key,
                                      Key value,
                                      KvOperation op) {
    ReadEvent event = new ReadEvent(txnId, opIndex, key, value, op);
    readEvents.add(event);
    readEventsByTxn.computeIfAbsent(txnId, k -> new ArrayList<>()).add(event);
  }

  public Result collect(KVHistory history) {
    List<ReadEvent> readEvents = new ArrayList<>();
    Map<Integer, List<ReadEvent>> readEventsByTxn = MapFactory.getEmptyMap(cfg.DEBUG);
    var rangePositions = new HashSet<Pos>();
    var iterPositions = new HashSet<Pos>();

    // Record the read events from ReadOp/RangeOp/IterOp
    for (KVTxn txn : history) {
      var ops = txn.getMops();
      for (int index = 0; index < ops.size(); index++) {
        KvOperation op = ops.get(index);
        switch (op.getOpType()) {
          case PUT, DELETE-> {
            // writes are recorded separately via WriteMetadata.collect
          }
          case READ -> {
            ReadOp readOp = (ReadOp) op;
            recordReadEvent(readEvents, readEventsByTxn,
                txn.getTxnId(), index, op.getKey(), readOp.getReadValue(), op);
          }
          case RANGE -> {
            rangePositions.add(new Pos(txn.getTxnId(), index));
            RangeOp range = (RangeOp) op;
            for (var entry : range.getKvPairs().entrySet()) {
              recordReadEvent(readEvents, readEventsByTxn,
                  txn.getTxnId(), index, entry.getKey(), entry.getValue(), op);
            }
          }
          case ITER -> {
            iterPositions.add(new Pos(txn.getTxnId(), index));
            IterOp iter = (IterOp) op;
            for (var entry : iter.getKvPairs().entrySet()) {
              recordReadEvent(readEvents, readEventsByTxn,
                  txn.getTxnId(), index, entry.getKey(), entry.getValue(), op);
            }
          }
        }
      }
    }

    ReadMetadata readMetadata = new ReadMetadata(readEvents, readEventsByTxn, rangePositions, iterPositions);
    return new Result(readMetadata);
  }

  public record Result(ReadMetadata reads) {
  }
}
