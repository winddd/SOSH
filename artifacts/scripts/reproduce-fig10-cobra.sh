#!/usr/bin/env bash
set -euo pipefail
runner="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-cobra-baseline.sh"

declare -A histories=(
  [f10_cobra_tpcc]=tpcc_10k
  [f10_cobra_twitter]=twitter_10k
  [f10_cobra_blindw]=rw_10k
  [f10_cobra_rubis]=rubis_10k
)
ids=("$@")
[[ ${#ids[@]} -gt 0 ]] || ids=(f10_cobra_tpcc f10_cobra_twitter f10_cobra_blindw f10_cobra_rubis)
overall_status=0
for id in "${ids[@]}"; do
  [[ -n "${histories[$id]:-}" ]] || { echo "Unknown Figure 10 Cobra id: ${id}" >&2; exit 2; }
  "${runner}" fig10 "${histories[$id]}" "${id}" || overall_status=1
done
exit "${overall_status}"
