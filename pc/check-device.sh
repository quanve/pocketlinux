#!/usr/bin/env bash
set -euo pipefail
expected_model="RMX3834"
expected_fp_prefix="realme/RMX3834/"
mapfile -t devs < <(adb devices | awk 'NR>1 && $2=="device"{print $1}')
if [ "${#devs[@]}" -ne 1 ]; then
  echo "FAIL: expected exactly 1 adb device, found ${#devs[@]}: ${devs[*]:-none}" >&2
  exit 2
fi
serial="${devs[0]}"
getprop() { adb -s "$serial" shell getprop "$1" 2>/dev/null | tr -d '\r'; }
model=$(getprop ro.product.model)
build_fp=$(getprop ro.build.fingerprint)
vendor_fp=$(getprop ro.vendor.build.fingerprint)
if [ "$model" != "$expected_model" ]; then
  echo "FAIL: device $serial is model=$model; refusing (phone 1 is $expected_model)" >&2
  exit 3
fi
if [[ "$build_fp" != "$expected_fp_prefix"* && "$vendor_fp" != "$expected_fp_prefix"* ]]; then
  echo "FAIL: device $serial fingerprint build=$build_fp vendor=$vendor_fp; refusing (phone 1 is $expected_fp_prefix*)" >&2
  exit 3
fi
echo "$serial"
