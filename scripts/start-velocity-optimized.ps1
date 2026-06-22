param(
  [Parameter(Mandatory = $true)]
  [string]$WorkDir,
  [string]$Jar = 'velocity.jar',
  [string]$Xms = '512M',
  [string]$Xmx = '1536M'
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
  '-XX:+DisableExplicitGC',
  '-XX:+PerfDisableSharedMem',
  '-Dfile.encoding=UTF-8',
  '-jar',
  $Jar
)
Push-Location $wd
try {
  & $Java @args
} finally {
  Pop-Location
}
