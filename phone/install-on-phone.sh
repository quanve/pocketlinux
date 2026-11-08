#!/system/bin/sh
set -u
PL=/data/local/tmp/pl
CH=/data/linux/debian
BIN=/data/linux/bin
FLAG=/data/linux/.debian-chroot-installed
TUX=$(stat -c %u /data/data/com.termux 2>/dev/null)
log() { echo "[install] $*"; }
mkdir -p "$CH" "$BIN"
if [ ! -f "$CH/etc/debian_version" ]; then
    log "extracting rootfs"
    rm -rf "$CH"; mkdir -p "$CH"
    tar -xzf "$PL/debian-rootfs.tar.gz" -C "$CH" || exit 1
else
    log "rootfs already present ($(cat "$CH/etc/debian_version"))"
fi
log "installing host scripts"
for f in mount-chroot.sh mount-pc.sh mount-pc-net.sh umount-pc.sh enter.sh status.sh zapret \
         usb-hid.sh hid-daemon.sh hid-loop.py ocserve-start.sh ocserve-stop.sh usb-share.sh \
         pc-bridge.sh; do
    [ -f "$PL/$f" ] && cp "$PL/$f" "$BIN/$f"
done
chmod 755 "$BIN"/*.sh "$BIN/zapret" "$BIN/hid-loop.py"
"$BIN/mount-chroot.sh"
mkdir -p "$CH/mnt/pc" 2>/dev/null || true
printf '#!/bin/sh\nexit 101\n' > "$CH/usr/sbin/policy-rc.d"
chmod 755 "$CH/usr/sbin/policy-rc.d"
echo 'nameserver 8.8.8.8' > "$CH/etc/resolv.conf"
echo 'nameserver 1.1.1.1' >> "$CH/etc/resolv.conf"
printf '127.0.0.1 localhost\n127.0.1.1 debian\n' > "$CH/etc/hosts"
if [ -f "$PL/debian-helpers.tar" ]; then
    log "installing Debian CLI helpers"
    tar -xf "$PL/debian-helpers.tar" -C "$CH"
    chown -R root:root "$CH/usr/local/sbin" "$CH/usr/local/bin" "$CH/root/.bashrc" \
        "$CH/root/.config/opencode/skills" 2>/dev/null
    chmod 755 "$CH"/usr/local/sbin/* "$CH"/usr/local/bin/* 2>/dev/null
    rm -f "$CH/etc/sudoers.d/phone-lab"
fi
if [ ! -f "$CH/.second-stage-done" ]; then
    log "debootstrap second stage"
    env PATH=/usr/sbin:/usr/bin:/sbin:/bin chroot "$CH" /debootstrap/debootstrap --second-stage || exit 1
    touch "$CH/.second-stage-done"
fi
log "apt update/install"
env PATH=/usr/sbin:/usr/bin:/sbin:/bin DEBIAN_FRONTEND=noninteractive \
    chroot "$CH" /usr/bin/apt-get -y -o Acquire::ForceIPv4=true update || exit 1
env PATH=/usr/sbin:/usr/bin:/sbin:/bin DEBIAN_FRONTEND=noninteractive \
    chroot "$CH" /usr/bin/apt-get -y -o Acquire::ForceIPv4=true \
    install curl ca-certificates git ripgrep unzip less nano tmux htop procps \
    iproute2 iw nmap tcpdump net-tools dnsutils netcat-openbsd socat rsync \
    whois traceroute mtr-tiny usbutils wireless-tools arp-scan hping3 \
    python3 python3-pip file jq tree zstd xz-utils sshfs fuse3 openssh-client \
    tzdata sudo || exit 1
if ! chroot "$CH" /usr/bin/id lab >/dev/null 2>&1; then
    log "creating user lab"
    chroot "$CH" /usr/sbin/useradd -m -s /bin/bash lab || exit 1
    chroot "$CH" /usr/bin/passwd -d lab >/dev/null 2>&1 || true
fi
if [ -f "$PL/plugins.tar" ]; then
    log "installing plugins"
    PLUGDIR=$CH/usr/local/lib/phone-lab/plugins
    rm -rf "$PLUGDIR"
    mkdir -p "$PLUGDIR"
    tar -xf "$PL/plugins.tar" -C "$PLUGDIR" || exit 1
    chown -R root:root "$PLUGDIR"
    find "$PLUGDIR" -name run -exec chmod 755 {} \;
    find "$PLUGDIR" -name install.sh -exec chmod 755 {} \;
    for d in "$PLUGDIR"/*/; do
        [ -f "$d/plugin.conf" ] || continue
        name=$(basename "$d")
        if [ -f "$d/skill/SKILL.md" ]; then
            mkdir -p "$CH/root/.config/opencode/skills/$name"
            cp "$d/skill/SKILL.md" "$CH/root/.config/opencode/skills/$name/SKILL.md"
            chown -R root:root "$CH/root/.config/opencode/skills/$name"
        fi
        if [ -f "$d/install.sh" ]; then
            log "plugin $name: install"
            chroot "$CH" /bin/sh "/usr/local/lib/phone-lab/plugins/$name/install.sh" || \
                log "plugin $name: install failed"
        fi
    done
