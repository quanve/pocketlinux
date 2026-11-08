#!/usr/bin/env bash
set -uo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
serial=$("$DIR/check-device.sh" 2>/dev/null) || { echo "no phone 1 connected"; exit 0; }
adb -s "$serial" shell su -c /data/linux/bin/umount-pc.sh 2>/dev/null || true
adb -s "$serial" reverse --remove tcp:2222 >/dev/null 2>&1 || true
echo "disconnected"
