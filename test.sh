#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
bash build.sh
mkdir -p build/test-classes
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 --release 17 -cp build/classes -d build/test-classes src/test/java/vn/ptit/colorgame/*.java
java -cp build/classes:build/test-classes vn.ptit.colorgame.IntegrationTest
java -Djava.awt.headless=true -cp build/classes:build/test-classes vn.ptit.colorgame.AudioManagerTest
java -Djava.awt.headless=true -cp build/classes:build/test-classes vn.ptit.colorgame.ClientAudioTest
java -Djava.awt.headless=true -cp build/classes:build/test-classes vn.ptit.colorgame.SettingsTest
java -Djava.awt.headless=true -cp build/classes:build/test-classes vn.ptit.colorgame.UiSmokeTest
java -Djava.awt.headless=true -cp build/classes:build/test-classes vn.ptit.colorgame.GuiNetworkTest
