#!/data/data/com.termux/files/usr/bin/sh
SU=/system/xbin/su
CH=/data/linux/debian
shq() {
    printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}
in_chroot() {
    cmd="chroot $CH /usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /usr/local/bin/pl"
    for a in "$@"; do
        cmd="$cmd $(shq "$a")"
    done
    exec $SU -c "$cmd"
}
case "${1:-help}" in
    status)
        exec $SU -c /data/linux/bin/status.sh
        ;;
    zapret)
        shift
        exec zapret "$@"
        ;;
    hid)
        shift
        exec usb-hid "$@"
        ;;
    pcmount)
        shift
        exec pcmount "$@"
        ;;
    pcumount)
        exec pcumount
        ;;
    help|"")
        cat <<'EOS'
pl - phone-lab
  pl status                chroot/sshfs/zapret status
  pl zapret on|off|status  control zapret
  pl hid "text" [delay]    type text on the PC over USB HID
  pl pcmount user@host [port] [path]
  pl pcumount
  pl share status|local|sync [dir]|export [dir]|import|off
  pl plugins
  pl <plugin> [args]
EOS
        ;;
    *)
        in_chroot "$@"
        ;;
esac
