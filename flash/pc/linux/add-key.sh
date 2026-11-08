#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
KEY=$(cat "$DIR/../../keys/phone_lab_key.pub")
LINE="from=\"127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12\",restrict $KEY"
mkdir -p "$HOME/.ssh"; chmod 700 "$HOME/.ssh"
touch "$HOME/.ssh/authorized_keys"; chmod 600 "$HOME/.ssh/authorized_keys"
if grep -qF "$KEY" "$HOME/.ssh/authorized_keys" 2>/dev/null; then
    echo "key already present in $HOME/.ssh/authorized_keys"
else
    echo "$LINE" >> "$HOME/.ssh/authorized_keys"
    echo "key added to $HOME/.ssh/authorized_keys"
fi
