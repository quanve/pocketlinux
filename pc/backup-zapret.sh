#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
serial=$(pc/check-device.sh)
ts=$(date +%Y%m%d-%H%M%S)
out="backups/zapret-$ts"
mkdir -p "$out"
adb -s "$serial" exec-out su -c "tar -cf - -C /data/adb modules/zapret zapret" > "$out/zapret.tar"
adb -s "$serial" shell su -c "sha256sum /data/adb/zapret/nfqws" | tr -d '\r' > "$out/nfqws.sha256"
adb -s "$serial" shell su -c "cat /data/adb/modules/zapret/module.prop" > "$out/module.prop"
tar -tf "$out/zapret.tar" > "$out/filelist.txt"
echo "saved $out"
ls -l "$out"
