$ErrorActionPreference = 'Continue'
$phoneIp = '__IP__'
$phonePort = __PORT__
$token = '__TOKEN__'
$cmdTimeout = 60
$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($phoneIp, $phonePort)
} catch {
    exit 1
}
$stream = $client.GetStream()
$reader = New-Object System.IO.StreamReader($stream, [Text.Encoding]::UTF8)
$writer = New-Object System.IO.StreamWriter($stream, (New-Object Text.UTF8Encoding($false)))
$writer.AutoFlush = $true
$hostB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:COMPUTERNAME))
$writer.WriteLine("HELLO $token windows $hostB64")
while (($line = $reader.ReadLine()) -ne $null) {
    $parts = $line.Split(' ', 2)
    if ($parts.Length -lt 2) { continue }
    $id = $parts[0]
    try {
        $cmd = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($parts[1]))
    } catch { continue }
    $ps = $null
    $out = ''
    $rc = 0
    try {
        $ps = [PowerShell]::Create()
        $script = '$ErrorActionPreference = "Continue"; try { ' + $cmd +
            ' } catch { $_ | Out-String }; ' +
            '$global:PL_RC = if ($LASTEXITCODE -ne $null) { $LASTEXITCODE } else { 0 }'
        [void]$ps.AddScript($script)
        $async = $ps.BeginInvoke()
        if ($async.AsyncWaitHandle.WaitOne($cmdTimeout * 1000)) {
            $out = $ps.EndInvoke($async) | Out-String -Width 4096
            $rc = $ps.Runspace.SessionStateProxy.GetVariable('PL_RC')
            if ($null -eq $rc) { $rc = 0 }
        } else {
            $ps.Stop()
            $out = "timeout after $cmdTimeout s"
            $rc = 124
        }
        if ($ps.Streams.Error.Count -gt 0) {
            $out = $out + ($ps.Streams.Error | Out-String -Width 4096)
        }
    } catch {
        $out = ($_ | Out-String -Width 4096)
        $rc = 1
    } finally {
        if ($ps) { $ps.Dispose() }
    }
    if ($null -eq $out) { $out = '' }
    $outB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$out))
    $writer.WriteLine("$id $rc $outB64")
}
$client.Close()
