#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=$(pc/check-device.sh)
adb -s "$serial" shell "su -c 'chroot /data/linux/debian /bin/bash -lc \"DEBIAN_FRONTEND=noninteractive apt-get -y -o Acquire::ForceIPv4=true install tshark aircrack-ng macchanger nikto sqlmap hydra john\"'" 2>&1 | tail -20
adb -s "$serial" shell "su -c 'chroot /data/linux/debian /bin/bash -lc \"for t in nmap tshark aircrack-ng iw tcpdump ssh sshfs nikto sqlmap hydra john macchanger; do printf \\\"%-12s %s\\n\\\" \\\$t \\\$(command -v \\\$t || echo MISSING); done\"'"
