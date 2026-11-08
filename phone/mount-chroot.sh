#!/system/bin/sh
CH=/data/linux/debian
mounted() { grep -q " $1 " /proc/mounts; }
mkdir -p "$CH/proc" "$CH/sys" "$CH/dev" "$CH/dev/pts" "$CH/dev/shm" "$CH/run" "$CH/mnt/pc"
mounted "$CH/proc" || mount -t proc proc "$CH/proc"
mounted "$CH/sys"  || mount -t sysfs sysfs "$CH/sys"
mounted "$CH/dev"  || mount --bind /dev "$CH/dev"
mounted "$CH/dev/pts" || mount --bind /dev/pts "$CH/dev/pts"
mounted "$CH/dev/shm" || mount -t tmpfs tmpfs "$CH/dev/shm"
mounted "$CH/run" || mount -t tmpfs tmpfs "$CH/run"
