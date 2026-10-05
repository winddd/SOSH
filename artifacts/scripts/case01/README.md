# Case 1: TiDB read-after-update candidates

From the SOSH2 repository root:

```bash
source artifacts/env.sh.example
artifacts/scripts/build-image.sh   # once; rebuild after Java changes
artifacts/scripts/case01/run.sh
```

The input is `tidb_20251203_202543` under
`artifacts/data/case-studies/case01-tidb-candidates/`.
The script checks this history with `B_PL299` (repeatable read) and `B_SI`
(snapshot isolation), both using the ordinary binary parser. Both return UNSAT;
a `RejectException` is a normal rejection indicating an isolation violation.
Operation-level inspection of the RR history found the same-value-write/old-snapshot
pattern described below.

Results: `artifacts/results/case01/run-*/results.csv`, plus per-check stdout,
profiling JSONL and timing files. 

Bug report: [TiDB #64888](https://github.com/pingcap/tidb/issues/64888)
(TiDB 8.5.4, pessimistic RR).

## Why the RR history matches the reported bug pattern

History:
`artifacts/data/case-studies/case01-tidb-candidates/tidb_20251203_202543/RANDOM_500txn_8op_2threads_D0_P30_R30_RMW30_RANGE10_ITER0_IsolationRR/`.

We decoded the serialized `J*.log` operations and found this witness on key `160`.
Transaction and operation indices below are **zero-based positions within the
session file**, not the checker's reassigned transaction IDs. Numeric keys/values
are decoded using the benchmark's sign-bit-flipped long encoding.

| Transaction | Operation index | Recorded operation |
|---|---|---|
| A: `J31.log`, transaction 239 | 1 | Ordinary READ(160) returns **160** (old snapshot value). |
| B: `J32.log`, transaction 248 | 8, then 12 | Successful PUT(160, **159**), then COMMIT; 159 is B's final write to this key. |
| A | 3 | READ(160) with `forUpdate=true` returns **159** (current-read value); A has not yet written this key. |
| A | 4 and 5 | Two successful PUT(160, **159**) operations, matching the current value. |
| A | 9 | Ordinary READ(160) still returns **160**, not A's most recent write, 159. |

This contains the report's essential conditions: another transaction commits the
new value, A observes that value through a current read, A successfully writes the
same value, and A's subsequent ordinary read returns its old snapshot value.

The trace records PUT, not literal SQL text. The benchmark's MySQL/TiDB adapter
implements PUT using `INSERT ... ON DUPLICATE KEY UPDATE`; for an existing key,
this takes the update path. Thus it is not a byte-for-byte replay of the report's
plain UPDATE example, but it exhibits the same same-value-update/read-after-update
pattern. A later PUT(160, 160) at operation 10 does not explain away the violation:
it occurs **after** the stale read at operation 9. There are additional operations
on other keys, so the witness is embedded in a larger history rather than a minimal
two-transaction SQL reproduction. The full input has 503 serialized transactions.
