param(
  [string]$Name,
  [string]$WorkDir,
  [string]$Jar,
  [int]$TimeoutSeconds = 160
)
$ErrorActionPreference = 'Stop'
$java = (Get-ChildItem .tools -Directory | Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1).FullName + '\bin\java.exe'
$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $java
$psi.WorkingDirectory = (Resolve-Path $WorkDir).Path
$psi.Arguments = "-Xms512M -Xmx1024M -jar $Jar nogui"
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $false
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$p = [System.Diagnostics.Process]::Start($psi)
$lines = [System.Collections.Generic.List[string]]::new()
$ready = $false; $failed = $false
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while((Get-Date) -lt $deadline -and -not $p.HasExited) {
  $task = $p.StandardOutput.ReadLineAsync()
  if($task.Wait(500)) {
    $line = $task.Result
    if($null -ne $line) {
      $lines.Add($line)
      if($line -match 'Done \(' -or $line -match 'Listening on' -or $line -match 'Velocity .* started') { $ready = $true; break }
      if($line -match 'Could not load|SEVERE|ERROR|Exception|Failed to start') { $failed = $true; break }
    }
  }
}
if(-not $p.HasExited) {
  $stopCommand = if($Jar -eq 'velocity.jar') { 'shutdown' } else { 'stop' }
  try { $p.StandardInput.WriteLine($stopCommand); $p.StandardInput.Flush() } catch {}
  if(-not $p.WaitForExit(45000)) { $p.Kill($true); $p.WaitForExit() }
}
while(-not $p.StandardOutput.EndOfStream) { $lines.Add($p.StandardOutput.ReadLine()) }
$log = Join-Path 'shared/docs/test-logs' "$Name.log"
$lines | Set-Content -Encoding UTF8 $log
$result = if($failed) { 'FAILED' } elseif($ready) { 'READY' } else { 'TIMEOUT' }
Write-Output "$Name $result exit=$($p.ExitCode) log=$log"
if($failed -or -not $ready){ exit 1 }
