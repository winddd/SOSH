#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 HISTORY_RELATIVE_TO_DATA_ROOT MODE FORMAT RUN_NAME [extra Boomslang flags]" >&2
  exit 2
fi

history_rel="$1"
mode="$2"
format="$3"
run_name="$4"
shift 4

if [[ -z "${DATA_ROOT:-}" ]]; then
  echo "ERROR: set DATA_ROOT to a copied figure directory under artifacts/data/." >&2
  exit 2
fi

if [[ "${history_rel}" = /* || "${history_rel}" == *".."* ]]; then
  echo "ERROR: history must be a safe path relative to DATA_ROOT: ${history_rel}" >&2
  exit 2
fi

host_history="${DATA_ROOT%/}/${history_rel}"
if [[ ! -e "${host_history}" && ! -e "${host_history}_I.txt" ]]; then
  echo "ERROR: history path/prefix does not exist: ${host_history}" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
artifact_dir="$(cd "${script_dir}/.." && pwd)"
results_dir="${RESULTS_DIR:-${artifact_dir}/results}"
image="${BOOMSLANG_AE_IMAGE:-boomslang-ae:local}"
heap="${JAVA_HEAP:-16g}"
memory="${DOCKER_MEMORY:-20g}"
memory_swap="${DOCKER_MEMORY_SWAP:-${memory}}"

mkdir -p "${results_dir}"

jsonl="${run_name}.jsonl"
stdout_log="${results_dir}/${run_name}.stdout.log"
time_log="${run_name}.time.txt"
timeout_record="${results_dir}/${run_name}.timeout.txt"

echo "Image:   ${image}"
echo "History: ${host_history} -> /data/${history_rel} (read-only)"
echo "Results: ${results_dir}"
echo "Limits:  memory=${memory}, memory+swap=${memory_swap}, Java heap=${heap}"

container_name="boomslang-ae-${run_name}-$$"
docker_command=(docker run --rm --name "${container_name}" \
  --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  --memory "${memory}" \
  --memory-swap "${memory_swap}" \
  --cpus "${DOCKER_CPUS:-12}" \
  --env "JAVA_HEAP=${heap}" \
  --env "TIME_LOG=/results/${time_log}" \
  --volume "${DATA_ROOT%/}:/data:ro" \
  --volume "${results_dir}:/results" \
  "${image}" \
  -h "/data/${history_rel}" \
  -config /opt/boomslang/config.yaml \
  -format "${format}" \
  -fromFile "${FROM_FILE:-false}" \
  -solver monosat \
  -m "${mode}" \
  -run_name "${run_name}" \
  -output "/results/${jsonl}" \
  -session \
  "$@")

if [[ -n "${RUN_TIMEOUT_SECONDS:-}" ]]; then
  [[ "${RUN_TIMEOUT_SECONDS}" =~ ^[1-9][0-9]*$ ]] || {
    echo "ERROR: RUN_TIMEOUT_SECONDS must be a positive integer" >&2
    exit 2
  }
  echo "Timeout: ${RUN_TIMEOUT_SECONDS}s"
  rm -f "${timeout_record}"
  started_at=${SECONDS}
  set +e
  timeout --signal=TERM --kill-after=10s "${RUN_TIMEOUT_SECONDS}s" \
    "${docker_command[@]}" 2>&1 | tee "${stdout_log}"
  run_status=${PIPESTATUS[0]}
  elapsed=$((SECONDS - started_at))
  set -e
  if [[ "${run_status}" -eq 124 || "${run_status}" -eq 137 ]]; then
    docker rm -f "${container_name}" >/dev/null 2>&1 || true
  fi
  if [[ "${run_status}" -eq 124 || ( "${run_status}" -eq 137 && "${elapsed}" -ge "${RUN_TIMEOUT_SECONDS}" ) ]]; then
    printf '%s\n' "${RUN_TIMEOUT_SECONDS}" > "${timeout_record}"
    echo "RUN_TIMEOUT_SECONDS=${RUN_TIMEOUT_SECONDS} exceeded" | tee -a "${stdout_log}"
  fi
  exit "${run_status}"
fi

"${docker_command[@]}" 2>&1 | tee "${stdout_log}"
