#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ ${1:-} == --help ]]; then
    cat <<'HELP'
Usage: ./docker-test.sh [application arguments]
Build/run TerminalFX with Java 8 + JavaFX 8 entirely inside Docker/Podman.
Open http://localhost:6080/vnc.html?autoconnect=1&resize=remote after startup.
Ctrl-C stops/removes the container.
Options via environment:
  CONTAINER_ENGINE=docker|podman (auto-detected)
  TEST_PORT=6080
  TEST_PLATFORM=linux/arm64|linux/amd64 (native host by default)
  SMOKE_TEST=1 (run terminal display and input/output callback assertions, then exit)
  JAVA8_URL=... (override pinned Azul JDK 8 with JavaFX download)
  MAVEN_CACHE=terminalfx-java8-m2
HELP
    exit 0
fi
engine=${CONTAINER_ENGINE:-}
if [[ -z $engine ]]; then
    if command -v docker >/dev/null 2>&1; then engine=docker
    elif command -v podman >/dev/null 2>&1; then engine=podman
    else echo 'Start/install Docker Desktop or Podman first.' >&2; exit 1; fi
fi
"$engine" info >/dev/null
case $(uname -m) in
    arm64|aarch64) native_platform=linux/arm64 ;;
    x86_64|amd64) native_platform=linux/amd64 ;;
    *) echo 'Unsupported host architecture.' >&2; exit 1 ;;
esac
platform=${TEST_PLATFORM:-$native_platform}
case $platform in
    linux/arm64) archive_arch=aarch64 ;;
    linux/amd64) archive_arch=x64 ;;
    *) echo 'Use linux/arm64 or linux/amd64.' >&2; exit 1 ;;
esac
url=${JAVA8_URL:-https://cdn.azul.com/zulu/bin/zulu8.96.0.205-ca-fx-jdk8.0.504-linux_${archive_arch}.tar.gz}
port=${TEST_PORT:-6080}
[[ $port =~ ^[0-9]+$ ]] && ((port > 0 && port < 65536)) || { echo 'Invalid TEST_PORT.' >&2; exit 1; }
image=terminalfx-java8-test:${platform##*/}
"$engine" build --platform "$platform" --build-arg "JAVA8_URL=$url" -t "$image" "$ROOT/docker/java8"
printf '\nAfter startup open http://localhost:%s/vnc.html?autoconnect=1&resize=remote\n' "$port"
exec "$engine" run --rm --init --platform "$platform" --shm-size=512m \
    -p "127.0.0.1:$port:6080" -v "$ROOT:/source:ro" \
    -v "${MAVEN_CACHE:-terminalfx-java8-m2}:/root/.m2" \
    -e "SMOKE_TEST=${SMOKE_TEST:-0}" "$image" "$@"
