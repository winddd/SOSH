package buildASG.builder;

import asg.ASG;
import buildASG.GraphBuildContext;
import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.nodes.GraphNodeId;
import history.KVHistory;
import util.Config;

/**
 * Builds commit order edges (T0 -CB-> Ti).
 * Single responsibility: Add initial transaction ordering constraints.
 */
public class CommitOrderBuilder implements GraphBuilder {
    private final Config config;

    public CommitOrderBuilder(Config config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "CommitOrder";
    }

    @Override
    public boolean isApplicable(GraphBuildContext context) {
        return !config.POLYSI_COMPATIBLE_MODE;
    }

    @Override
    public void build(GraphBuildContext context) {
        if (!isApplicable(context)) {
            return;
        }

        ASG asg = context.getAsg();
        KVHistory history = context.getHistory();
        int numTxns = history.length();

        // T0 -CB-> Ti for any i >= 1
        for (int i = 1; i <= numTxns - 1; i++) {
            asg.getDependencyStore().addEdge(new TypeEdge(GraphNodeId.txn(0), GraphNodeId.txn(i), EdgeType.CB, Key.getNullKey()));
        }

        // If there's a final transaction, add Ti -CB-> Tf for all i < finalTxnId
        history.getFinalTxn().ifPresent(finalTxn -> {
            int finalTxnId = finalTxn.getTxnId();
            for (int i = 1; i < finalTxnId; i++) {
                asg.getDependencyStore().addEdge(new TypeEdge(GraphNodeId.txn(i), GraphNodeId.txn(finalTxnId), EdgeType.CB, Key.getNullKey()));
            }
        });
    }
}
