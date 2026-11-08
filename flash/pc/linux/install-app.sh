#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
serial=$("$DIR/check-device.sh")
APK=${1:-$ROOT/payload/phone-lab.apk}
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 2; }
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
