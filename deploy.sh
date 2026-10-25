#!/usr/bin/env bash
# Builds the mod, installs it on the Minecraft server (CT 104 on proxmox-01) and restarts the server.
#
#   ./deploy.sh
#
# The restart kicks everyone, so this stops if anyone is online. FORCE=1 restarts anyway.
# Optional: PVE_HOST (root@proxmox-01.local), CTID (104), STACK (/opt/minecraft)
set -euo pipefail

PVE_HOST="${PVE_HOST:-root@proxmox-01.local}"
CTID="${CTID:-104}"
STACK="${STACK:-/opt/minecraft}"

cd "$(dirname "$0")"
./gradlew build --console=plain -q
version="$(sed -n 's/^version=//p' gradle.properties)"
name="rings-$version.jar"
jar="build/libs/$name"

online="$(ssh "$PVE_HOST" "pct exec $CTID -- docker exec minecraft rcon-cli list" || true)"
if [ "${FORCE:-}" != 1 ] && ! grep -q "There are 0 " <<<"$online"; then
  echo "Players are online (${online:-server not answering}). Run with FORCE=1 to restart anyway."
  exit 1
fi

scp -q "$jar" "$PVE_HOST:/tmp/$name"
ssh "$PVE_HOST" "set -e
pct exec $CTID -- sh -c 'rm -f $STACK/mods/rings-*.jar'
pct push $CTID /tmp/$name $STACK/mods/$name --user 1000 --group 1000
rm /tmp/$name
pct exec $CTID -- sh -c 'cd $STACK && docker compose up -d --force-recreate minecraft'"
echo "Installed $name. The server is back in about 30 s."
