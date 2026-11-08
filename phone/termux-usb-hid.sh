#!/data/data/com.termux/files/usr/bin/sh
if [ $# -lt 1 ]; then
    echo "usage: usb-hid \"text to type\" [delay_seconds]"
    exit 1
fi
text="$1"
delay="${2:-3}"
esc=$(printf '%s' "$text" | sed "s/'/'\\\\''/g")
exec /system/xbin/su -c "/data/linux/bin/usb-hid.sh '$esc' '$delay'"
