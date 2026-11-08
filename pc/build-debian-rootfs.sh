#!/usr/bin/env bash
set -euo pipefail
SUITE=${SUITE:-trixie}
MIRROR=${MIRROR:-https://deb.debian.org/debian}
WORK=${WORK:-/storage/phone-lab}
if [ ! -d "$WORK" ] && [ -d "$HOME/Disk/phone-lab" ]; then
    WORK="$HOME/Disk/phone-lab"
fi
ROOT=$WORK/debian-rootfs
OUT=$WORK/build/debian-rootfs.tar.gz
DB=$WORK/build/debootstrap-pkg
if command -v doas >/dev/null 2>&1; then SUDO=doas
elif command -v sudo >/dev/null 2>&1; then SUDO=sudo
else SUDO=""; fi
run_root() {
    if [ -n "$SUDO" ]; then "$SUDO" "$@"; else "$@"; fi
}
mkdir -p "$WORK/build"
if [ ! -x "$DB/usr/sbin/debootstrap" ]; then
    echo ">> fetching debootstrap 1.0.145"
    rm -rf "$DB"; mkdir -p "$DB"
    ( cd "$DB"
      curl -fSLO https://deb.debian.org/debian/pool/main/d/debootstrap/debootstrap_1.0.145_all.deb
      ar x debootstrap_1.0.145_all.deb
      tar -xf data.tar.* )
fi
if [ -f "$ROOT/etc/debian_version" ]; then
    echo ">> rootfs already exists ($(cat "$ROOT/etc/debian_version")), skipping debootstrap"
else
    echo ">> debootstrap --foreign $SUITE arm64 -> $ROOT"
    run_root rm -rf "$ROOT"
    ( cd "$DB" && run_root env DEBOOTSTRAP_DIR="$DB/usr/share/debootstrap" \
        ./usr/sbin/debootstrap --foreign --arch=arm64 --variant=minbase \
        --extractor=ar --no-check-gpg "$SUITE" "$ROOT" "$MIRROR" )
fi
echo ">> packing $OUT"
run_root tar -C "$ROOT" -czf "$OUT" .
ls -lh "$OUT"
