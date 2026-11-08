#!/system/bin/sh
CH=/data/linux/debian
/data/linux/bin/mount-chroot.sh
exec chroot "$CH" /usr/bin/env -i \
    HOME=/root TERM="${TERM:-xterm-256color}" LANG=C.UTF-8 \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    /bin/bash -l
