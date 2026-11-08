[CmdletBinding()]
param(
    [string]$KeyFile = (Join-Path $PSScriptRoot '..\..\keys\phone_lab_key.pub'),
    [switch]$UserKey
)
$ErrorActionPreference = 'Stop'
Write-Host '== OpenSSH Server =='
$cap = Get-WindowsCapability -Online -Name 'OpenSSH.Server*' | Select-Object -First 1
if ($cap.State -ne 'Installed') {
    Add-WindowsCapability -Online -Name $cap.Name | Out-Null
}
Set-Service -Name sshd -StartupType Automatic
Start-Service sshd
if (-not (Get-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH Server (sshd)' `
        -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 | Out-Null
}
Write-Host ("sshd: " + (Get-Service sshd).Status)
Write-Host '== phone key =='
if (Test-Path $KeyFile) {
    $key = (Get-Content $KeyFile -Raw).Trim()
    $line = 'from="127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12",restrict ' + $key
    if ($UserKey) {
        $dir = Join-Path $HOME '.ssh'
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $file = Join-Path $dir 'authorized_keys'
        if ((Test-Path $file) -and ((Get-Content $file -Raw) -match [regex]::Escape($key))) {
            Write-Host 'key already present'
        } else {
            Add-Content -Path $file -Value $line
            Write-Host "key added to $file"
        }
    } else {
        $file = Join-Path $env:ProgramData 'ssh\administrators_authorized_keys'
        if (-not (Test-Path $file)) { New-Item -ItemType File -Path $file -Force | Out-Null }
        if ((Test-Path $file) -and ((Get-Content $file -Raw -ErrorAction SilentlyContinue) -match [regex]::Escape($key))) {
            Write-Host 'key already present'
        } else {
            Add-Content -Path $file -Value $line
            Write-Host "key added to $file"
        }
        icacls $file /inheritance:r /grant 'Administrators:F' /grant 'SYSTEM:F' | Out-Null
    }
    Restart-Service sshd
} else {
    Write-Warning "key file not found: $KeyFile"
}
Write-Host ''
Write-Host 'IPv4 addresses (use one of these on the phone):'
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
    Format-Table InterfaceAlias, IPAddress -AutoSize
Write-Host 'Phone (Termux):  pcmount <windows_user>@<IP>'
Write-Host '                 linux  ->  cd /mnt/pc  ->  opencode'
