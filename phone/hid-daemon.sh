#!/system/bin/sh
G=/config/usb_gadget/g1
LOG=/data/local/tmp/hid-daemon.log
exec 2>>"$LOG"
echo "=== hid-daemon start $(date)" >>"$LOG"
cleanup() {
    echo "" > "$G/UDC" 2>/dev/null
    rm -f "$G/configs/b.1/f5" "$G/configs/b.1/f6"
    rmdir "$G/functions/hid.usb0" "$G/functions/hid.usb1" 2>/dev/null
    setprop sys.usb.config adb
    echo "=== HID removed, adb restored $(date)" >>"$LOG"
}
trap 'cleanup; exit 0' EXIT INT TERM HUP
[ -e /dev/hidg0 ] && { echo "hidg0 already exists"; exit 1; }
setprop sys.usb.config none
i=0
while [ -n "$(cat "$G/UDC" 2>/dev/null)" ] && [ $i -lt 12 ]; do sleep 0.5; i=$((i+1)); done
echo "udc=[$(cat "$G/UDC" 2>/dev/null)]"
mkdir -p "$G/functions/hid.usb0" "$G/functions/hid.usb1" || { echo "mkdir failed"; exit 1; }
echo 1 > "$G/functions/hid.usb0/protocol"
echo 1 > "$G/functions/hid.usb0/subclass"
echo 8 > "$G/functions/hid.usb0/report_length"
printf '\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x03\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x03\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0' > "$G/functions/hid.usb0/report_desc"
echo 0 > "$G/functions/hid.usb1/protocol"
echo 0 > "$G/functions/hid.usb1/subclass"
echo 4 > "$G/functions/hid.usb1/report_length"
printf '\x05\x01\x09\x02\xa1\x01\x09\x01\xa1\x00\x05\x09\x19\x01\x29\x03\x15\x00\x25\x01\x95\x03\x75\x01\x81\x02\x95\x01\x75\x05\x81\x03\x05\x01\x09\x30\x09\x31\x15\x81\x25\x7f\x75\x08\x95\x02\x81\x06\x09\x38\x15\x81\x25\x7f\x75\x08\x95\x01\x81\x06\xc0\xc0' > "$G/functions/hid.usb1/report_desc"
cd "$G" || exit 1
ln -s functions/hid.usb0 configs/b.1/f5 || { echo "symlink kbd failed"; exit 1; }
ln -s functions/hid.usb1 configs/b.1/f6 || { echo "symlink mouse failed"; exit 1; }
echo musb-hdrc.1.auto > "$G/UDC" || { echo "bind failed"; exit 1; }
sleep 2
[ -e /dev/hidg0 ] && [ -e /dev/hidg1 ] || { echo "hidg missing"; exit 1; }
echo READY
PY=/data/data/com.termux/files/usr/bin/python3
if [ -x "$PY" ]; then
    "$PY" /data/linux/bin/hid-loop.py
else
    echo "python3 not found" >&2
    exit 1
fi
