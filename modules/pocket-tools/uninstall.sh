#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
adb -s "$serial" shell "su -c 'chroot /data/linux/debian /bin/bash -lc \"DEBIAN_FRONTEND=noninteractive apt-get -y remove --purge tshark aircrack-ng macchanger nikto sqlmap hydra john; apt-get -y autoremove\"'" 2>&1 | tail -10
