#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if [[ ! -f dist/ColorDuel-30s.jar ]]; then bash build.sh; fi
exec java -Dfile.encoding=UTF-8 -jar dist/ColorDuel-30s.jar "$@"
