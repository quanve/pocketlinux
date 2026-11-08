#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
name="${1:?usage: uninstall-module.sh <name>}"
dir="modules/$name"
[ -d "$dir" ] || { echo "no such module: $name"; exit 2; }
[ -x "$dir/uninstall.sh" ] || { echo "$dir/uninstall.sh missing or not executable"; exit 3; }
exec "$dir/uninstall.sh"
