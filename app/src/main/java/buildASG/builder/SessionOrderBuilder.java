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

/**
 * Builds session order edges between transactions in the same session.
 * Single responsibility: Add session ordering constraints.
 */
public class SessionOrderBuilder implements GraphBuilder {

    @Override
    public String getName() {
        return "SessionOrder";
    }

    @Override
    public boolean isApplicable(GraphBuildContext context) {
        return context.getOptions().isSessionOrder();
    }

    @Override
    public void build(GraphBuildContext context) {
        if (!isApplicable(context)) {
            return;
        }

        ASG asg = context.getAsg();
        KVHistory history = context.getHistory();

        List<Long> threadIds = history.getThreadIds();
        for (long tid : threadIds) {
            List<KVTxn> txns = history.getTxnsByTid(tid);
            addSessionOrderEdges(asg, txns);
        }
    }

    private void addSessionOrderEdges(ASG asg, List<KVTxn> txns) {
        for (int i = 0; i < txns.size() - 1; i++) {
            asg.getDependencyStore().addEdge(new TypeEdge(
                    GraphNodeId.txn(txns.get(i).getTxnId()),
                    GraphNodeId.txn(txns.get(i + 1).getTxnId()),
                    EdgeType.CB,
                    Key.getNullKey()));
        }
    }
}
