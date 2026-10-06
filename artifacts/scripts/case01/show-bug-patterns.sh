#!/usr/bin/env bash
set -euo pipefail
[[ $# == 0 ]] || { echo 'Usage: show-bug-patterns.sh' >&2; exit 2; }
artifact_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
data_root="$artifact_dir/data/case-studies"
memory=${DOCKER_MEMORY:-20g}

# The image's JDK runs this decoder from stdin; no build or output files.
docker run --rm -i --network none --read-only --tmpfs /tmp \
  --user "$(id -u):$(id -g)" \
  --memory "$memory" --memory-swap "${DOCKER_MEMORY_SWAP:-$memory}" \
  --cpus "${DOCKER_CPUS:-12}" \
  --volume "$data_root:/data:ro" --workdir /tmp --entrypoint jshell \
  "${BOOMSLANG_AE_IMAGE:-boomslang-ae:local}" \
  "-J-Xmx${JAVA_HEAP:-18g}" "-R-Xmx${JAVA_HEAP:-18g}" \
  -J-Duser.home=/tmp -R-Duser.home=/tmp --feedback silent \
  --class-path /opt/boomslang/boomslang.jar \
  - <<'JAVA'
import common.Key;
import common.binaryhistory.LOG_TXN;
import common.binaryhistory.Mop;
import common.binaryhistory.SessionHistory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

class ShowCase01BugPatterns {
  // These benchmark histories encode longs in big-endian order with the sign bit flipped.
  static String decode(Key key) {
    if (key == null) return "unset";
    if (key.equals(Key.getNullKey())) return "nil";
    if (key.getBytes().length != Long.BYTES)
      throw new IllegalArgumentException("Expected an 8-byte benchmark key/value");
    return Long.toString(ByteBuffer.wrap(key.getBytes()).getLong() ^ Long.MIN_VALUE);
  }

  static String pairs(Mop op) {
    if (op.kvPairs == null) return "{}";
    return op.kvPairs.entrySet().stream()
      .sorted(Comparator.comparingLong(e -> Long.parseLong(decode(e.getKey()))))
      .map(e -> decode(e.getKey()) + "=" + decode(e.getValue()))
      .collect(Collectors.joining(", ", "{", "}"));
  }

  static String operation(Mop op) {
    return switch (op.op_type) {
      case START_TXN -> "BEGIN";
      case COMMIT_TXN -> "COMMIT";
      case ABORT_TXN -> "ABORT";
      case READ -> (op.forUpdate ? "READ_FOR_UPDATE" : "READ")
        + " key=" + decode(op.key1) + " -> " + decode(op.value);
      case PUT, INSERT, UPDATE -> op.op_type + " key=" + decode(op.key1)
        + " value=" + decode(op.value) + " success=" + op.update_succ;
      case DELETE -> "DELETE key=" + decode(op.key1) + " success=" + op.update_succ;
      case RANGE, ITER -> op.op_type + " key1=" + decode(op.key1)
        + " key2=" + decode(op.key2) + " val1=" + decode(op.v1)
        + " val2=" + decode(op.v2) + " forUpdate=" + op.forUpdate
        + " returned=" + pairs(op);
      default -> op.op_type + " key=" + decode(op.key1) + " value=" + decode(op.value)
        + " read_v=" + decode(op.read_v);
    };
  }

  static Path workload(String relativeRun) throws Exception {
    Path run = Path.of("/data", relativeRun);
    try (var entries = Files.list(run)) {
      var matches = entries.filter(Files::isDirectory)
        .filter(p -> p.getFileName().toString().contains("_Isolation")).toList();
      if (matches.size() != 1)
        throw new IllegalArgumentException("Expected one workload directory in " + run);
      return matches.get(0);
    }
  }

  static void transaction(Path history, String label, String file, int position,
      Set<Integer> relevantOps) throws Exception {
    SessionHistory session = SessionHistory.loadFromFile(history.resolve(file).toFile());
    if (position < 0 || position >= session.size())
      throw new IllegalArgumentException("Missing transaction " + position + " in " + file);
    LOG_TXN txn = session.getKthTxn(position);
    System.out.println("\n" + label + ": " + file + ", transaction " + position);
    for (int i = 0; i < txn.getMops().size(); i++) {
      System.out.printf("%s op %02d: %s%n", relevantOps.contains(i) ? "*" : " ",
        i, operation(txn.getMops().get(i)));
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Positions are zero-based; * marks bug-pattern operations.");

    Path tidb = workload("case01-tidb-candidates/tidb_20251203_202543");
    System.out.println("\n=== TiDB read-after-update ===\nHistory: " + tidb);
    transaction(tidb, "A", "J31.log", 239, Set.of(1, 3, 4, 5, 9));
    transaction(tidb, "B", "J32.log", 248, Set.of(8, 12));
  }
}
int exitCode = 0;
try { ShowCase01BugPatterns.main(new String[0]); }
catch (Throwable error) { error.printStackTrace(); exitCode = 1; }
/exit exitCode
JAVA
