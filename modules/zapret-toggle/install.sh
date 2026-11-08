#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
adb -s "$serial" push phone/zapret /data/local/tmp/zapret >/dev/null
TUX=$(adb -s "$serial" shell "su -c 'stat -c %u /data/data/com.termux'" | tr -d '\r')
adb -s "$serial" shell "su -c 'mkdir -p /data/linux/bin /data/data/com.termux/files/home/.shortcuts; cp /data/local/tmp/zapret /data/linux/bin/zapret; chmod 755 /data/linux/bin/zapret; rm -f /data/local/tmp/zapret; printf \"#!/data/data/com.termux/files/usr/bin/sh\\n/data/linux/bin/zapret toggle\\n\" > /data/data/com.termux/files/home/.shortcuts/zapret-toggle; chmod 755 /data/data/com.termux/files/home/.shortcuts/zapret-toggle; chown -R $TUX:$TUX /data/data/com.termux/files/home/.shortcuts; echo installed'"
if adb -s "$serial" shell "su -c 'test -x /data/adb/zapret/ctl.sh'" >/dev/null 2>&1; then
    adb -s "$serial" shell "su -c '/data/linux/bin/zapret status'"
else
    echo "zapret Magisk module not found; install it first (see README)"
fi
