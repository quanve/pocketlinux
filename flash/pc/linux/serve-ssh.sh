#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
say() { printf '%s\n' "$*"; }
if ! ss -ltn 2>/dev/null | grep -q ':22 '; then
    say "starting sshd..."
    if command -v rc-service >/dev/null 2>&1; then
        if command -v doas >/dev/null 2>&1; then doas rc-service sshd start
        else sudo rc-service sshd start; fi
    elif command -v systemctl >/dev/null 2>&1; then
        if command -v doas >/dev/null 2>&1; then doas systemctl start sshd
        else sudo systemctl start sshd; fi
    else say "start sshd manually"; exit 1
    fi
fi
say "sshd is listening on :22"
say ""
say "IPv4 addresses of this PC:"
ip -4 -br addr show scope global 2>/dev/null | awk '{printf "  %-12s %s\n", $1, $3}'
say ""
say "Add the phone key on this PC once (idempotent):"
say "  $DIR/add-key.sh"
say ""
say "On the phone (Termux), with phone and PC on the same network:"
say "  pcmount $(id -un)@<PC_IP>"
say "  linux   ->   cd /mnt/pc   ->   opencode"
say ""
say "Public key (for Windows: run pc\\windows\\setup-sshd.ps1 as Administrator):"
say "  $(cat "$DIR/../../keys/phone_lab_key.pub")"
