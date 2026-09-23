#!/usr/bin/env bash
set -euo pipefail
export BASELINE_TIMEOUT=600s
runner="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-viper-baseline.sh"

declare -A histories=(
  [f11_viper_blindw]=yuga_si_blindw_50k
  [f11_viper_rubis]=yuga_si_rubis_50k
  [f11_viper_twitter]=yuga_si_twitter_50k
  [f11_viper_tpcc]=yuga_si_tpcc_50k
)
ids=("$@")
[[ ${#ids[@]} -gt 0 ]] || ids=(f11_viper_blindw f11_viper_rubis f11_viper_twitter f11_viper_tpcc)
overall_status=0
for id in "${ids[@]}"; do
  [[ -n "${histories[$id]:-}" ]] || { echo "Unknown Figure 11 Viper id: ${id}" >&2; exit 2; }
  "${runner}" fig11 "${histories[$id]}" "${id}" || overall_status=1
done
exit "${overall_status}"
