package buildASG.builder;

import java.util.List;

import asg.ASG;
import buildASG.GraphBuildContext;
import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.nodes.GraphNodeId;
import history.KVHistory;
import history.KVTxn;
import util.Config;

/**
 * Builds real-time ordering constraints between transactions.
 * Single responsibility: Add real-time order edges based on commit/begin
 * timestamps.
 */
public class RealtimeOrderBuilder implements GraphBuilder {
  private final Config config;

  public RealtimeOrderBuilder(Config config) {
    this.config = config;
  }

  @Override
  public String getName() {
    return "RealtimeOrder";
  }

  @Override
  public boolean isApplicable(GraphBuildContext context) {
    boolean requiredByIsolationLevel = config.RUNMODE.getIsolationLevel().enforceRealtimeOrder();
    boolean explicitlyRequested = context.getOptions().isRealtimeOrder();
    return requiredByIsolationLevel | explicitlyRequested;
  }

  @Override
  public void build(GraphBuildContext context) {
    ASG asg = context.getAsg();
    KVHistory history = context.getHistory();

    // if this isolation level requires real-time order,
    // or users manually specify it.
    List<Long> clientIds = history.getThreadIds();

    for (var client1 : clientIds) {
      for (var client2 : clientIds) {
        if (!client1.equals(client2)) {
          processClientPair(asg, history, client1, client2);
        }
      }
    }
  }

  private void processClientPair(ASG asg, KVHistory history, long client1, long client2) {
    List<KVTxn> txns1 = history.getTxnsByTid(client1);
    List<KVTxn> txns2 = history.getTxnsByTid(client2);

    int j = 0;
    // sliding window algorithm
    for (int i = 0; i < txns1.size(); ++i) {
      while (j + 1 < txns2.size() && txns2.get(j).getBeginTS() <= txns1.get(i).getCommitTS()) {
        ++j;
      }

      // either j is the last txn in `txns2`, or j is the first txn who starts after
      // txns1.get(i) commits.
      var txn1i = txns1.get(i);
      var txn2j = txns2.get(j);
      if (txn2j.getBeginTS() > txn1i.getCommitTS()) {
        asg.getDependencyStore().addEdge(new TypeEdge(
            GraphNodeId.txn(txns1.get(i).getTxnId()),
            GraphNodeId.txn(txns2.get(j).getTxnId()),
            EdgeType.RTO,
            Key.getNullKey()));
      }

      if (j == txns2.size() - 1) {
        break;
      }
    }
  }
}
