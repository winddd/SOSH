package buildASG.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import asg.ASG;
import buildASG.GraphBuildContext;
import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.nodes.GraphNodeId;
import history.KVHistory;
import history.KVTxn;
import util.exception.RejectException;

/**
 * Builds expected execution order constraints between transactions.
 * Single responsibility: Add expected ordering edges based on transaction
 * timestamps.
 */
public class ExpectedOrderBuilder implements GraphBuilder {

    @Override
    public String getName() {
        return "ExpectedOrder";
    }

    @Override
    public boolean isApplicable(GraphBuildContext context) {
        return context.getOptions().isExpectedExecutionOrder();
    }

    @Override
    public void build(GraphBuildContext context) throws RejectException {
        if (!isApplicable(context)) {
            return;
        }

        ASG asg = context.getAsg();
        KVHistory history = context.getHistory();

        List<KVTxn> allTxns = new ArrayList<>(history.getAllTxns()); // make a copy
        if (!allTxns.isEmpty()) {
            allTxns.remove(0); // Remove T0
        }
        Collections.sort(allTxns, new KVTxnComparator());

        for (int i = 0; i < allTxns.size() - 1; i++) {
            var txn1 = allTxns.get(i);
            var txn2 = allTxns.get(i + 1);

            asg.getDependencyStore().addEdge(new TypeEdge(
                    GraphNodeId.txn(txn1.getTxnId()),
                    GraphNodeId.txn(txn2.getTxnId()),
                    EdgeType.EXPECTED,
                    Key.getNullKey()));

            if (txn2.getCommitTS() < txn1.getBeginTS()) {
                throw new RejectException("Violated real-time order");
            }
        }
    }

    private static class KVTxnComparator implements Comparator<KVTxn> {
        @Override
        public int compare(KVTxn o1, KVTxn o2) {
            if (o1.getTxnTS() < o2.getTxnTS()) {
                return -1;
            } else if (o1.getTxnTS() > o2.getTxnTS()) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
