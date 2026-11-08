#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
FLASH=$ROOT/flash
PL_STORAGE=${PL_STORAGE:-/storage/phone-lab}
if [ ! -d "$PL_STORAGE" ] && [ -d "$HOME/Disk/phone-lab" ]; then
    PL_STORAGE="$HOME/Disk/phone-lab"
fi
say() { printf '%s\n' "$*"; }
say ">> syncing host scripts -> flash/phone"
for f in mount-chroot.sh mount-pc.sh mount-pc-net.sh umount-pc.sh enter.sh status.sh \
         zapret usb-hid.sh hid-daemon.sh hid-loop.py ocserve-start.sh ocserve-stop.sh usb-share.sh \
         pc-bridge.sh service.sh install-on-phone.sh; do
    cp -p "$ROOT/phone/$f" "$FLASH/phone/$f"
done
say ">> syncing Termux wrappers -> flash/termux"
for f in "$ROOT"/phone/termux-*.sh; do
    cp -p "$f" "$FLASH/termux/$(basename "$f")"
done
say ">> syncing Debian helpers -> flash/phone/debian"
rm -rf "$FLASH/phone/debian"
cp -a "$ROOT/phone/debian" "$FLASH/phone/debian"
say ">> syncing plugins -> flash/plugins"
rm -rf "$FLASH/plugins"
mkdir -p "$FLASH/plugins"
if git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
    ( cd "$ROOT" && git ls-files -z plugins ) |
    while IFS= read -r -d '' f; do
        mkdir -p "$FLASH/$(dirname "$f")"
        cp -p "$ROOT/$f" "$FLASH/$f"
    done
else
    cp -a "$ROOT/plugins/." "$FLASH/plugins/"
fi
say ">> syncing device guard -> flash/pc/linux/check-device.sh"
cp -p "$ROOT/pc/check-device.sh" "$FLASH/pc/linux/check-device.sh"
say ">> syncing PC bootstrap scripts -> flash/pc/bootstrap"
mkdir -p "$FLASH/pc/bootstrap"
cp -p "$ROOT/pc/bootstrap/"* "$FLASH/pc/bootstrap/"
if [ "${1:-}" = "--payload" ]; then
    say ">> refreshing payload"
    [ -f "$PL_STORAGE/build/debian-rootfs.tar.gz" ] && \
        cp -p "$PL_STORAGE/build/debian-rootfs.tar.gz" "$FLASH/payload/"
    [ -f "$PL_STORAGE/build/opencode-linux-arm64.tar.gz" ] && \
        cp -p "$PL_STORAGE/build/opencode-linux-arm64.tar.gz" "$FLASH/payload/"
    [ -f "$ROOT/app/app/build/outputs/apk/debug/app-debug.apk" ] && \
        cp -p "$ROOT/app/app/build/outputs/apk/debug/app-debug.apk" "$FLASH/payload/phone-lab.apk"
fi
say ">> refreshing CHECKSUMS.txt"
( cd "$FLASH" && find . -type f ! -name CHECKSUMS.txt -print0 | sort -z | xargs -0 sha256sum > CHECKSUMS.txt )
if command -v zip >/dev/null 2>&1 && [ -d "$PL_STORAGE/images" ]; then
    say ">> rebuilding $PL_STORAGE/images/phone-lab-flash.zip"
    ZIP=$PL_STORAGE/images/phone-lab-flash.zip
    rm -f "$ZIP"
    ( cd "$ROOT" && zip -q -r "$ZIP" flash )
    ls -lh "$ZIP"
    sha256sum "$ZIP"
else
    say ">> zip skipped (zip or $PL_STORAGE/images missing)"
fi
say "done"
