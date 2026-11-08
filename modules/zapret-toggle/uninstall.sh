#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
adb -s "$serial" shell "su -c 'rm -f /data/linux/bin/zapret /data/data/com.termux/files/home/.shortcuts/zapret-toggle; echo removed (zapret module untouched)'"
