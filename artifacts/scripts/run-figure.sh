#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 FIGURE_ID [RUN_ID ...]" >&2
  exit 2
fi

figure="$1"
shift
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
artifact_dir="$(cd "${script_dir}/.." && pwd)"
manifest="${artifact_dir}/manifests/${figure}-runs.tsv"
data_root="${artifact_dir}/data/${figure}"
results_dir="${RESULTS_DIR:-${artifact_dir}/results}"

[[ -f "${manifest}" ]] || { echo "ERROR: missing manifest: ${manifest}" >&2; exit 2; }
[[ -d "${data_root}" ]] || { echo "ERROR: missing staged data: ${data_root}" >&2; exit 2; }

declare -A selected=()
for id in "$@"; do selected["${id}"]=1; done

matched=0
overall_status=0
while IFS=$'\t' read -r id label history source mode format from_file extra_flags paper_value note; do
  [[ "${id}" == "id" || -z "${id}" ]] && continue
  if (( $# > 0 )) && [[ -z "${selected[${id}]:-}" ]]; then continue; fi
  matched=$((matched + 1))
  echo
  echo "===== ${id}: ${label}; paper/reference=${paper_value} ====="
  extra=()
  if [[ -n "${extra_flags}" && "${extra_flags}" != "-" ]]; then
    read -r -a extra <<< "${extra_flags}"
  fi
  if ! DATA_ROOT="${data_root}" FROM_FILE="${from_file}" \
    "${script_dir}/run-one.sh" "${history}" "${mode}" "${format}" "${id}" "${extra[@]}"; then
    overall_status=1
  fi
done < "${manifest}"

(( matched > 0 )) || { echo "ERROR: no matching run IDs" >&2; exit 2; }

python3 "${script_dir}/collect-results.py" \
  "${results_dir}" \
  "${results_dir}/${figure}.csv" \
  --figure "${figure}"
exit "${overall_status}"
