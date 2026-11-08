#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT"
BASE=(debian hid zapret app)
usage() {
    cat <<'EOS'
phone-lab installer
  ./install.sh                  install Debian, USB HID, zapret and the app
  ./install.sh debian app       install selected components
  ./install.sh pocket           install optional pocket tools
  ./install.sh --list           list components
  ./install.sh --uninstall      uninstall the default components
  ./install.sh --uninstall app  uninstall selected components
Components: debian, hid, zapret, app, pocket
EOS
}
component_dir() {
    case "$1" in
        debian|debian-chroot) echo debian-chroot ;;
        hid|usb-hid)          echo usb-hid ;;
        zapret|zapret-toggle) echo zapret-toggle ;;
        app|android-app)      echo android-app ;;
        pocket|pocket-tools)  echo pocket-tools ;;
        *) return 1 ;;
    esac
}
if [ "${1:-}" = "--list" ]; then
    cat <<'EOS'
debian   Debian 13 arm64 chroot
hid      USB HID keyboard and mouse
zapret   zapret wrapper and Termux shortcut
app      Android app
pocket   optional network and security tools
EOS
    exit 0
fi
action=install
if [ "${1:-}" = "--uninstall" ]; then
    action=uninstall
    shift
fi
if [ "$#" -eq 0 ]; then
    set -- "${BASE[@]}"
fi
for name in "$@"; do
    dir=$(component_dir "$name") || {
        echo "unknown component: $name" >&2
        usage >&2
        exit 2
    }
    script="modules/$dir/$action.sh"
    if [ ! -x "$script" ]; then
        echo "missing: $script" >&2
        exit 3
    fi
    printf '\n== %s %s\n' "$action" "$dir"
    "$script"
done
