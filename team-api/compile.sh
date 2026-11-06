#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/api build/phase1 build/phase2
find life-api/src/main/java -name '*.java' > build/api-sources.txt
javac -encoding UTF-8 -d build/api @build/api-sources.txt
javac -encoding UTF-8 -cp build/api -d build/phase1 examples/HostCompileProbe.java
javac -encoding UTF-8 -cp build/api -d build/phase2 examples/PluginCompileProbe.java
echo "PASS: API + Phase1 host-only + Phase2 plugin-only compilation (Java 21)"
