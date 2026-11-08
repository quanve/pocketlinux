[CmdletBinding()]
param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$lines = & $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice\s*$' }
if ($lines.Count -ne 1) { throw "need exactly one adb device, found $($lines.Count)" }
$serial = ($lines[0] -split '\s+')[0]
$model = (& $Adb -s $serial shell getprop ro.product.model).Trim()
$buildFp = (& $Adb -s $serial shell getprop ro.build.fingerprint).Trim()
$vendorFp = (& $Adb -s $serial shell getprop ro.vendor.build.fingerprint).Trim()
if ($model -ne 'RMX3834') { throw "device $serial is $model, expected RMX3834" }
if (($buildFp -notlike 'realme/RMX3834/*') -and ($vendorFp -notlike 'realme/RMX3834/*')) {
    throw "device $serial fingerprint build=$buildFp vendor=$vendorFp, expected realme/RMX3834/*"
}
Write-Host "phone: $serial ($model)"
$tmp = Join-Path $env:TEMP 'phone-lab-debian-helpers.tar'
Push-Location (Join-Path $Root 'phone\debian')
& tar.exe -cf $tmp usr/local/sbin usr/local/bin root/.bashrc root/.config/opencode/skills
Pop-Location
$plug = Join-Path $env:TEMP 'phone-lab-plugins.tar'
Push-Location (Join-Path $Root 'plugins')
& tar.exe -cf $plug .
Pop-Location
$share = Join-Path $env:TEMP 'phone-lab-share'
Remove-Item $share -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path (Join-Path $share 'usr/local/share/phone-lab') -Force | Out-Null
Copy-Item (Join-Path $Root 'pc\bootstrap\linux.sh') (Join-Path $share 'usr/local/share/phone-lab\linux.sh')
Copy-Item (Join-Path $Root 'pc\bootstrap\windows.ps1') (Join-Path $share 'usr/local/share/phone-lab\windows.ps1')
Push-Location $share
& tar.exe -rf $tmp usr/local/share/phone-lab
Pop-Location
& $Adb -s $serial shell mkdir -p /data/local/tmp/pl
& $Adb -s $serial push (Join-Path $Root 'payload\debian-rootfs.tar.gz') /data/local/tmp/pl/debian-rootfs.tar.gz
& $Adb -s $serial push (Join-Path $Root 'payload\opencode-linux-arm64.tar.gz') /data/local/tmp/pl/opencode.tar.gz
& $Adb -s $serial push (Join-Path $Root 'keys\phone_lab_key') /data/local/tmp/pl/phone_lab_key
& $Adb -s $serial push $tmp /data/local/tmp/pl/debian-helpers.tar
& $Adb -s $serial push $plug /data/local/tmp/pl/plugins.tar
$files = 'mount-chroot.sh','mount-pc.sh','mount-pc-net.sh','umount-pc.sh','enter.sh','status.sh',
         'zapret','usb-hid.sh','hid-daemon.sh','hid-loop.py','ocserve-start.sh','ocserve-stop.sh','usb-share.sh',
         'pc-bridge.sh','service.sh','install-on-phone.sh',
         'termux-linux.sh','termux-usb-hid.sh','termux-zapret.sh',
         'termux-pcmount.sh','termux-pcumount.sh','termux-pl.sh'
foreach ($f in $files) {
    & $Adb -s $serial push (Join-Path $Root "phone\$f") "/data/local/tmp/pl/$f"
}
& $Adb -s $serial shell "su -c 'sh /data/local/tmp/pl/install-on-phone.sh'"
