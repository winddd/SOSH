#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tracescope_runner="${script_dir}/run-figure.sh"
cobra_runner="${script_dir}/reproduce-fig10-cobra.sh"

tracescope_ids=()
cobra_ids=()
for id in "$@"; do
  if [[ "${id}" == f10_cobra_* ]]; then
    cobra_ids+=("${id}")
  else
    tracescope_ids+=("${id}")
  fi
done

status=0
if (( $# == 0 || ${#tracescope_ids[@]} > 0 )); then
  "${tracescope_runner}" fig10 "${tracescope_ids[@]}" || status=1
fi
if (( $# == 0 || ${#cobra_ids[@]} > 0 )); then
  "${cobra_runner}" "${cobra_ids[@]}" || status=1
fi
exit "${status}"
