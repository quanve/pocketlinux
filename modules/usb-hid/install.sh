#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
for f in usb-hid.sh hid-daemon.sh hid-loop.py; do
    adb -s "$serial" push "phone/$f" "/data/local/tmp/$f" >/dev/null
done
adb -s "$serial" shell "su -c 'mkdir -p /data/linux/bin; cp /data/local/tmp/usb-hid.sh /data/local/tmp/hid-daemon.sh /data/local/tmp/hid-loop.py /data/linux/bin/; chmod 755 /data/linux/bin/usb-hid.sh /data/linux/bin/hid-daemon.sh /data/linux/bin/hid-loop.py; rm -f /data/local/tmp/usb-hid.sh /data/local/tmp/hid-daemon.sh /data/local/tmp/hid-loop.py; echo installed /data/linux/bin/{usb-hid.sh,hid-daemon.sh,hid-loop.py}'"
