package tools.anomaly_injector;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import common.Key;
import common.binaryhistory.LOG_TXN;
import common.binaryhistory.OP_TYPE;
import common.binaryhistory.SessionHistory;

/**
 * T1: w(x, 1), w(y, 1)
 * T2: r(x, 1), w(x, 2)
 * T3: r(y, 1), w(y, 2)
 * T4: r(x, 2), r(y, 1)
 * T5: r(x, 1), r(y, 2)
 */
public class LongForkInjector extends UnsatCoreInjector {

    @Override
    public Set<Integer> injectUnsatCores(Map<Integer, SessionHistory> m, int numOfUnsatCores) {
        var tids = new ArrayList<>(m.keySet());
        assert !tids.isEmpty();
        int maxTid = Collections.max(tids);
        Set<Integer> targetTids = new HashSet<>();

        for (int i = 0; i < numOfUnsatCores; i ++) {
            int tid = maxTid + i + 1;
            targetTids.add(tid);

            SessionHistory sessionHistory = new SessionHistory();
            Key key1 = new Key(new byte[] { 'k', (byte) (2*i + 1) });
            Key key2 = new Key(new byte[] { 'k', (byte) (2*i + 2) });
            Key v1 = new Key(ByteBuffer.allocate(4).putInt(2*i+1).array());
            Key v2 = new Key(ByteBuffer.allocate(4).putInt(2*i+2).array());

            // T1
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.PUT, key1, v1),
                    createMop(OP_TYPE.PUT, key2, v1))));
            // T2
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.READ, key1, v1),
                    createMop(OP_TYPE.PUT, key1, v2))));
            // T3
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.READ, key2, v1),
                    createMop(OP_TYPE.PUT, key2, v2))));
            // T4
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.READ, key1, v2),
                    createMop(OP_TYPE.READ, key2, v1))));
            // T5
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.READ, key1, v1),
                    createMop(OP_TYPE.READ, key2, v2))));

            m.put(tid, sessionHistory);
        }

        return targetTids;
    }
}
