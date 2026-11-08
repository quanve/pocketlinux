$ErrorActionPreference = 'SilentlyContinue'
Write-Host '== sshd =='
Get-Service sshd | Format-Table Name, Status, StartType -AutoSize
Write-Host '== key file =='
$f = Join-Path $env:ProgramData 'ssh\administrators_authorized_keys'
if (Test-Path $f) { Write-Host "$f exists"; (Get-Content $f | Measure-Object -Line).Lines; } else { Write-Host "$f missing" }
Write-Host '== IPv4 =='
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' } | Format-Table InterfaceAlias, IPAddress -AutoSize
