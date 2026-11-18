#!/bin/sh
set -eu
cd "$(dirname "$0")"
# Target is JDK 25. A lower release override is ONLY an explicit compatibility check.
release="${JAVA_RELEASE:-25}"
javac_cmd="${JAVAC:-javac}"
if [ "$release" != 25 ]; then
    echo "NOTE: compatibility check for release $release; NOT JDK 25 validation."
fi
"$javac_cmd" -version
# Avoid stale .class files (notably removed BoardEffect/PluginPriority).
rm -rf build/api build/phase1 build/phase2 build/view build/smoke
mkdir -p build/api build/phase1 build/phase2 build/view build/smoke
find life-api/src/main/java -name '*.java' | sort > build/api-sources.txt
"$javac_cmd" --release "$release" -encoding UTF-8 -d build/api @build/api-sources.txt
"$javac_cmd" --release "$release" -encoding UTF-8 -cp build/api -d build/phase1 examples/HostCompileProbe.java
"$javac_cmd" --release "$release" -encoding UTF-8 -cp build/api -d build/phase2 examples/PluginCompileProbe.java
"$javac_cmd" --release "$release" -encoding UTF-8 -cp build/api -d build/view examples/ViewCompileProbe.java
"$javac_cmd" --release "$release" -encoding UTF-8 -cp build/api -d build/smoke examples/ApiValueSmoke.java
echo "PASS: API + independent host/plugin/view compilation (release $release)"
