#!/usr/bin/env bash
set -euo pipefail

export RUN_TIMEOUT_SECONDS=600

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tracescope_runner="${script_dir}/run-figure.sh"
viper_runner="${script_dir}/reproduce-fig11-viper.sh"
polysi_runner="${script_dir}/reproduce-fig11-polysi.sh"

tracescope_ids=()
viper_ids=()
polysi_ids=()
for id in "$@"; do
  case "${id}" in
    f11_viper_*) viper_ids+=("${id}") ;;
    f11_polysi_*) polysi_ids+=("${id}") ;;
    *) tracescope_ids+=("${id}") ;;
  esac
done

status=0
if (( $# == 0 || ${#tracescope_ids[@]} > 0 )); then
  "${tracescope_runner}" fig11 "${tracescope_ids[@]}" || status=1
fi
if (( $# == 0 || ${#viper_ids[@]} > 0 )); then
  "${viper_runner}" "${viper_ids[@]}" || status=1
fi
if (( $# == 0 || ${#polysi_ids[@]} > 0 )); then
  "${polysi_runner}" "${polysi_ids[@]}" || status=1
fi
exit "${status}"
