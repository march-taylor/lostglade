#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo 'Run as root: sudo ./deploy/contabo/provision-vps.sh' >&2
  exit 1
fi

server_root='/srv/lostglade'
if [[ ! -f "${server_root}/mods/lg2-0.1.0/gradlew" ]]; then
  echo "Expected the release under ${server_root}" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
  openjdk-21-jdk-headless \
  mesa-va-drivers mesa-vulkan-drivers libgl1 libglx-mesa0 \
  libegl1 libegl-mesa0 libx11-6 libxcursor1 libxext6 libxrandr2 libxinerama1 libxi6 \
  ffmpeg curl ca-certificates ufw \
  xserver-xorg-core xserver-xorg-video-dummy

id -u lostglade >/dev/null 2>&1 || useradd --system --home-dir "${server_root}" --create-home --shell /usr/sbin/nologin lostglade
chown -R lostglade:lostglade "${server_root}"
chmod 0750 "${server_root}/server-secrets" 2>/dev/null || true
chmod +x "${server_root}/mods/lg2-0.1.0/gradlew" "${server_root}/mods/lg2-0.1.0/scripts/run-dev-server.sh" "${server_root}/deploy/contabo/run-server.sh"

install -D -m 0644 "${server_root}/deploy/contabo/lostglade.service" /etc/systemd/system/lostglade.service
systemctl daemon-reload

ufw allow OpenSSH
ufw allow 25565/tcp comment 'Lostglade Minecraft'
ufw allow 24454/udp comment 'Lostglade voice chat'
ufw --force enable

systemctl enable --now lostglade.service
systemctl --no-pager --full status lostglade.service
