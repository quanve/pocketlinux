#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PL_STORAGE=${PL_STORAGE:-/storage/phone-lab}
if [ ! -d "$PL_STORAGE" ] && [ -d "$HOME/Disk/phone-lab" ]; then
    PL_STORAGE="$HOME/Disk/phone-lab"
fi
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/openjdk-bin-21}
if [ ! -x "$JAVA_HOME/bin/java" ] && command -v javac >/dev/null 2>&1; then
    JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
fi
if [ -x "$JAVA_HOME/bin/java" ]; then
    jv=$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F'"' '/version/{print $2; exit}')
    jmajor=${jv%%.*}
    case "$jmajor" in
        2[4-9]|3[0-9])
            if [ -x /usr/lib/jvm/openjdk-bin-21/bin/java ]; then
                echo ">> JAVA_HOME=$JAVA_HOME (Java $jv) is too new for Gradle; using JDK 21"
                JAVA_HOME=/usr/lib/jvm/openjdk-bin-21
            fi
            ;;
    esac
fi
ANDROID_HOME=${ANDROID_HOME:-$PL_STORAGE/android-sdk}
GRADLE_USER_HOME=${GRADLE_USER_HOME:-$PL_STORAGE/gradle-home}
GRADLE=${GRADLE:-$PL_STORAGE/toolchain/gradle-8.11.1/bin/gradle}
[ -x "$JAVA_HOME/bin/java" ] || { echo "java not found: $JAVA_HOME"; exit 2; }
[ -x "$GRADLE" ] || { echo "gradle not found: $GRADLE"; exit 2; }
[ -f "$ANDROID_HOME/platforms/android-35/android.jar" ] || {
    echo "Android SDK platform 35 not found under $ANDROID_HOME"; exit 3; }
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME" GRADLE_USER_HOME
"$GRADLE" -p "$ROOT/app" :app:assembleDebug --no-daemon --max-workers=2 --console=plain
APK="$ROOT/app/app/build/outputs/apk/debug/app-debug.apk"
ls -lh "$APK"
echo "APK: $APK"
