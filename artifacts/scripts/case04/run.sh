#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/case-study-common.sh"
init_case case04 "$@"
history=$(binary_history case04-mariadb-hybrid/mariadb_20251204_004117)
check_history "$history" B_PL299 binary mariadb_004117_standard 'UNSAT (archived)'
check_history "$history" B_PL299 hybrid_snapshot_binary mariadb_004117_customized 'SAT (archived)'
finish_case
