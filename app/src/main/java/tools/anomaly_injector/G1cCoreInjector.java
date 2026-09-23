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

public class G1cCoreInjector extends UnsatCoreInjector {

    @Override
    public Set<Integer> injectUnsatCores(Map<Integer, SessionHistory> m, int numOfUnsatCores) {
        var tids = new ArrayList<>(m.keySet());
        assert !tids.isEmpty();
        int maxTid = Collections.max(tids);
        Set<Integer> targetTids = new HashSet<>();

        for (int i = 0; i < numOfUnsatCores; i++) {
            int tid = maxTid + i + 1;
            targetTids.add(tid);

            SessionHistory sessionHistory = new SessionHistory();
            byte[] keyBytes = new byte[] { 'k', (byte) i };
            Key key = new Key(keyBytes);
            byte[] bytes1 = ByteBuffer.allocate(4).putInt(3 * i + 1).array();
            Key v1 = new Key(bytes1);
            byte[] bytes2 = ByteBuffer.allocate(4).putInt(3 * i + 2).array();
            Key v2 = new Key(bytes2);
            byte[] bytes3 = ByteBuffer.allocate(4).putInt(3 * i + 3).array();
            Key v3 = new Key(bytes3);

            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                    List.of(
                            createMop(OP_TYPE.READ, key, v1),
                            createMop(OP_TYPE.PUT, key, v2))));
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                    List.of(
                            createMop(OP_TYPE.READ, key, v2),
                            createMop(OP_TYPE.PUT, key, v3))));
            sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                    List.of(
                            createMop(OP_TYPE.READ, key, v3),
                            createMop(OP_TYPE.PUT, key, v1))));

            m.put(tid, sessionHistory);
        }

        return targetTids;
    }

}
