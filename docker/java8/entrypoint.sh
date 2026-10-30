#!/usr/bin/env bash
set -euo pipefail
mkdir -p /workspace
tar -C /source --exclude=.git --exclude=target -cf - . | tar -C /workspace -xf -
cd /workspace
java -version
mvn -s /source/docker/java8/settings.xml -B -ntp clean package dependency:copy-dependencies -DincludeScope=runtime
# Prove compiled application classes target Java 8 (class major version 52).
python3 - <<'PY'
from pathlib import Path
import struct
classes=list(Path('target/classes').rglob('*.class'))
assert classes
for p in classes:
    assert struct.unpack('>H', p.read_bytes()[6:8])[0] == 52, str(p)
import zipfile
jars = list(Path('target/dependency').glob('*.jar'))
assert not any(any(name in p.name.lower() for name in ('pty4j', 'purejavacomm', 'jna')) for p in jars)
with zipfile.ZipFile('target/terminalfx.jar') as jar:
    assert not any(name.startswith('libpty/') for name in jar.namelist())
print('PASS: Java 8 class files; no PTY/JNA dependencies or bundled PTY natives.')
PY
pids=()
cleanup() {
    trap - EXIT INT TERM
    if ((${#pids[@]})); then
        kill "${pids[@]}" 2>/dev/null || true
        wait "${pids[@]}" 2>/dev/null || true
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
Xvfb "$DISPLAY" -screen 0 1280x800x24 -nolisten tcp &
pids+=("$!")
for ((i=0; i<100; i++)); do
    if xdpyinfo >/dev/null 2>&1; then break; fi
    sleep 0.1
done
xdpyinfo >/dev/null
openbox &
pids+=("$!")
x11vnc -display "$DISPLAY" -rfbport 5900 -localhost -nopw -forever -shared -noxdamage &
pids+=("$!")
websockify --web=/usr/share/novnc/ 0.0.0.0:6080 localhost:5900 &
pids+=("$!")
classpath='target/test-classes:target/classes:target/dependency/*'
if [[ ${SMOKE_TEST:-0} == 1 ]]; then
    timeout 90 java -Dprism.order=sw -cp "$classpath" com.terminalfx.TerminalDisplaySmoke
fi
java -Dprism.order=sw -cp "$classpath" com.terminalfx.AppStarter "$@" &
app_pid=$!
pids+=("$app_pid")
if [[ ${SMOKE_TEST:-0} == 1 ]]; then
    timeout 60 xdotool search --sync --onlyvisible --name '^TerminalFX$' >/dev/null
    kill -0 "$app_pid"
    curl -fsS --retry 10 --retry-connrefused --retry-delay 1 http://localhost:6080/vnc.html >/dev/null
    echo 'PASS: Java 8 example window and noVNC HTTP endpoint.'
    exit 0
fi
echo 'TerminalFX Java 8 desktop ready; use the localhost browser URL.'
set +e
wait -n "${pids[@]}"
status=$?
set -e
exit "$status"
