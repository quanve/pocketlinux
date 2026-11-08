#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
name="${1:?usage: install-module.sh <name>}"
dir="modules/$name"
[ -d "$dir" ] || { echo "no such module: $name"; ls modules; exit 2; }
[ -x "$dir/install.sh" ] || { echo "$dir/install.sh missing or not executable"; exit 3; }
exec "$dir/install.sh"
