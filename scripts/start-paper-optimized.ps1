param(
  [Parameter(Mandatory = $true)]
  [string]$WorkDir,
  [string]$Jar = 'paper-1.21.11.jar',
  [string]$Xms = '2G',
  [string]$Xmx = '4G',
  [switch]$NoGui
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Java = (Get-ChildItem (Join-Path $Root '.tools') -Directory -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -like 'jdk-21*' } |
  Select-Object -First 1).FullName + '\bin\java.exe'
if (-not (Test-Path $Java)) { $Java = 'java' }

$wd = (Resolve-Path (Join-Path $Root $WorkDir)).Path
$args = @(
  "-Xms$Xms",
  "-Xmx$Xmx",
  '-XX:+UseG1GC',
  '-XX:+ParallelRefProcEnabled',
  '-XX:MaxGCPauseMillis=200',
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC',
  '-XX:+AlwaysPreTouch',
  '-XX:G1NewSizePercent=30',
  '-XX:G1MaxNewSizePercent=40',
  '-XX:G1HeapRegionSize=8M',
  '-XX:G1ReservePercent=20',
  '-XX:G1HeapWastePercent=5',
  '-XX:G1MixedGCCountTarget=4',
  '-XX:InitiatingHeapOccupancyPercent=15',
  '-XX:G1MixedGCLiveThresholdPercent=90',
  '-XX:G1RSetUpdatingPauseTimePercent=5',
  '-XX:SurvivorRatio=32',
  '-XX:+PerfDisableSharedMem',
  '-XX:MaxTenuringThreshold=1',
  '-Dfile.encoding=UTF-8',
  '-jar',
  $Jar
)
if ($NoGui) { $args += 'nogui' }
Push-Location $wd
try {
  & $Java @args
} finally {
  Pop-Location
}
