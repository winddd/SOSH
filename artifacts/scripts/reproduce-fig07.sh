#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
artifact_dir="$(cd "${script_dir}/.." && pwd)"
repo_root="$(cd "${artifact_dir}/.." && pwd)"
results_dir="${RESULTS_DIR:-${artifact_dir}/results}"
output="${results_dir}/fig07.txt"
image="${BOOMSLANG_AE_IMAGE:-boomslang-ae:local}"

files=(
  app/src/main/java/compile/v2/compilers/StrictSerCompiler.java
  app/src/main/java/util/isolation/AllEdgesPruningSpec.java
  app/src/main/java/encoders/MonoSATEncoder.java
  app/src/main/java/compile/v2/compilers/SerCompiler.java
  app/src/main/java/util/isolation/ExcludeRtoPruningSpec.java
  app/src/main/java/compile/v2/compilers/Pl299Compiler.java
  app/src/main/java/util/isolation/NoPrwPruningSpec.java
  app/src/main/java/compile/v2/compilers/PlSiCompiler.java
  app/src/main/java/compile/v2/compilers/RcCompiler.java
  app/src/main/java/util/isolation/DependencyOnlyPruningSpec.java
  app/src/main/java/solvers/PL2PlusSolver.java
  app/src/main/java/solvers/CursorStabilitySolver.java
  app/src/main/java/solvers/PlFcvSolver.java
)

command -v docker >/dev/null 2>&1 || {
  echo "ERROR: docker is required to reproduce Figure 7." >&2
  exit 2
}
docker image inspect "${image}" >/dev/null 2>&1 || {
  echo "ERROR: Docker image ${image} is unavailable; run artifacts/scripts/build-image.sh first." >&2
  exit 2
}

declare -A measured=()
while IFS=, read -r language filename _blank _comment code _rest; do
  [[ "${language}" == "Java" ]] || continue
  code="${code//$'\r'/}"
  measured["${filename}"]="${code}"
done < <(
  docker run --rm \
    --volume "${repo_root}:/repo:ro" \
    --workdir /repo \
    --entrypoint cloc \
    "${image}" \
    --csv --quiet --by-file --include-lang=Java "${files[@]}"
)

mkdir -p "${results_dir}"
{
  printf '# Figure 7 checker-specific files\n\n'
  printf '| File | Java SLOC |\n'
  printf '|---|---:|\n'
  for file in "${files[@]}"; do
    value="${measured[${file}]:-}"
    [[ "${value}" =~ ^[0-9]+$ ]] || {
      echo "ERROR: cloc did not return a count for ${file}" >&2
      exit 2
    }
    printf '| `%s` | %s |\n' "${file}" "${value}"
  done
} | tee "${output}"

echo "Wrote ${output}"
