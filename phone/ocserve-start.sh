#!/system/bin/sh
CH=/data/linux/debian
PWFILE=$CH/root/.config/phone-lab/server-password
LOG=/data/local/tmp/ocserve.log
PIDFILE=/data/local/tmp/ocserve.pid
PORT=${PL_OC_PORT:-4096}
mkdir -p "$(dirname "$PWFILE")"
if [ ! -s "$PWFILE" ]; then
    head -c 32 /dev/urandom | base64 | tr -d '\n' > "$PWFILE"
    chmod 600 "$PWFILE"
fi
PASS=$(cat "$PWFILE")
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    echo "opencode server already running (pid $(cat "$PIDFILE"), port $PORT)"
    exit 0
fi
setsid /system/bin/sh -c "echo \$\$ > '$PIDFILE'; exec chroot '$CH' /usr/bin/env -i \
    HOME=/root LANG=C.UTF-8 PATH=/usr/local/bin:/usr/bin:/bin \
    OPENCODE_SERVER_PASSWORD='$PASS' \
    /bin/bash -lc 'exec opencode serve --port $PORT --hostname 127.0.0.1'" \
    </dev/null >"$LOG" 2>&1 &
sleep 1
if [ -s "$PIDFILE" ]; then
    echo "started pid=$(cat "$PIDFILE") port=$PORT log=$LOG"
else
    echo "start failed, see $LOG"
fi