fi
mkdir -p "$CH/root/.ssh"
cp "$PL/phone_lab_key" "$CH/root/.ssh/id_ed25519"
chmod 700 "$CH/root/.ssh"; chmod 600 "$CH/root/.ssh/id_ed25519"
if [ ! -x "$CH/usr/local/bin/opencode" ] || \
   ! chroot "$CH" /usr/bin/env -i HOME=/root PATH=/usr/local/bin:/usr/bin:/bin \
        /usr/local/bin/opencode --version 2>/dev/null | grep -q '^opencode v2'; then
    log "installing opencode 2.x"
    tar -xzf "$PL/opencode.tar.gz" -C "$CH/usr/local/bin" || exit 1
    chmod 755 "$CH/usr/local/bin/opencode"
    chown root:root "$CH/usr/local/bin/opencode"
fi
mkdir -p "$CH/home/lab/.config/opencode"
if [ ! -f "$CH/home/lab/.config/opencode/opencode.json" ]; then
    printf '{\n  "$schema": "https://opencode.ai/config.json"\n}\n' > "$CH/home/lab/.config/opencode/opencode.json"
fi
chown -R 1000:1000 "$CH/home/lab/.config" 2>/dev/null
mkdir -p /data/adb/service.d
cp "$PL/service.sh" /data/adb/service.d/00-phone-lab.sh
chmod 755 /data/adb/service.d/00-phone-lab.sh
if [ -n "$TUX" ]; then
    log "installing Termux commands (linux, pl, zapret, usb-hid, pcmount, pcumount)"
    for pair in "termux-linux.sh:linux" "termux-usb-hid.sh:usb-hid" "termux-zapret.sh:zapret" \
                "termux-pcmount.sh:pcmount" "termux-pcumount.sh:pcumount" "termux-pl.sh:pl"; do
        src=${pair%%:*}; dst=${pair##*:}
        cp "$PL/$src" "/data/data/com.termux/files/usr/bin/$dst"
        chmod 755 "/data/data/com.termux/files/usr/bin/$dst"
        chown "$TUX:$TUX" "/data/data/com.termux/files/usr/bin/$dst"
    done
    mkdir -p /data/data/com.termux/files/home/.shortcuts
    printf '#!/data/data/com.termux/files/usr/bin/sh\n/data/linux/bin/zapret toggle\n' \
        > /data/data/com.termux/files/home/.shortcuts/zapret-toggle
    chmod 755 /data/data/com.termux/files/home/.shortcuts/zapret-toggle
    chown -R "$TUX:$TUX" /data/data/com.termux/files/home/.shortcuts
fi
touch "$FLAG"
log "done"
"$BIN/status.sh"
