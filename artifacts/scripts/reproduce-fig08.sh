#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
artifact_dir="$(cd "${script_dir}/.." && pwd)"
repo_root="$(cd "${artifact_dir}/.." && pwd)"
results_dir="${RESULTS_DIR:-${artifact_dir}/results}"
output="${results_dir}/fig08.txt"
image="${BOOMSLANG_AE_IMAGE:-boomslang-ae:local}"

mkdir -p "${results_dir}"
cd "${repo_root}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required to reproduce Figure 8." >&2
  exit 2
fi
if ! docker image inspect "${image}" >/dev/null 2>&1; then
  echo "ERROR: Docker image ${image} is unavailable." >&2
  echo "Run artifacts/scripts/build-image.sh first." >&2
  exit 2
fi
run_cloc() {
  docker run --rm \
    --volume "${repo_root}:/repo:ro" \
    --workdir /repo \
    --entrypoint cloc \
    "${image}" "$@"
}

cloc_code() {
  local language="$1"
  shift
  local value
  value="$(run_cloc --csv --quiet --include-lang="${language}" "$@" \
    | awk -F, '$2 == "SUM" { gsub(/\r/, "", $5); print $5 }')"
  [[ "${value}" =~ ^[0-9]+$ ]] || {
    echo "ERROR: could not obtain ${language} SLOC for: $*" >&2
    exit 2
  }
  printf '%s\n' "${value}"
}

# Original checker implementations. Generated/build output and vendored solver
# trees are outside these source roots.
cobra_original="$(cloc_code Java \
  artifacts/baselines/cobra/source/src/main/java)"
viper_original="$(cloc_code Python \
  artifacts/baselines/viper/source/src)"
polysi_original="$(cloc_code Java \
  artifacts/baselines/polysi/source/src/main/java)"

# Boomslang reimplementations: IR generation, checker-specific pruning, and
# SMT generation. Count every unique file once, then report it under each
# checker that uses it.
boomslang_files=(
  app/src/main/java/compile/v1/CobraSerGraphCompiler.java
  app/src/main/java/compile/v1/ViperSiGraphCompiler.java
  app/src/main/java/compile/v1/PolySiGraphCompiler.java
  app/src/main/java/util/isolation/AllEdgesPruningSpec.java
  app/src/main/java/util/isolation/ExcludeRtoPruningSpec.java
  app/src/main/java/encoders/MonoSATEncoder.java
  app/src/main/java/optimizations/reachability/pruner/PolySIPruner.java
  app/src/main/java/solvers/PolySISolver.java
  app/src/main/java/graphs/graphs/PolySIOriginalGraph.java
  app/src/main/java/graphs/graphs/PolySIMatrixGraph.java
  app/src/main/java/graphs/graphs/interfaces/PolySIABGraph.java
)

declare -A measured=()
while IFS=, read -r language filename _blank _comment code _rest; do
  [[ "${language}" == "Java" ]] || continue
  code="${code//$'\r'/}"
  measured["${filename}"]="${code}"
done < <(run_cloc --csv --quiet --by-file --include-lang=Java "${boomslang_files[@]}")

for file in "${boomslang_files[@]}"; do
  [[ "${measured[${file}]:-}" =~ ^[0-9]+$ ]] || {
    echo "ERROR: cloc did not return Java SLOC for ${file}" >&2
    exit 2
  }
done

cobra_ir="${measured[app/src/main/java/compile/v1/CobraSerGraphCompiler.java]}"
viper_ir="${measured[app/src/main/java/compile/v1/ViperSiGraphCompiler.java]}"
polysi_ir="${measured[app/src/main/java/compile/v1/PolySiGraphCompiler.java]}"
all_edges="${measured[app/src/main/java/util/isolation/AllEdgesPruningSpec.java]}"
exclude_rto="${measured[app/src/main/java/util/isolation/ExcludeRtoPruningSpec.java]}"
monosat_encoder="${measured[app/src/main/java/encoders/MonoSATEncoder.java]}"
polysi_pruner="${measured[app/src/main/java/optimizations/reachability/pruner/PolySIPruner.java]}"
polysi_solver="${measured[app/src/main/java/solvers/PolySISolver.java]}"
polysi_graph="${measured[app/src/main/java/graphs/graphs/PolySIOriginalGraph.java]}"
polysi_matrix="${measured[app/src/main/java/graphs/graphs/PolySIMatrixGraph.java]}"
polysi_interface="${measured[app/src/main/java/graphs/graphs/interfaces/PolySIABGraph.java]}"

cobra_boomslang=$((cobra_ir + all_edges + exclude_rto + monosat_encoder))
viper_boomslang=$((viper_ir + all_edges + exclude_rto + monosat_encoder))
polysi_boomslang=$((polysi_ir + polysi_graph + polysi_matrix + polysi_interface + polysi_pruner + polysi_solver))

{
  echo "checker,implementation,loc"
  echo "Cobra,baseline,${cobra_original}"
  echo "Cobra,current,${cobra_boomslang}"
  echo "Viper,baseline,${viper_original}"
  echo "Viper,current,${viper_boomslang}"
  echo "PolySI,baseline,${polysi_original}"
  echo "PolySI,current,${polysi_boomslang}"
} | tee "${output}"
