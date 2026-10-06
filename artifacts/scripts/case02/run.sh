#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/case-study-common.sh"
init_case case02 "$@"
for run in postgres12_20251129_005732 postgres12_20251129_232417_withprw; do
  history=$(binary_history "case02-postgres-binary-candidates/$run")
  flags=()
  [[ $run != postgres12_20251129_005732 ]] || flags+=(-no_hashmap)
  "$scripts_dir/run-one.sh" "$history" B_SER binary "$run" "${flags[@]}" || failures=$((failures + 1))
done
history=$(binary_history case02-mariadb-candidates/mariadb106_20251202_015038)
"$scripts_dir/run-one.sh" "$history" B_PL299 hybrid_snapshot_binary mariadb106_20251202_015038 || failures=$((failures + 1))
echo "Checker logs: $RESULTS_DIR"
[[ $failures == 0 ]]
