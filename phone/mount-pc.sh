#!/system/bin/sh
CH=/data/linux/debian
TARGET="${1:-${PL_PC_TARGET:-quanve@127.0.0.1}}"
PORT="${2:-2222}"
REMOTE="${3:-${PL_PC_REMOTE:-/home/quanve}}"
/data/linux/bin/mount-chroot.sh
if grep -q " $CH/mnt/pc " /proc/mounts; then
    echo "pc: already mounted at $CH/mnt/pc"
    exit 0
fi
mkdir -p "$CH/root/.ssh"
chroot "$CH" /usr/bin/env PATH=/usr/sbin:/usr/bin:/sbin:/bin /usr/bin/sshfs \
    -p "$PORT" \
    -o IdentityFile=/root/.ssh/id_ed25519 \
    -o StrictHostKeyChecking=accept-new \
    -o UserKnownHostsFile=/root/.ssh/known_hosts \
    -o allow_other \
    -o uid=1000 -o gid=1000 -o umask=022 \
    "$TARGET:$REMOTE" /mnt/pc
rc=$?
if [ $rc -eq 0 ]; then
    echo "pc: mounted $TARGET:$REMOTE -> $CH/mnt/pc (read/write)"
else
    echo "pc: sshfs failed (rc=$rc)"
fi
exit $rc
