package buildASG.builder;

import asg.ASG;
import buildASG.GraphBuildContext;
import history.KVHistory;
import history.KVTxn;

/**
 * Builds hints from transaction operations.
 * Single responsibility: Extract and store operation context hints.
 */
public class HintsBuilder implements GraphBuilder {

    @Override
    public String getName() {
        return "Hints";
    }

    @Override
    public boolean isApplicable(GraphBuildContext context) {
        return true; // Always needed
    }

    @Override
    public void build(GraphBuildContext context) {
        ASG asg = context.getAsg();
        KVHistory history = context.getHistory();

        asg.resetHints();
        for (KVTxn txn : history) {
            asg.getHints().initializeForTxn(txn.getTxnId(), txn.getMops().size());
            for (int i = 0; i < txn.getMops().size(); i++) {
                var op = txn.getKthOperation(i);
                asg.getHints().addHint(txn.getTxnId(), i, op.getCtx());
            }
        }
    }
}
