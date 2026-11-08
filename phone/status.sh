#!/system/bin/sh
CH=/data/linux/debian
echo "== system =="
echo "  model: $(getprop ro.product.model)"
echo "  android: $(getprop ro.build.version.release)"
echo "  magisk: $(magisk -V 2>/dev/null || echo none)"
IP=$(ip -4 addr show wlan0 2>/dev/null | sed -n 's/.*inet \([0-9.]*\).*/\1/p' | head -n 1)
[ -n "$IP" ] && echo "  ip: $IP"
UP=$(cut -d. -f1 /proc/uptime 2>/dev/null)
[ -n "$UP" ] && echo "  uptime: $((UP / 86400))d $((UP % 86400 / 3600))h"
BAT=$(dumpsys battery 2>/dev/null | sed -n 's/.*level: \([0-9]*\).*/\1/p' | head -n 1)
[ -n "$BAT" ] && echo "  battery: $BAT%"
FREE=$(df -h /data 2>/dev/null | sed -n '2p' | awk '{print $4}')
[ -n "$FREE" ] && echo "  data: $FREE free"
echo "== agent =="
if [ -f /data/local/tmp/ocserve.pid ] && kill -0 "$(cat /data/local/tmp/ocserve.pid 2>/dev/null)" 2>/dev/null; then
    echo "  opencode: running"
else
    echo "  opencode: stopped"
fi
if [ -f /data/local/tmp/pc-bridge.pid ] && kill -0 "$(cat /data/local/tmp/pc-bridge.pid 2>/dev/null)" 2>/dev/null; then
    echo "  bridge: running"
else
    echo "  bridge: stopped"
fi
echo "== share =="
echo "  state: $(cat /data/local/tmp/usb-share.state 2>/dev/null || echo none)"
echo "== chroot =="
for m in proc sys dev dev/pts run mnt/pc; do
    if grep -q " $CH/$m " /proc/mounts; then echo "  $m: mounted"; else echo "  $m: -"; fi
done
echo "== debian =="
if [ -x "$CH/bin/bash" ]; then
    chroot "$CH" /bin/bash -lc 'echo "  debian: $(. /etc/os-release; echo $PRETTY_NAME)"; echo "  cli: $(command -v opencode || echo none)"' 2>/dev/null
fi
echo "== zapret =="
/data/adb/zapret/ctl.sh status 2>/dev/null | sed 's/^/  /'
echo "== usb =="
echo "  udc: $(cat /config/usb_gadget/g1/UDC 2>/dev/null)"
