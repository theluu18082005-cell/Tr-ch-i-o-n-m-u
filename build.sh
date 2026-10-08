#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
rm -rf -- build/classes
mkdir -p build/classes dist
mkdir -p dist/lib
driver=mysql-connector-j-8.3.0.jar
if [[ ! -f "dist/lib/$driver" && -f "$HOME/.m2/repository/com/mysql/mysql-connector-j/8.3.0/$driver" ]]; then
  cp "$HOME/.m2/repository/com/mysql/mysql-connector-j/8.3.0/$driver" dist/lib/
fi
if [[ ! -f "dist/lib/$driver" ]]; then echo "Missing dist/lib/$driver; see docs/MYSQL.md" >&2; exit 1; fi
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 --release 17 -d build/classes src/main/java/vn/ptit/colorgame/*.java
cp -R src/main/resources/. build/classes/
printf 'Manifest-Version: 1.0\nMain-Class: vn.ptit.colorgame.Main\nClass-Path: lib/mysql-connector-j-8.3.0.jar\n\n' > build/manifest.mf
java -m jdk.jartool/sun.tools.jar.Main --create --file dist/ColorDuel-30s.jar --manifest build/manifest.mf -C build/classes .
echo 'Built dist/ColorDuel-30s.jar'
