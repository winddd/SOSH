# Case 6: Tapir timestamp inversion

From the SOSH2 repository root, after building the artifact image:

```bash
source artifacts/env.sh.example
artifacts/scripts/case06/run.sh
```

Both checks use the same input:
`artifacts/data/case-studies/case06-tapir/RealTimeInversion_usedinpaper/`.

| Checker | Parser | Expected by the paper |
|---|---|
| B_SER |  SAT |
| B_SSER | UNSAT |

B_SSER automatically includes real-time ordering from the Tapir timestamps;
B_SER checks serializability without that extra ordering. Both retain session order.
Both checks have been verified: B_SER returns SAT and B_SSER returns UNSAT.

