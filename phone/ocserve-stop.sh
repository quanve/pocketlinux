#!/system/bin/sh
PIDFILE=/data/local/tmp/ocserve.pid
if [ ! -f "$PIDFILE" ]; then
    echo "opencode server: no pidfile"
    exit 0
fi
PID=$(cat "$PIDFILE")
kill "$PID" 2>/dev/null
sleep 1
kill -9 "$PID" 2>/dev/null
rm -f "$PIDFILE"
echo "opencode server stopped (pid $PID)"
