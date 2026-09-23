#!/bin/sh
set -eu

case "$0" in
  /*) script_path=$0 ;;
  */*) script_path="${PWD%/}/$0" ;;
  *) script_path=$(command -v -- "$0") ;;
esac

repo_root=$(CDPATH= cd -- "$(dirname -- "$script_path")" && pwd)
export BOOMSLANG_HOME="$repo_root"

cd "$repo_root"

exec ./gradlew \
  --no-daemon \
  --no-watch-fs \
  shadowJar
