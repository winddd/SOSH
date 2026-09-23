#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
artifacts_dir="$(cd "${script_dir}/.." && pwd)"

# Reuse the self-contained copy staged for Figure 10.
history="${SMOKE_HISTORY:-inputs/rw_10k}"

DATA_ROOT="${DATA_ROOT:-${artifacts_dir}/data/fig10}" exec "${script_dir}/run-one.sh" \
  "${history}" B_SER cobra smoke_ser_10k -no_hashmap
