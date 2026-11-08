[CmdletBinding()]
param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$lines = & $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice\s*$' }
if ($lines.Count -ne 1) { throw "need exactly one adb device, found $($lines.Count)" }
$serial = ($lines[0] -split '\s+')[0]
$model = (& $Adb -s $serial shell getprop ro.product.model).Trim()
$vendorFp = (& $Adb -s $serial shell getprop ro.vendor.build.fingerprint).Trim()
if ($model -ne 'RMX3834') { throw "device $serial is $model, expected RMX3834" }
if ($vendorFp -notlike 'realme/RMX3834/*') { throw "device $serial vendor fingerprint is $vendorFp, expected realme/RMX3834/*" }
Write-Host "phone: $serial ($model)"
$apk = (Resolve-Path (Join-Path $PSScriptRoot '..\..\payload\phone-lab.apk')).Path
& $Adb -s $serial install -r $apk
$out = & $Adb -s $serial shell "cmd package list packages -U dev.quanve.phonelab"
$uid = ([regex]::Match($out, 'uid:(\d+)')).Groups[1].Value
if ($uid) {
    Write-Host "app uid: $uid"
    & $Adb -s $serial shell "su -c 'magisk --sqlite ""INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES ($uid,2,0,1,1);""'"
}
& $Adb -s $serial shell am force-stop dev.quanve.phonelab
& $Adb -s $serial shell am start -n dev.quanve.phonelab/.MainActivity
Write-Host 'installed and started'
