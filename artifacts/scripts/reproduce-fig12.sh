#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
artifact_dir="$(cd "${script_dir}/.." && pwd)"
data_root="${artifact_dir}/data/fig12"
results_dir="${RESULTS_DIR:-${artifact_dir}/results}"
image="${BOOMSLANG_AE_IMAGE:-boomslang-ae:local}"
memory="${DOCKER_MEMORY:-20g}"
memory_swap="${DOCKER_MEMORY_SWAP:-${memory}}"
cpus="${DOCKER_CPUS:-12}"
elle_timeout="${ELLE_TIMEOUT:-600s}"
elle_heap="${ELLE_JAVA_HEAP:-16g}"

all_durations=(500 1000 1500 2000 2500 3000)
tracescope_ids=()
elle_durations=()
run_tracescope=0

valid_duration() {
  local candidate="$1"
  local duration
  for duration in "${all_durations[@]}"; do
    [[ "${candidate}" == "${duration}" ]] && return 0
  done
  return 1
}

if (( $# == 0 )); then
  run_tracescope=1
  elle_durations=("${all_durations[@]}")
else
  for id in "$@"; do
    case "${id}" in
      f12_elle_*)
        duration="${id#f12_elle_}"
        valid_duration "${duration}" || {
          echo "ERROR: unknown Figure 12 Elle run ID: ${id}" >&2
          exit 2
        }
        elle_durations+=("${duration}")
        ;;
      f12_*)
        duration="${id#f12_}"
        valid_duration "${duration}" || {
          echo "ERROR: unknown Figure 12 run ID: ${id}" >&2
          exit 2
        }
        run_tracescope=1
        tracescope_ids+=("${id}")
        elle_durations+=("${duration}")
        ;;
      *)
        echo "ERROR: unknown Figure 12 run ID: ${id}" >&2
        exit 2
        ;;
    esac
  done
fi

mkdir -p "${results_dir}"
status=0

if (( run_tracescope )); then
  "${script_dir}/run-figure.sh" fig12 "${tracescope_ids[@]}" || status=1
fi

for duration in "${elle_durations[@]}"; do
  run_name="f12_elle_${duration}"
  history="/data/inputs/stolon_${duration}s/history.edn"
  stdout_file="${results_dir}/${run_name}.stdout.log"
  time_file="/results/${run_name}.time.txt"

  echo
  echo "===== ${run_name}: Elle, ${duration} seconds ====="
  echo "Image:   ${image}"
  echo "History: ${data_root}/inputs/stolon_${duration}s/history.edn (read-only)"
  echo "Results: ${stdout_file}, ${results_dir}/${run_name}.time.txt"
  echo "Limits:  timeout=${elle_timeout}, memory=${memory}, memory+swap=${memory_swap}, Java heap=${elle_heap}"

  if ! docker run --rm \
    --platform linux/amd64 \
    --user "$(id -u):$(id -g)" \
    --memory "${memory}" \
    --memory-swap "${memory_swap}" \
    --cpus "${cpus}" \
    --volume "${data_root}:/data:ro" \
    --volume "${results_dir}:/results" \
    --entrypoint bash \
    "${image}" \
    -c 'exec /usr/bin/time -v -o "$1" timeout "$2" xvfb-run -a java "-Xmx$3" -jar "$ELLE_JAR" -m list-append -f edn -c serializable "$4"' \
    _ "${time_file}" "${elle_timeout}" "${elle_heap}" "${history}" \
    2>&1 | tee "${stdout_file}"; then
    status=1
  fi
done

python3 "${script_dir}/collect-results.py" \
  "${results_dir}" \
  "${results_dir}/fig12.csv" \
  --figure fig12

exit "${status}"
