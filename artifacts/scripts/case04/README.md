# Case 4: MariaDB transaction-splitting customization

From the SOSH2 repository root, after building the artifact image:

```bash
source artifacts/env.sh.example
artifacts/scripts/case04/run.sh
```

The same `case04-mariadb-hybrid/mariadb_20251204_004117` input is checked twice:

| Checker | Parser | Archived verdict |
|---|---|---|
| B_PL299 | binary (without splitting) | UNSAT |
| B_PL299 | hybrid_snapshot_binary (with splitting) | SAT |

The input consists of Java-serialized binary `J*.log` files. The customized check
uses `-format hybrid_snapshot_binary`, which invokes
`app/src/main/java/parse/binary/BinaryHybridSnapshotParser.java`,
mainly `splitIntoEpochs`.

The paper's 85 LOC refers to the transaction-splitting logic. For this reproduction, the corresponding
implementation is `BinaryHybridSnapshotParser.splitIntoEpochs()` in
`app/src/main/java/parse/binary/BinaryHybridSnapshotParser.java` (lines 138–241).
Count its LOC by executing 
```
sed -n '138,241p' app/src/main/java/parse/binary/BinaryHybridSnapshotParser.java |
  docker run --rm -i --entrypoint cloc boomslang-ae:local \
  - --stdin-name=splitIntoEpochs.java
```