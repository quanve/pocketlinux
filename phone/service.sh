#!/system/bin/sh
sleep 20
/data/linux/bin/mount-chroot.sh >/data/local/tmp/phone-lab-boot.log 2>&1
/data/linux/bin/pc-bridge.sh start >>/data/local/tmp/phone-lab-boot.log 2>&1
exit 0
