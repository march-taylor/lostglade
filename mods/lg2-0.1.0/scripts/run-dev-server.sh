#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${MOD_DIR}"

# Copies ./resourcepack to the server's polymer/source_assets folder,
# then starts Loom's dev server without jar assembly tasks.
./gradlew --console=plain prepareDevResourcePack runServer -x jar -x remapJar
