#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
rm -rf -- build/classes
mkdir -p build/classes dist
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 --release 17 -d build/classes src/main/java/vn/ptit/colorgame/*.java
cp -R src/main/resources/. build/classes/
java -m jdk.jartool/sun.tools.jar.Main --create --file dist/ColorDuel.jar --main-class vn.ptit.colorgame.Main -C build/classes .
echo 'Built dist/ColorDuel.jar'
