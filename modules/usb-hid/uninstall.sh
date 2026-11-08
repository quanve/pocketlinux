#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
adb -s "$serial" shell "su -c 'rm -f /data/linux/bin/usb-hid.sh /data/linux/bin/hid-daemon.sh /data/linux/bin/hid-loop.py; echo removed usb-hid helpers'"
