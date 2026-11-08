#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
serial=$("$DIR/check-device.sh")
PL=/data/local/tmp/pl
echo "phone serial: $serial"
tar -C "$ROOT/phone/debian" -cf /tmp/phone-lab-debian-helpers.tar usr/local/sbin usr/local/bin root/.bashrc \
    root/.config/opencode/skills
tar -C "$ROOT/pc/bootstrap" --transform 's,^,usr/local/share/phone-lab/,' \
    -rf /tmp/phone-lab-debian-helpers.tar linux.sh windows.ps1
tar -C "$ROOT/plugins" -cf /tmp/phone-lab-plugins.tar .
adb -s "$serial" shell mkdir -p "$PL"
adb -s "$serial" push "$ROOT/payload/debian-rootfs.tar.gz" "$PL/debian-rootfs.tar.gz" >/dev/null
adb -s "$serial" push "$ROOT/payload/opencode-linux-arm64.tar.gz" "$PL/opencode.tar.gz" >/dev/null
adb -s "$serial" push "$ROOT/keys/phone_lab_key" "$PL/phone_lab_key" >/dev/null
adb -s "$serial" push /tmp/phone-lab-debian-helpers.tar "$PL/debian-helpers.tar" >/dev/null
adb -s "$serial" push /tmp/phone-lab-plugins.tar "$PL/plugins.tar" >/dev/null
for f in mount-chroot.sh mount-pc.sh mount-pc-net.sh umount-pc.sh enter.sh status.sh \
         zapret usb-hid.sh hid-daemon.sh hid-loop.py ocserve-start.sh ocserve-stop.sh usb-share.sh \
         pc-bridge.sh service.sh install-on-phone.sh \
         termux-linux.sh termux-usb-hid.sh termux-zapret.sh \
         termux-pcmount.sh termux-pcumount.sh termux-pl.sh; do
    adb -s "$serial" push "$ROOT/phone/$f" "$PL/$f" >/dev/null
done
adb -s "$serial" shell su -c 'sh /data/local/tmp/pl/install-on-phone.sh'
