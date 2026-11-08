#!/system/bin/sh
CH=/data/linux/debian
LOG=/data/local/tmp/pc-bridge.log
PIDFILE=/data/local/tmp/pc-bridge.pid
PC_PORT=${PL_PC_PORT:-4444}
CTL_PORT=${PL_PC_CTL_PORT:-4445}
is_running() {
    [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE" 2>/dev/null)" 2>/dev/null
}
start() {
    if is_running; then
        echo "pc-bridge already running (pid $(cat "$PIDFILE"), port $PC_PORT)"
        return 0
    fi
    LOCK=/data/local/tmp/pc-bridge.lock
    if ! mkdir "$LOCK" 2>/dev/null; then
        echo "pc-bridge start already in progress"
        return 0
    fi
    trap 'rmdir "$LOCK" 2>/dev/null' EXIT INT TERM HUP
    /data/linux/bin/mount-chroot.sh >/dev/null 2>&1 || true
    setsid /system/bin/sh -c "echo \$\$ > '$PIDFILE'; exec chroot '$CH' /usr/bin/env -i \
        HOME=/root LANG=C.UTF-8 PATH=/usr/local/bin:/usr/bin:/bin \
        PYTHONDONTWRITEBYTECODE=1 \
        PL_PC_PORT=$PC_PORT PL_PC_CTL_PORT=$CTL_PORT \
        /usr/bin/python3 /usr/local/bin/pc-bridge.py" \
        </dev/null >"$LOG" 2>&1 &
    sleep 1
    if is_running; then
        echo "started pid=$(cat "$PIDFILE") pc=0.0.0.0:$PC_PORT ctl=127.0.0.1:$CTL_PORT"
    else
        echo "start failed, see $LOG"
        tail -5 "$LOG" 2>/dev/null
        return 1
    fi
}
stop() {
    if is_running; then
        pid=$(cat "$PIDFILE")
        kill "$pid" 2>/dev/null
        i=0
        while kill -0 "$pid" 2>/dev/null && [ $i -lt 10 ]; do
            sleep 0.3
            i=$((i + 1))
        done
        kill -9 "$pid" 2>/dev/null
        echo "stopped pid=$pid"
    else
        echo "not running"
    fi
    rm -f "$PIDFILE"
}
status() {
    if is_running; then
        echo "running pid=$(cat "$PIDFILE") pc=0.0.0.0:$PC_PORT ctl=127.0.0.1:$CTL_PORT"
        tail -3 "$LOG" 2>/dev/null
    else
        echo "not running"
        return 1
    fi
}
case "${1:-status}" in
    start) start ;;
    stop) stop ;;
    restart) stop; start ;;
    status) status ;;
    *) echo "usage: pc-bridge.sh start|stop|restart|status"; exit 2 ;;
esac
