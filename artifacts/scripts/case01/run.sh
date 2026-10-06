#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/case-study-common.sh"
[[ $# == 0 ]] || { echo 'Usage: run.sh' >&2; exit 2; }
init_case case01
run=tidb_20251203_202543
history=$(binary_history "case01-tidb-candidates/$run")
check_history "$history" B_PL299 binary "${run}_rr" 'UNSAT (archived; matching bug-pattern witness)'
finish_case
