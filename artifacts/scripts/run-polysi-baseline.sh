#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 FIGURE HISTORY_NAME RUN_NAME" >&2
  exit 2
fi

figure="$1"
history_name="$2"
run_name="$3"
if [[ ! "${figure}" =~ ^fig(11|19)$ ]] || [[ "${history_name}" == */* || "${history_name}" == *..* ]]; then
  echo "ERROR: expected fig11 or fig19 and a direct input-directory name" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
artifact_dir="$(cd "${script_dir}/.." && pwd)"
data_root="${artifact_dir}/data/${figure}/inputs"
results_dir="${RESULTS_DIR:-${artifact_dir}/results}"
host_history="${data_root}/${history_name}"
image="${BOOMSLANG_AE_IMAGE:-boomslang-ae:local}"
memory="${DOCKER_MEMORY:-20g}"
memory_swap="${DOCKER_MEMORY_SWAP:-${memory}}"

if [[ ! -d "${host_history}" ]]; then
  echo "ERROR: copied PolySI history does not exist: ${host_history}" >&2
  exit 2
fi
mkdir -p "${results_dir}"

echo "Image:   ${image}"
echo "History: ${host_history} -> /data/${history_name} (read-only)"
echo "Results: ${results_dir}/${run_name}.{stdout.log,time.txt}"
echo "Limits:  memory=${memory}, memory+swap=${memory_swap}"

set +e
docker run --rm \
  --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  --memory "${memory}" \
  --memory-swap "${memory_swap}" \
  --cpus "${DOCKER_CPUS:-12}" \
  --volume "${data_root}:/data:ro" \
  --volume "${results_dir}:/results" \
  --env "TIMEOUT=${BASELINE_TIMEOUT:-600s}" \
  --env "TIME_LOG=/results/${run_name}.time.txt" \
  --entrypoint bash \
  "${image}" \
  -c 'exec /usr/bin/time -v -o "$TIME_LOG" timeout "$TIMEOUT" java -jar /opt/baselines/polysi/polysi.jar audit --type=cobra "$1"' \
  _ "/data/${history_name}" 2>&1 | tee "${results_dir}/${run_name}.stdout.log"
run_status=${PIPESTATUS[0]}
set -e

python3 "${script_dir}/collect-results.py" \
  "${results_dir}" "${results_dir}/${figure}.csv" --figure "${figure}"
exit "${run_status}"
