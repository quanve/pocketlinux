#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
adb -s "$serial" shell "su -c '/data/linux/bin/umount-pc.sh 2>/dev/null; umount /data/linux/debian/mnt/pc 2>/dev/null; umount /data/linux/debian/dev/shm 2>/dev/null; umount /data/linux/debian/dev/pts 2>/dev/null; umount /data/linux/debian/dev 2>/dev/null; umount /data/linux/debian/run 2>/dev/null; umount /data/linux/debian/sys 2>/dev/null; umount /data/linux/debian/proc 2>/dev/null; rm -f /data/adb/service.d/00-phone-lab.sh; rm -f /data/data/com.termux/files/usr/bin/linux; rm -rf /data/linux; echo removed'"
adb -s "$serial" reverse --remove tcp:2222 >/dev/null 2>&1 || true
echo "debian-chroot removed"
