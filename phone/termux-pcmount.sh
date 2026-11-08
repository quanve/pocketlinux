#!/data/data/com.termux/files/usr/bin/sh
if [ $# -lt 1 ]; then
    echo "usage: pcmount [user@]host [port] [remote_path]"
    exit 1
fi
args=""
for a in "$@"; do
    esc=$(printf '%s' "$a" | sed "s/'/'\\\\''/g")
    args="$args '$esc'"
done
exec /system/xbin/su -c "/data/linux/bin/mount-pc-net.sh$args"
