#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 || ( $# -eq 2 && "$2" != '--integration' ) ]]; then
    printf 'Usage: bash build.sh /path/to/server [--integration]\n' >&2
    exit 2
fi
server_dir="$(cd -- "$1" && pwd)"
cd -- "$(dirname -- "$0")"
server_jar="${SERVER_RUNTIME_JAR:?Set SERVER_RUNTIME_JAR to the extracted server runtime JAR}"
api_jar="${SERVER_API_JAR:?Set SERVER_API_JAR to the matching server API JAR}"
test -f "$server_jar"
test -f "$api_jar"
bootstrap_jar="${BOOTSTRAP_JAR:-$server_dir/server.jar}"
test -f "$bootstrap_jar"
classpath="$server_jar:$api_jar:$bootstrap_jar"
api_artifact_dir="$(dirname -- "$(dirname -- "$api_jar")")"
while IFS= read -r -d '' library; do
    [[ "$library" == "$api_artifact_dir/"* ]] || classpath+=":$library"
done < <(find "$server_dir/libraries" -name '*.jar' -print0)
mkdir -p build/classes
find build/classes -type f -delete
mapfile -d '' mixin_sources < <(find src/main/java/world/bigbarrel/inject -name '*.java' -print0)
# Irisclip advertises Java 21 as its maximum Mixin compatibility level.
javac -proc:none --release 21 -encoding UTF-8 -classpath "$classpath" -d build/classes "${mixin_sources[@]}"
mapfile -d '' sources < <(find src/main/java -name '*.java' -not -path '*/inject/*' -print0)
javac -proc:none --release 25 -encoding UTF-8 -classpath "$classpath:build/classes" -d build/classes "${sources[@]}"
cp src/main/resources/* build/classes/
jar --create --file build/BigBarrel-0.2.0.jar -C build/classes .
printf '%s\n' 'Built build/BigBarrel-0.2.0.jar'
if [[ "${2:-}" == '--integration' ]]; then
    mkdir -p build/test-classes
    javac -proc:none --release 25 -encoding UTF-8 -classpath "$classpath" -d build/test-classes src/test/java/world/bigbarreltest/*.java
    cat > build/test-classes/plugin.yml <<'YAML'
name: BigBarrelIntegrationTest
version: 0.1.0
main: world.bigbarreltest.IntegrationPlugin
api-version: '26.3'
folia-supported: true
depend: [BigBarrel]
YAML
    jar --create --file build/BigBarrelIntegrationTest.jar -C build/test-classes .
fi
