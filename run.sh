#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if [[ ! -f dist/ColorDuel.jar ]]; then bash build.sh; fi
exec java -Dfile.encoding=UTF-8 -jar dist/ColorDuel.jar "$@"
