#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/case-study-common.sh"
init_case case06 "$@"
history=case06-tapir/RealTimeInversion_usedinpaper
check_history "$history" B_SER tapir tapir_rt_ser 'SAT (paper claim; saved paired result not located)'
check_history "$history" B_SSER tapir tapir_rt_sser 'UNSAT (paper claim)'
finish_case
