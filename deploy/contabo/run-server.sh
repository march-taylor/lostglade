#!/usr/bin/env bash
set -euo pipefail

cd /srv/lostglade/mods/lg2-0.1.0

# A Cloud VPS has no physical GPU/display. GLFW's null backend cannot create
# the OpenGL context Minecraft needs, so provide a local dummy Xorg display
# backed by Mesa llvmpipe. It is never exposed on the network.
export DISPLAY=:99
export XDG_RUNTIME_DIR=/tmp/lostglade-xdg
install -d -m 0700 "${XDG_RUNTIME_DIR}"
Xorg "${DISPLAY}" \
  -config /srv/lostglade/deploy/contabo/xorg-dummy.conf \
  -noreset +extension GLX +extension RANDR +extension RENDER -nolisten tcp &
xorg_pid=$!

cleanup() {
  kill "${xorg_pid}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# The server process owns the renderer bot child process. Heap limits are set
# in build.gradle per run, so the child does not inherit the server's heap.
set +e
./gradlew --no-daemon --console=plain \
  -Dlg2.serverXms=8G -Dlg2.serverXmx=8G \
  -Dlg2.rendererBotXms=4G -Dlg2.rendererBotXmx=4G \
  prepareDevResourcePack runServer -x jar -x remapJar
exit_code=$?
set -e
exit "${exit_code}"
