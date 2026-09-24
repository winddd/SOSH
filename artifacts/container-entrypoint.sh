#!/usr/bin/env bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
  cat >&2 <<'USAGE'
Usage: docker run ... boomslang-ae [Boomslang arguments]

Required arguments normally include:
  -h HISTORY -config /opt/boomslang/config.yaml -format FORMAT
  -fromFile false -solver monosat -m MODE -run_name NAME -output FILE

Set JAVA_HEAP (default: 16g) and optional TIME_LOG (for /usr/bin/time -v).
USAGE
  exit 2
fi

java_cmd=(
  java
  "-Xmx${JAVA_HEAP:-16g}"
  -Djava.library.path=/usr/local/lib
  -Dlog4j.configurationFile=/opt/boomslang/log4j2.xml
)

if [[ -s /opt/boomslang/tracescope-classes.jar ]]; then
  java_cmd+=(
    -cp /opt/boomslang/tracescope-classes.jar:/opt/boomslang/boomslang.jar
    main.Main
  )
else
  java_cmd+=(-jar /opt/boomslang/boomslang.jar)
fi

if [[ -n "${TIME_LOG:-}" ]]; then
  exec /usr/bin/time -v -o "${TIME_LOG}" "${java_cmd[@]}" "$@"
fi

exec "${java_cmd[@]}" "$@"
