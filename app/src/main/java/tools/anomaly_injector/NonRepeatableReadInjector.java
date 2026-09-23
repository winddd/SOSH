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
 * T1: w(k, v1)
 * T2: w(k, v2)
 * T3: r(k, v1), r(k, v2)
 */
public class NonRepeatableReadInjector extends UnsatCoreInjector {

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
            Key key = new Key(new byte[] { 'k', (byte) i });
            Key v1 = new Key(ByteBuffer.allocate(4).putInt(2*i+1).array());
            Key v2 = new Key(ByteBuffer.allocate(4).putInt(2*i+2).array());

            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.PUT, key, v1))));
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.PUT, key, v2))));
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.READ, key, v1),
                    createMop(OP_TYPE.READ, key, v2))));

            m.put(tid, sessionHistory);
        }

        return targetTids;
    }
}
