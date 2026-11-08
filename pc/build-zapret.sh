#!/usr/bin/env bash
set -euo pipefail
WORK=${WORK:-/tmp/zapret-build}
OUT=${OUT:-$WORK/out}
ZAPRET_REPO=${ZAPRET_REPO:-https://github.com/bol-van/zapret.git}
ZAPRET_REF=${ZAPRET_REF:-c4d0b1990129dce2b28095277173ee0d7702d304}
PREFIX=$WORK/prefix
INC=$WORK/include
ALPINE=https://dl-cdn.alpinelinux.org/alpine/latest-stable/main/aarch64
TARGET=aarch64-linux-musl
CC="zig cc -target $TARGET"
export CC
log() { printf '>>> %s\n' "$*"; }
for tool in zig git make tar curl pkg-config ar ranlib; do
    command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done
mkdir -p "$WORK" "$OUT" "$PREFIX" "$INC"
cd "$WORK"
fetch_tar() {
    local url=$1 name=$2
    [ -d "$name" ] && return 0
    log "download $name"
    curl -fsSL "$url" -o "$name.tar"
    tar -xf "$name.tar"
    rm -f "$name.tar"
}
build_autotools() {
    local dir=$1
    log "build $dir"
    (
        cd "$dir"
        PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig" \
        PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig" \
        ./configure --host="$TARGET" --prefix="$PREFIX" \
            --enable-static --disable-shared --disable-dependency-tracking \
            >/dev/null
        make -j"$(nproc)" >/dev/null
        make install >/dev/null
    )
}
fetch_tar https://zlib.net/fossils/zlib-1.3.1.tar.gz zlib-1.3.1
log "build zlib-1.3.1"
(
    cd zlib-1.3.1
    CC="$CC" AR=ar RANLIB=ranlib ./configure --static --prefix="$PREFIX" >/dev/null
    make -j"$(nproc)" >/dev/null
    make install >/dev/null
)
fetch_tar https://netfilter.org/projects/libmnl/files/libmnl-1.0.5.tar.bz2 libmnl-1.0.5
fetch_tar https://netfilter.org/projects/libnfnetlink/files/libnfnetlink-1.0.2.tar.bz2 libnfnetlink-1.0.2
fetch_tar https://netfilter.org/projects/libnetfilter_queue/files/libnetfilter_queue-1.0.5.tar.bz2 libnetfilter_queue-1.0.5
build_autotools libmnl-1.0.5
build_autotools libnfnetlink-1.0.2
build_autotools libnetfilter_queue-1.0.5
apk_version() {
    local pkg=$1 idx=$WORK/apk/APKINDEX
    if [ ! -s "$idx" ]; then
        curl -fsSL "$ALPINE/APKINDEX.tar.gz" | tar -xzO APKINDEX > "$idx"
    fi
    awk -v P="$pkg" '$0=="P:"P{f=1} f&&/^V:/{print substr($0,3); exit}' "$idx"
}
apk_extract() {
    local pkg=$1 ver dir
    ver=$(apk_version "$pkg")
    [ -n "$ver" ] || { echo "cannot resolve apk $pkg" >&2; exit 1; }
    dir=$WORK/apk/$pkg
    [ -d "$dir" ] && return 0
    log "download $pkg-$ver.apk"
    mkdir -p "$dir"
    curl -fsSL "$ALPINE/$pkg-$ver.apk" -o "$WORK/apk/$pkg.apk"
    tar -xzf "$WORK/apk/$pkg.apk" -C "$dir" 2>/dev/null
}
mkdir -p "$WORK/apk"
apk_extract bsd-compat-headers
apk_extract libcap-dev
apk_extract libcap-static
cp -R "$WORK/apk/bsd-compat-headers/usr/include/." "$INC/"
cp -R "$WORK/apk/libcap-dev/usr/include/." "$INC/"
if [ ! -d "$WORK/zapret/.git" ]; then
    log "clone zapret"
    git clone "$ZAPRET_REPO" "$WORK/zapret"
fi
log "checkout $ZAPRET_REF"
git -C "$WORK/zapret" fetch --depth 1 origin "$ZAPRET_REF" 2>/dev/null || true
git -C "$WORK/zapret" checkout -q "$ZAPRET_REF"
log "build nfqws"
cd "$WORK/zapret/nfq"
zig cc -target "$TARGET" \
    -Os -std=gnu99 -s -Wno-error=date-time \
    -ffunction-sections -fdata-sections \
    -I"$INC" -I"$PREFIX/include" \
    -o "$OUT/nfqws" \
    ./*.c ./crypto/*.c \
    -L"$PREFIX/lib" -L"$WORK/apk/libcap-static/usr/lib" \
    -Wl,--gc-sections -static \
    -lz -lnetfilter_queue -lnfnetlink -lmnl -lcap
chmod 755 "$OUT/nfqws"
file "$OUT/nfqws"
sha256sum "$OUT/nfqws"
echo "done: $OUT/nfqws"
