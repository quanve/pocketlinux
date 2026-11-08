#!/system/bin/sh
CH=/data/linux/debian
if ! grep -q " $CH/mnt/pc " /proc/mounts; then
    echo "pc: not mounted"
    exit 0
fi
chroot "$CH" /usr/bin/fusermount3 -u /mnt/pc 2>/dev/null
sleep 1
if grep -q " $CH/mnt/pc " /proc/mounts; then
    pkill -f "sshfs .* /mnt/pc" 2>/dev/null
    sleep 1
    umount "$CH/mnt/pc" 2>/dev/null
    umount -l "$CH/mnt/pc" 2>/dev/null
fi
if grep -q " $CH/mnt/pc " /proc/mounts; then
    echo "pc: unmount FAILED (still mounted)"
    exit 1
fi
echo "pc: unmounted"
