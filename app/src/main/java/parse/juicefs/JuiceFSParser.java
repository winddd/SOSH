package parse.juicefs;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import common.Key;
import history.KVHistory;
import history.KVTxn;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import parse.Parser;
import parse.juicefs.operation.*;
import util.Config;
import util.Context;
import util.Profiler;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import parse.ParserUtils;

@Slf4j
public class JuiceFSParser implements Parser {
    private final Config cfg;
    private BiMap<Long, Integer> txnMap = HashBiMap.create();

    public JuiceFSParser(Config cfg) {
        this.cfg = cfg;
    }

    /**
     *
     * @param folder
     * @return
     */
    @Override
    public KVHistory parse(String folder) {
        log.info("Start history parsing...");
        var files = ParserUtils.findLogs(folder, "T", ".log");

        var history = new KVHistory(cfg);
        var initWrites = new HashMap<Key, Key>();
        for (File file : files) {
            extractLog(folder, file, history, initWrites);
        }

        // process to
        var txn = history.getKthTxn(0);
        txn.setState(initWrites);
        txn.setStatus(KVTxn.TransactionStatus.COMMIT);
        for (var p : initWrites.entrySet()) {
            txn.addOperations(new history.op.PutOp(p.getKey(), p.getValue()));
        }

        return history;
    }

    @SneakyThrows
    public void extractLog(String folder,
            File file,
            KVHistory history,
            Map<Key, Key> initWrites) {
        var fileName = file.getName();
        // T55.log
        long threadId = Long.parseLong(fileName.substring(1, fileName.length() - 4));

        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader br = new BufferedReader(new FileReader(Paths.get(folder, fileName).toString()))) {
            String line;
            int numTxn = 0;
            while ((line = br.readLine()) != null) {
                LogTxn logTxn = mapper.readValue(line, LogTxn.class);
                KVTxn txn = KVTxn.createRegular(threadId, false);
                int num_ops = logTxn.ops.size();
                for (int i = 0; i < num_ops; ++ i) {
                    LogOp logOp = logTxn.ops.get(i);

                    switch (logOp.type) {
                        case "get": {
                            GetOp getOp = mapper.convertValue(logOp.op, GetOp.class);
                            txn.addOperations(new history.op.ReadOp(getOp.key, getOp.val));
                        } break;
                        case "put": {
                            PutOp putOp = mapper.convertValue(logOp.op, PutOp.class);
                            txn.addOperations(new history.op.PutOp(putOp.key, putOp.val));
                        } break;
                        case "delete": {
                            DeleteOp deleteOp = mapper.convertValue(logOp.op, DeleteOp.class);
                            txn.addOperations(new history.op.DeleteOp(deleteOp.key));
                        } break;
                        case "iter_kth": {
                            IterOpKth ithIterOp = mapper.convertValue(logOp.op, IterOpKth.class);
                            if (ithIterOp.kth == 0) { // need to traverse the latter operations and merge them together
                                var iterOp = new history.op.IterOp(new Key(ithIterOp.start_key), new Key(ithIterOp.end_key), new HashMap<>());
                                int counter = 1;
                                for (int j = i+1; j < num_ops; ++ j) {
                                    var jthOp = logTxn.ops.get(j);
                                    if (jthOp.type.equals("iter_kth")) {
                                        IterOpKth jthIterOp = mapper.convertValue(jthOp.op, IterOpKth.class);
                                        if (jthIterOp.kth == counter && jthIterOp.start_key.equals(ithIterOp.start_key) && jthIterOp.end_key.equals(ithIterOp.end_key)) {
                                            iterOp.addKeyValuePair(new Key(jthIterOp.key), new Key(jthIterOp.val));
                                            counter ++;
                                        }
                                    }
                                }
                                txn.addOperations(iterOp);
                            }
                            // Ignore if kth != 0 since they already be handled by those IterOpKth whose kth==0.
                        }
                            break;
                        case "iter": {
                            IterOp iterOp = mapper.convertValue(logOp.op, IterOp.class);
                            Map<Key, Key> kvs = new HashMap<>();

                            // debug
//                            if (iterOp.keys == null || iterOp.vals == null) {
//                                System.out.println();
//                            }
                            assert iterOp.keys.size() == iterOp.vals.size();
                            for (int j = 0; j < iterOp.keys.size(); ++ j) {
                                var key = new Key(iterOp.keys.get(j));
                                var val = new Key(iterOp.vals.get(j));

                                var whichMap = numTxn > 0? kvs : initWrites;
                                whichMap.put(key, val);
                            }

                            if (numTxn > 0) { // not initial txn
                                txn.addOperations(new history.op.IterOp(new Key(iterOp.start_key), new Key(iterOp.end_key), kvs));
                            }
                        }
                            break;
                        default:
                            assert false;
                    }
                }

                if (!txnMap.containsKey(logTxn.txn_id)) { // deduplicate
                    // juicefs log may output the same txn twice for some reason
                    history.addRegularTxn(threadId, txn); // history will allocate a txn id
                    txnMap.put(logTxn.txn_id, txn.getTxnId());
                    numTxn ++;
                }

                // for debugging, should be removed
//                if (numTxn >= 2) {
//                    break;
//                }
            }
        }
    }

//    boolean sameStartEndKeys()

    @Override
    public KVHistory apply(Context context, String s) {
        Profiler profiler = Profiler.getInstance();
        profiler.startTick("parsing");
        KVHistory history = this.parse(s);
        profiler.endTick("parsing");

        return history;
    }
}
