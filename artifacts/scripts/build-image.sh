#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
image="${BOOMSLANG_AE_IMAGE:-boomslang-ae:local}"
vcs_ref="$(git -C "${repo_root}" rev-parse HEAD)"
build_date="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

exec docker build \
  --platform linux/amd64 \
  --file "${repo_root}/artifacts/Dockerfile" \
  --tag "${image}" \
  --build-arg "VCS_REF=${vcs_ref}" \
  --build-arg "BUILD_DATE=${build_date}" \
  "${repo_root}"
