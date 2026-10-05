#!/usr/bin/env bash
# Shared Docker runner for saved case-study histories.
set -euo pipefail
scripts_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
artifact_dir=$(cd "$scripts_dir/.." && pwd)

init_case() {
  case_id=$1; shift
  dry_run=false
  if [[ $# == 1 && $1 == --dry-run ]]; then dry_run=true
  elif [[ $# != 0 ]]; then echo 'Usage: run.sh [--dry-run]' >&2; exit 2; fi
  export DATA_ROOT="$artifact_dir/data/case-studies"
  export RUN_TIMEOUT_SECONDS="${RUN_TIMEOUT_SECONDS:-600}"
  export FROM_FILE=false
  failures=0
  if [[ $dry_run == false ]]; then
    command -v python3 >/dev/null
    command -v docker >/dev/null
    mkdir -p "$artifact_dir/results/$case_id"
    export RESULTS_DIR
    RESULTS_DIR=$(mktemp -d "$artifact_dir/results/$case_id/run-XXXXXXXX")
  fi
}

binary_history() {
  local parent="$DATA_ROOT/$1" dirs=()
  [[ -d $parent ]] || { echo "Missing history: $parent" >&2; return 2; }
  mapfile -t dirs < <(find "$parent" -mindepth 1 -maxdepth 1 -type d -name '*_Isolation*' | sort)
  [[ ${#dirs[@]} == 1 ]] || { echo "Expected one workload in $parent" >&2; return 2; }
  printf '%s\n' "${dirs[0]#"$DATA_ROOT/"}"
}

check_history() {
  local history=$1 mode=$2 format=$3 name=$4 expected=$5 policy=${6:--}
  local flags=() rc=0
  [[ -d "$DATA_ROOT/$history" ]] || { echo "Missing history: $history" >&2; return 2; }
  [[ $policy == - ]] || flags+=(-ryow "$policy")
  if [[ $dry_run == true ]]; then
    printf 'DATA_ROOT=%q RUN_TIMEOUT_SECONDS=%q ' "$DATA_ROOT" "$RUN_TIMEOUT_SECONDS"
    printf '%q ' "$scripts_dir/run-one.sh" "$history" "$mode" "$format" "$name" "${flags[@]}"
    printf '\n'
    return
  fi
  "$scripts_dir/run-one.sh" "$history" "$mode" "$format" "$name" "${flags[@]}" || rc=$?
  if ! python3 - "$RESULTS_DIR" "$name" "$history" "$mode" "$format" "$policy" "$expected" "$rc" <<'PY'
import csv
import json
import pathlib
import re
import sys

directory, name, history, mode, fmt, policy, expected, rc = sys.argv[1:]
root = pathlib.Path(directory)
log = root / (name + '.stdout.log')
text = log.read_text(errors='replace') if log.exists() else ''
lines = re.findall(r'^\s*(-?\d+):\s*(true|false|SAT|UNSAT|ERROR|TIMEOUT)\s*$', text, re.M | re.I)
observed = 'ERROR'
if (root / (name + '.timeout.txt')).exists():
    observed = 'TIMEOUT'
elif lines:
    ident, value = lines[-1]
    value = value.upper()
    if value in ('ERROR', 'TIMEOUT'):
        observed = value
    elif int(ident) >= 0 and int(rc) == 0:
        observed = {'TRUE': 'SAT', 'FALSE': 'UNSAT', 'SAT': 'SAT', 'UNSAT': 'UNSAT'}[value]
metrics = {}
profile = root / (name + '.jsonl')
if profile.exists():
    for line in profile.read_text(errors='replace').splitlines():
        try:
            metrics = json.loads(line).get(name, metrics)
        except (ValueError, AttributeError):
            pass
output = root / 'results.csv'
new = not output.exists()
with output.open('a', newline='') as file:
    writer = csv.writer(file)
    if new:
        writer.writerow(['run', 'history', 'mode', 'format', 'ryow_policy',
                         'expected_reference', 'observed', 'exit_code', 'e2e_seconds', 'stdout_log'])
    writer.writerow([name, history, mode, fmt, policy, expected, observed, rc,
                     metrics.get('e2e', ''), log.name])
print(f'{name}: {observed}; reference={expected}')
sys.exit(1 if observed in ('ERROR', 'TIMEOUT') or int(rc) != 0 else 0)
PY
  then failures=$((failures + 1)); fi
}

finish_case() {
  [[ $dry_run == false ]] || return 0
  echo "Results: $RESULTS_DIR/results.csv"
  echo 'Expected references are not proof of anomaly identity; inspect the actual verdict and log.'
  [[ $failures == 0 ]]
}
