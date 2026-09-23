#!/usr/bin/env bash
set -euo pipefail
export BASELINE_TIMEOUT=600s
runner="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-polysi-baseline.sh"

declare -A histories=(
  [f11_polysi_blindw]=yuga_si_blindw_50k
  [f11_polysi_rubis]=yuga_si_rubis_50k
  [f11_polysi_twitter]=yuga_si_twitter_50k
  [f11_polysi_tpcc]=yuga_si_tpcc_50k
)
ids=("$@")
[[ ${#ids[@]} -gt 0 ]] || ids=(f11_polysi_blindw f11_polysi_rubis f11_polysi_twitter f11_polysi_tpcc)
overall_status=0
for id in "${ids[@]}"; do
  [[ -n "${histories[$id]:-}" ]] || { echo "Unknown Figure 11 PolySI id: ${id}" >&2; exit 2; }
  "${runner}" fig11 "${histories[$id]}" "${id}" || overall_status=1
done
exit "${overall_status}"
