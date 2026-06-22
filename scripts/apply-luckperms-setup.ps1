param(
  [ValidateSet('all','backends','proxies')]
  [string]$Scope = 'all'
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Java = (Get-ChildItem (Join-Path $Root '.tools') -Directory | Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1).FullName + '\bin\java.exe'
if (-not (Test-Path $Java)) { throw 'JDK 21 java.exe was not found under .tools' }

$Commands = Get-Content (Join-Path $Root 'shared\docs\LuckPerms\setup-commands.txt') |
  Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') } |
  ForEach-Object { $_.Trim().TrimStart('/') }

$Targets = @(
  @{ Name = 'lobby-practice'; Dir = 'lobby-practice'; Jar = 'paper-1.21.11.jar'; Ready = 'Done \('; Stop = 'stop' },
  @{ Name = 'event-1'; Dir = 'event-1'; Jar = 'paper-1.21.11.jar'; Ready = 'Done \('; Stop = 'stop' },
  @{ Name = 'velocity-na'; Dir = 'velocity-na'; Jar = 'velocity.jar'; Ready = 'Listening on|Done|MaceCupProxyBridge ready'; Stop = 'shutdown' },
  @{ Name = 'velocity-eu'; Dir = 'velocity-eu'; Jar = 'velocity.jar'; Ready = 'Listening on|Done|MaceCupProxyBridge ready'; Stop = 'shutdown' }
)
if ($Scope -eq 'backends') { $Targets = $Targets | Where-Object { $_.Jar -like 'paper-*' } }
if ($Scope -eq 'proxies') { $Targets = $Targets | Where-Object { $_.Jar -eq 'velocity.jar' } }

$logDir = Join-Path $Root 'shared\docs\test-logs\luckperms-setup'
New-Item -ItemType Directory -Force $logDir | Out-Null

foreach ($target in $Targets) {
  $wd = Join-Path $Root $target.Dir
  $latest = Join-Path $wd 'logs\latest.log'
  $commandLog = Join-Path $logDir "$($target.Name).commands.log"
  Remove-Item $latest,$commandLog -Force -ErrorAction SilentlyContinue

  Write-Host "Applying LuckPerms setup on $($target.Name)"
  $psi = [System.Diagnostics.ProcessStartInfo]::new()
  $psi.FileName = $Java
  $psi.Arguments = "-Xms384M -Xmx1024M -jar `"$($target.Jar)`" nogui"
  $psi.WorkingDirectory = $wd
  $psi.UseShellExecute = $false
  $psi.RedirectStandardInput = $true
  $psi.CreateNoWindow = $true
  $process = [System.Diagnostics.Process]::Start($psi)

  try {
    $deadline = (Get-Date).AddSeconds(240)
    $ready = $false
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
      Start-Sleep -Milliseconds 500
      $text = if (Test-Path $latest) { Get-Content $latest -Raw } else { '' }
      if ($text -match $target.Ready) { $ready = $true; break }
      if ($text -match 'SEVERE|Could not load|Failed to start|Address already in use') { throw "$($target.Name) failed during startup" }
    }
    if (-not $ready) { throw "$($target.Name) did not become ready during LuckPerms setup" }

    $targetCommands = if ($target.Jar -eq 'velocity.jar') {
      $Commands | ForEach-Object { $_ -replace '^lp(\s|$)', 'lpv$1' }
    } else {
      $Commands
    }

    foreach ($command in $targetCommands) {
      Add-Content -LiteralPath $commandLog -Value $command
      $process.StandardInput.WriteLine($command)
      $process.StandardInput.Flush()
      Start-Sleep -Milliseconds 250
    }
    $syncCommand = if ($target.Jar -eq 'velocity.jar') { 'lpv sync' } else { 'lp sync' }
    foreach ($command in @($syncCommand, $target.Stop)) {
      Add-Content -LiteralPath $commandLog -Value $command
      $process.StandardInput.WriteLine($command)
      $process.StandardInput.Flush()
      Start-Sleep -Milliseconds 500
    }

    if (-not $process.WaitForExit(90000)) {
      Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
      throw "$($target.Name) did not stop after LuckPerms setup"
    }
  } finally {
    if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
  }
}

Write-Host 'LuckPerms setup commands applied to all configured servers.'
