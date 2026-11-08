#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
serial=$(pc/check-device.sh)
ts=$(date +%Y%m%d-%H%M%S)
out="backups/partitions-$ts"
mkdir -p "$out"
echo "serial=$serial out=$out"
parts=(
  boot_a boot_b init_boot_a init_boot_b vendor_boot_a vendor_boot_b
  dtb_a dtb_b dtbo_a dtbo_b vbmeta_a vbmeta_b
  vbmeta_system_a vbmeta_system_b vbmeta_vendor_a vbmeta_vendor_b
  vbmeta_product_a vbmeta_product_b vbmeta_odm_a vbmeta_odm_b
  vbmeta_system_ext_a vbmeta_system_ext_b avbmeta_rs_a avbmeta_rs_b
  persist prodnv calinv ocdt_a ocdt_b
  l_fixnv1_a l_fixnv1_b l_fixnv2_a l_fixnv2_b l_deltanv_a l_deltanv_b
  l_modem_a l_modem_b l_runtimenv1 l_runtimenv2
  l_gdsp_a l_gdsp_b l_ldsp_a l_ldsp_b l_agdsp_a l_agdsp_b
  pm_sys_a pm_sys_b teecfg_a teecfg_b trustos_a trustos_b
  sml_a sml_b uboot_a uboot_b uboot_log misc miscdata
  metadata oplusreserve1 oplusreserve3 oplusreserve5 opporeserve
  common_rs1_a common_rs1_b common_rs2_a common_rs2_b
)
echo "# partition backups $ts" > "$out/MANIFEST.txt"
echo "serial=$serial" >> "$out/MANIFEST.txt"
echo "fingerprint=$(adb -s "$serial" shell getprop ro.build.fingerprint | tr -d '\r')" >> "$out/MANIFEST.txt"
for p in "${parts[@]}"; do
  echo "== $p"
  adb -s "$serial" exec-out su -c "dd if=/dev/block/by-name/$p bs=1M 2>/dev/null" > "$out/$p.img"
  dev=$(adb -s "$serial" shell su -c "sha256sum /dev/block/by-name/$p" | awk '{print $1}' | tr -d '\r')
  loc=$(sha256sum "$out/$p.img" | awk '{print $1}')
  if [ "$dev" != "$loc" ]; then
    echo "CHECKSUM MISMATCH for $p (dev=$dev loc=$loc)" >&2
    echo "MISMATCH $p $loc dev=$dev" >> "$out/MANIFEST.txt"
    exit 4
  fi
  sz=$(stat -c %s "$out/$p.img")
  echo "$p $sz $loc" >> "$out/MANIFEST.txt"
done
adb -s "$serial" exec-out su -c "dd if=/dev/block/mmcblk0 bs=512 count=34 2>/dev/null" > "$out/gpt-primary.bin"
sectors=$(adb -s "$serial" shell su -c "cat /sys/class/block/mmcblk0/size" | tr -d '\r')
skip=$((sectors - 34))
adb -s "$serial" exec-out su -c "dd if=/dev/block/mmcblk0 bs=512 skip=$skip count=34 2>/dev/null" > "$out/gpt-backup.bin"
{
  echo "sectors=$sectors"
  adb -s "$serial" shell su -c "for d in /dev/block/by-name/*; do echo \"\$(basename \$d) \$(blockdev --getsize64 \$d)\"; done"
} > "$out/partition-table.txt"
echo DONE "$out"
