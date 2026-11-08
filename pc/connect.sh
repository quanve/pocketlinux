#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
say() { printf '%s\n' "$*"; }
shq() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }
start_sshd() {
  if command -v rc-service >/dev/null 2>&1; then
    if command -v doas >/dev/null 2>&1; then doas rc-service sshd start
    else sudo rc-service sshd start; fi
  elif command -v systemctl >/dev/null 2>&1; then
    if command -v doas >/dev/null 2>&1; then doas systemctl start sshd
    else sudo systemctl start sshd; fi
  else
    say "ERROR: cannot start sshd automatically; start it manually"
    return 1
  fi
}
serial=$(pc/check-device.sh) || { say "ABORT: device guard failed"; exit 2; }
if ! ss -ltn 2>/dev/null | grep -q ':22 '; then
  say "sshd is not listening on :22, starting..."
  start_sshd
fi
adb -s "$serial" reverse --remove tcp:2222 >/dev/null 2>&1 || true
adb -s "$serial" reverse tcp:2222 tcp:22 >/dev/null
say "channel: phone 127.0.0.1:2222 -> PC sshd :22 (adb reverse)"
if ! adb -s "$serial" shell test -d /data/linux/debian >/dev/null 2>&1; then
  say "ERROR: /data/linux/debian not found. Install: pc/install-module.sh debian-chroot"
  exit 4
fi
target=${PL_PC_TARGET:-${USER:-$(id -un)}@127.0.0.1}
remote=${PL_PC_REMOTE:-$HOME}
say "mounting $target:$remote inside chroot..."
adb -s "$serial" shell su -c "/data/linux/bin/mount-pc.sh $(shq "$target") 2222 $(shq "$remote")"
say "--- status ---"
adb -s "$serial" shell su -c "/data/linux/bin/status.sh"
say "---"
say "Phone: open Termux, run:  linux"
say "Then:  cd /mnt/pc   (PC home, read/write)   and:  opencode"
