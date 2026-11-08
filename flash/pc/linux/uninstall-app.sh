#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
serial=$("$DIR/check-device.sh")
APPUID=$(adb -s "$serial" shell "cmd package list packages -U dev.quanve.phonelab" \
    | tr -d '\r' | sed -n 's/.*uid:\([0-9]*\).*/\1/p')
adb -s "$serial" uninstall dev.quanve.phonelab || true
if [ -n "$APPUID" ]; then
    adb -s "$serial" shell "su -c 'magisk --sqlite \"DELETE FROM policies WHERE uid=$APPUID;\"'" || true
fi
echo "uninstalled"
