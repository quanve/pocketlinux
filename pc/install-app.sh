#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
serial=$(pc/check-device.sh)
APK=${1:-app/app/build/outputs/apk/debug/app-debug.apk}
[ -f "$APK" ] || { echo "APK not found: $APK (run pc/build-app.sh)"; exit 2; }
adb -s "$serial" install -r "$APK"
APPUID=$(adb -s "$serial" shell "cmd package list packages -U dev.quanve.phonelab" \
    | tr -d '\r' | sed -n 's/.*uid:\([0-9]*\).*/\1/p')
if [ -n "$APPUID" ]; then
    echo "app uid: $APPUID"
    adb -s "$serial" shell "su -c 'magisk --sqlite \"INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES ($APPUID,2,0,1,1);\"'"
fi
adb -s "$serial" shell am force-stop dev.quanve.phonelab
adb -s "$serial" shell am start -n dev.quanve.phonelab/.MainActivity
echo "installed and started"
