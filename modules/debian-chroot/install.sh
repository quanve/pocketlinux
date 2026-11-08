#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
say() { printf '\n== %s\n' "$*"; }
PL_STORAGE=${PL_STORAGE:-/storage/phone-lab}
if [ ! -d "$PL_STORAGE" ] && [ -d "$HOME/Disk/phone-lab" ]; then
    PL_STORAGE="$HOME/Disk/phone-lab"
fi
ROOTFS_TAR=$PL_STORAGE/build/debian-rootfs.tar.gz
OPENCODE_TAR=$PL_STORAGE/build/opencode-linux-arm64.tar.gz
KEY=pc/phone_lab_key
HELPERS=/tmp/phone-lab-debian-helpers.tar
PLUGTAR=/tmp/phone-lab-plugins.tar
if [ ! -f "$ROOTFS_TAR" ]; then
    say "rootfs tarball missing, building (this takes a while)"
    pc/build-debian-rootfs.sh
fi
[ -f "$OPENCODE_TAR" ] || { echo "missing $OPENCODE_TAR"; exit 3; }
say "packing Debian CLI helpers"
tar -C phone/debian -cf "$HELPERS" usr/local/sbin usr/local/bin root/.bashrc \
    root/.config/opencode/skills
tar -C pc/bootstrap --transform 's,^,usr/local/share/phone-lab/,' \
    -rf "$HELPERS" linux.sh windows.ps1
say "packing plugins"
tar -C plugins -cf "$PLUGTAR" .
say "pushing payload files to /data/local/tmp/pl"
adb -s "$serial" shell mkdir -p /data/local/tmp/pl
adb -s "$serial" push "$ROOTFS_TAR" /data/local/tmp/pl/debian-rootfs.tar.gz >/dev/null
adb -s "$serial" push "$OPENCODE_TAR" /data/local/tmp/pl/opencode.tar.gz >/dev/null
adb -s "$serial" push "$KEY" /data/local/tmp/pl/phone_lab_key >/dev/null
adb -s "$serial" push "$HELPERS" /data/local/tmp/pl/debian-helpers.tar >/dev/null
adb -s "$serial" push "$PLUGTAR" /data/local/tmp/pl/plugins.tar >/dev/null
for f in phone/mount-chroot.sh phone/mount-pc.sh phone/mount-pc-net.sh \
         phone/umount-pc.sh phone/enter.sh phone/status.sh phone/zapret \
         phone/usb-hid.sh phone/hid-daemon.sh phone/hid-loop.py \
         phone/ocserve-start.sh phone/ocserve-stop.sh phone/usb-share.sh \
         phone/pc-bridge.sh \
         phone/service.sh phone/install-on-phone.sh \
         phone/termux-linux.sh phone/termux-usb-hid.sh phone/termux-zapret.sh \
         phone/termux-pcmount.sh phone/termux-pcumount.sh phone/termux-pl.sh; do
    adb -s "$serial" push "$f" /data/local/tmp/pl/ >/dev/null
done
say "running on-device installer"
adb -s "$serial" shell "su -c 'sh /data/local/tmp/pl/install-on-phone.sh'" 2>&1 | tee logs/install-debian.log
say "verify"
adb -s "$serial" shell "su -c 'chroot /data/linux/debian /usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash -lc \"opencode --version\"'"
adb -s "$serial" shell "su -c 'chroot /data/linux/debian /usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash -lc \". /etc/os-release; echo \\\$PRETTY_NAME\"'"
