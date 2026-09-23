import os
import struct

from dns.rdatatype import TYPE0

from parse.kvtxn import KvTxn
from parse.operations import ReadOp, WriteOp
from utils.exceptions import RejectException
from utils.range_utils import check_INT


def construct_all_txns_cobra(LOG_DIR):
    other_logs, initialWrites = load_logs_cobra(LOG_DIR)

    # T0
    T0 = KvTxn(0)
    for key, val in initialWrites.items():
        T0.addOp(WriteOp(key, val))

    session_histories = []
    full_history = [T0]  # T0
    
    txn_id = 1
    for i in range(len(other_logs)):
        txns = other_logs[i]  # a list
        txn_id = construct_single_txn4wrrange_cobra(txns, txn_id)  # line, txn_id, workload, NORMAL_WR = False

        full_history.extend(txns)
        session_histories.append(txns)

    return full_history, session_histories

def construct_single_txn4wrrange_cobra(txns, txn_id):
    for txn in txns:
        txn.setTxnId(txn_id)
        txn_id += 1

        if not check_INT(txn['value']):
            raise RejectException("INT error")

    return txn_id

def load_logs_cobra(LOG_DIR):
    """

    """
    INIT_WRITE_ID = 0xbebeebee
    INIT_TXN_ID = 0xbebeebee
    NULL_TXN_ID = 0xdeadbeef

    COBRA_LOG_PREFIX = "T"
    COBRA_LOG_POSTFIX = ".log"

    other_logs = []

    files = os.listdir(LOG_DIR)
    files = [file for file in files if file.startswith(COBRA_LOG_PREFIX) and file.endswith(COBRA_LOG_POSTFIX)]
    files = [os.path.join(LOG_DIR, file) for file in files if not os.path.isdir(os.path.join(LOG_DIR, file))]

    initialWrites = {}
    for path in files:  # T14.log
        # only open text files, ignore dirs
        cur_txn = None
        txns = []
        with open(path, "rb") as f:
            byte = f.read(1)
            while byte:
                if byte == b'S':
                    assert cur_txn is None
                    original_txn_id = readLong(f)
                    cur_txn = KvTxn()
                elif byte == b'C':
                    original_txn_id = readLong(f)
                    assert cur_txn is not None
                    txns.append(cur_txn)
                    cur_txn = None
                elif byte == b'W':
                    assert cur_txn is not None
                    writeId = readLong(f)
                    key_hash = readLong(f)
                    val_hash = readLong(f)
                    writeOp = WriteOp(key_hash, val_hash)
                    cur_txn.addOp(writeOp)
                elif byte == b'R':
                    assert cur_txn is not None
                    writeTxnid = readLong(f)
                    writeId = readLong(f)
                    key_hash = readLong(f)
                    val_hash = readLong(f)

                    if writeTxnid == INIT_TXN_ID or writeTxnid == NULL_TXN_ID:
                        assert writeId == INIT_WRITE_ID or writeId == NULL_TXN_ID
                        # writeId = key_hash
                        # writeTxnid = 0
                        initialWrites[key_hash] = val_hash
                    op = ReadOp(key_hash, val_hash)
                    cur_txn.addOp(op)
                else:
                    assert False

                byte = f.read(1)

    # lines = lines.replace("\n", ',')
    # assert lines[-1] == ','
    # lines = "[" + lines[:-1] + "]"
        other_logs.append(txns)
    return other_logs, initialWrites

def readLong(f):
    bytes_read = f.read(8)
    n = struct.unpack('>q', bytes_read)[0]
    return n