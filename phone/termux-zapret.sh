#!/data/data/com.termux/files/usr/bin/sh
args=""
for a in "$@"; do
    esc=$(printf '%s' "$a" | sed "s/'/'\\\\''/g")
    args="$args '$esc'"
done
exec /system/xbin/su -c "/data/linux/bin/zapret$args"
