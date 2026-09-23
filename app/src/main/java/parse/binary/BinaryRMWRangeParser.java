package parse.binary;

import history.KVHistory;
import history.op.KvOpType;
import history.op.KvOperation;
import history.op.RangeOp;
import asg.VersionSet;
import parse.Parser;
import util.Config;
import util.Context;
import util.Profiler;

import java.util.ArrayList;

public class BinaryRMWRangeParser implements Parser {
  private BinaryParser binaryParser;
  private Config cfg;

  public BinaryRMWRangeParser(Config cfg) {
    binaryParser = new BinaryParser(cfg);
    this.cfg = cfg;
  }

  @Override
  public KVHistory parse(String folder) {
    var history = binaryParser.parse(folder);
    // TODO: process the first Range query as a version set
    for (var txn : history) {
      if (txn.getTxnId() == 0)
        continue;

      //
      var mops = txn.getMops();
      var toBeDeletedMops = new ArrayList<KvOperation>();
      for (int i = 0; i < mops.size(); i ++) {
        var mop = mops.get(i);
        if (mop.getOpType() == KvOpType.RANGE) {
          var nextMop = mops.get(i+1);
          // debug
//          if (nextMop.getOpType() != KvOpType.RANGE) {
//            System.out.println();
//          }
          assert nextMop.getOpType() == KvOpType.RANGE;
          ((RangeOp) nextMop).setVersionSet(new VersionSet(((RangeOp) mop).getKvPairs()));
          toBeDeletedMops.add(mop);
          // if found a pair of consecutive RANGE operations, should increment by an extra 1.
          i ++;
        }
      }

      for (var mop : toBeDeletedMops) {
        mops.remove(mop);
      }
    }

    return history;
  }

  public KVHistory apply(Context ctx, String s) {
    Profiler profiler = Profiler.getInstance();
    profiler.startTick("parsing");
    KVHistory history = this.parse(s);
    profiler.endTick("parsing");
    return history;
  }
}
