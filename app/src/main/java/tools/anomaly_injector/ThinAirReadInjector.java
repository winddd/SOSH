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
 * T1: r(k, v) and there is no w(k,v) in the history or in the initialState
 */
public class ThinAirReadInjector extends UnsatCoreInjector {

    @Override
    public Set<Integer> injectUnsatCores(Map<Integer, SessionHistory> m, int numOfUnsatCores) {
        var tids = new ArrayList<>(m.keySet());
        assert !tids.isEmpty();
        int maxTid = Collections.max(tids);
        Set<Integer> targetTids = new HashSet<>();

        int tid = maxTid + 1;
        targetTids.add(tid);

        // store the unsat core in a new session.
        SessionHistory sessionHistory = new SessionHistory();
        Key key = new Key(new byte[] { 'k', 1 });
        Key v1 = new Key(ByteBuffer.allocate(4).putInt(1).array());

        sessionHistory.commitSingleTxn(new LOG_TXN(false, false,
                List.of(createMop(OP_TYPE.READ, key, v1))));

        // add the session to the history
        m.put(tid, sessionHistory);

        return targetTids;
    }
}
