#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
APK=app/app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || pc/build-app.sh
exec pc/install-app.sh "$APK"
