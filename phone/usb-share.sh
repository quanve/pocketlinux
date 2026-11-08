#!/system/bin/sh
CH=/data/linux/debian
IMG=/data/linux/share.img
MNT=$CH/mnt/share
G=/config/usb_gadget/g1
FUNC=$G/functions/mass_storage.share
LINK=$G/configs/b.1/f6
STATE=/data/local/tmp/usb-share.state
PREV=/data/local/tmp/usb-share.prev
PENDING=/data/local/tmp/usb-share.pending
LOG=/data/local/tmp/usb-share.log
UDC=musb-hdrc.1.auto
DEF_MB=4096
log() { echo "[share] $*" >>"$LOG"; }
say() { echo "$*"; log "$*"; }
mounted() { grep -q " $MNT " /proc/mounts; }
save_usb_config() {
    cur=$(getprop sys.usb.config 2>/dev/null)
    case "$cur" in
        *mtp*|*ptp*) echo "$cur" >"$PREV" ;;
        *) echo mtp,adb >"$PREV" ;;
    esac
    log "saved usb config: $(cat "$PREV")"
}
restore_usb() {
    prev=$(cat "$PREV" 2>/dev/null)
    case "$prev" in
        *mtp*|*ptp*) ;;
        *) prev=mtp,adb ;;
    esac
    setprop sys.usb.config none
    sleep 1
    setprop sys.usb.config "$prev"
    setprop sys.usb.ffs.ready 1 2>/dev/null
    setprop ctl.start adbd 2>/dev/null
    sleep 1
    rm -f "$PREV" "$PENDING"
    say "USB restored: $(getprop sys.usb.config) (file transfer + adb)"
}
free_loop() { losetup -f 2>/dev/null; }
ensure_dir() { mkdir -p "$MNT" || return 1; }
set_label() {
    chcon u:object_r:media_rw_data_file:s0 "$IMG" 2>/dev/null || true
}
do_init() {
    size_mb=${1:-$DEF_MB}
    if mounted; then say "unmount first (usb-share.sh local off)"; return 1; fi
    if [ -e "$IMG" ]; then
        say "image already exists: $IMG ($(du -h "$IMG" 2>/dev/null | cut -f1))"
        return 0
    fi
    say "creating ${size_mb}MB image $IMG"
    truncate -s "${size_mb}M" "$IMG" || return 1
    set_label
    loop=$(free_loop) || { say "no free loop device"; return 1; }
    losetup "$loop" "$IMG" || { say "losetup failed"; rm -f "$IMG"; return 1; }
    if ! newfs_msdos -F 32 -c 8 -L PHONELAB "$loop" >>"$LOG" 2>&1; then
        say "mkfs failed, see $LOG"
        losetup -d "$loop" 2>/dev/null
        rm -f "$IMG"
        return 1
    fi
    losetup -d "$loop" 2>/dev/null
    echo none >"$STATE"
    say "created $IMG"
}
do_local() {
    ensure_dir || return 1
    if mounted; then say "already mounted at $MNT"; return 0; fi
    if [ -e "$LINK" ]; then say "image is exported to the PC; run import first"; return 1; fi
    [ -f "$IMG" ] || { say "no image, run: usb-share.sh init"; return 1; }
    set_label
    loop=$(free_loop) || { say "no free loop device"; return 1; }
    losetup "$loop" "$IMG" || { say "losetup failed"; return 1; }
    if ! mount -t vfat -o rw,utf8,umask=000 "$loop" "$MNT"; then
        say "mount failed"
        losetup -d "$loop" 2>/dev/null
        return 1
    fi
    echo "local $loop" >"$STATE"
    say "mounted at $MNT (phone/agent side)"
}
do_unmount() {
    if mounted; then
        sync
        umount "$MNT" || { say "umount failed (busy?)"; return 1; }
    fi
    for l in $(losetup -a 2>/dev/null | grep "($IMG)" | cut -d: -f1); do
        losetup -d "$l" 2>/dev/null
    done
    echo none >"$STATE"
    say "unmounted"
}
do_sync() {
    src=${1:-$CH/root/work}
    [ -d "$src" ] || { say "no such directory: $src"; return 1; }
    mounted || { say "not mounted locally, run: usb-share.sh local"; return 1; }
    say "copying $src -> $MNT"
    if command -v rsync >/dev/null 2>&1; then
        rsync -a "$src"/ "$MNT"/ >>"$LOG" 2>&1 || return 1
    else
        cp -R "$src"/. "$MNT"/ >>"$LOG" 2>&1 || return 1
    fi
    sync
    say "copied: $(ls -A "$MNT" | wc -l) top-level entries"
}
gadget_add() {
    rm -f "$PENDING"
    echo pending >"$PENDING"
    setsid sh -c "sleep 45; [ -f '$PENDING' ] && sh '$0' restore-usb" \
        </dev/null >/dev/null 2>&1 &
    save_usb_config
    setprop sys.usb.config none
    i=0
    while [ -n "$(cat "$G/UDC" 2>/dev/null)" ] && [ $i -lt 20 ]; do sleep 0.5; i=$((i+1)); done
    mkdir -p "$FUNC" || return 1
    echo 0 >"$FUNC/lun.0/cdrom"
    echo 1 >"$FUNC/lun.0/removable"
    echo 0 >"$FUNC/lun.0/ro"
    echo 1 >"$FUNC/lun.0/nofua"
    echo "$IMG" >"$FUNC/lun.0/file" || { say "cannot set backing file"; return 1; }
    cd "$G" || return 1
    [ -e "$LINK" ] || ln -s functions/mass_storage.share configs/b.1/f6 || return 1
    echo "$UDC" >"$G/UDC" || return 1
    sleep 1
    [ -n "$(cat "$G/UDC" 2>/dev/null)" ] || { say "UDC bind failed"; return 1; }
    rm -f "$PENDING"
    say "exported as USB drive (FAT32, label PHONELAB); MTP+adb will return on import"
}
do_export() {
    src=${1:-}
    [ -f "$IMG" ] || { say "no image, run: usb-share.sh init"; return 1; }
    if [ -e "$LINK" ]; then say "already exported"; return 0; fi
    if [ -d "$FUNC" ]; then
        echo "" >"$FUNC/lun.0/file" 2>/dev/null
        rmdir "$FUNC/lun.0" 2>/dev/null
        rmdir "$FUNC" 2>/dev/null
    fi
    if [ -n "$src" ]; then
        do_local || return 1
        do_sync "$src" || return 1
    fi
    mounted && { do_unmount || return 1; }
    set_label
    gadget_add || { say "export failed, restoring USB"; restore_usb; return 1; }
    echo exported >"$STATE"
    say "safe to unplug only after: usb-share.sh import"
}
do_import() {
    if [ -e "$LINK" ]; then
        echo "" >"$G/UDC" 2>/dev/null
        rm -f "$LINK"
        if [ -d "$FUNC" ]; then
            echo "" >"$FUNC/lun.0/file" 2>/dev/null
            rmdir "$FUNC/lun.0" 2>/dev/null
            rmdir "$FUNC" 2>/dev/null
        fi
        restore_usb
    elif [ -d "$FUNC" ]; then
        echo "" >"$FUNC/lun.0/file" 2>/dev/null
        rmdir "$FUNC/lun.0" 2>/dev/null
        rmdir "$FUNC" 2>/dev/null
        say "removed stale mass-storage function"
    fi
    do_local
}
do_status() {
    if mounted; then echo "local" >"$STATE"
    elif [ -e "$LINK" ]; then echo exported >"$STATE"
    else echo none >"$STATE"; fi
    say "image:  $(ls -lh "$IMG" 2>/dev/null | awk '{print $5" "$6" "$7" "$8}' || echo missing)"
    say "state:  $(cat "$STATE" 2>/dev/null || echo unknown)"
    say "local:  $(mounted && echo "mounted at $MNT" || echo unmounted)"
    say "usb:    $([ -e "$LINK" ] && echo "exported" || echo "not exported")"
    say "udc:    $(cat "$G/UDC" 2>/dev/null || echo none)"
    if mounted; then say "files:  $(ls -A "$MNT" 2>/dev/null | wc -l) entries"; fi
}
case "$1" in
    init)   do_init "$2" ;;
    local)  do_local ;;
    off|umount) do_unmount ;;
    sync)   do_sync "$2" ;;
    export) do_export "$2" ;;
    import) do_import ;;
    restore-usb) restore_usb ;;
    status|"") do_status ;;
    *) say "usage: usb-share.sh init|local|off|sync|export|import|status"; exit 2 ;;
esac
