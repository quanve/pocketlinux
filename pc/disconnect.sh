#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
serial=$(pc/check-device.sh 2>/dev/null) || { echo "no phone 1 connected"; exit 0; }
adb -s "$serial" shell su -c "/data/linux/bin/umount-pc.sh" 2>/dev/null || true
adb -s "$serial" reverse --remove tcp:2222 >/dev/null 2>&1 || true
echo "disconnected"
