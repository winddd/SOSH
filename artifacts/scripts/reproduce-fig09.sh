#!/usr/bin/env bash
set -euo pipefail
RUN_TIMEOUT_SECONDS=600 exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-figure.sh" fig09 "$@"
