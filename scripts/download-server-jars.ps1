$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
function Get-PaperJar($version, $target) {
  $meta = Invoke-RestMethod "https://api.papermc.io/v2/projects/paper/versions/$version"
  $build = ($meta.builds | Sort-Object -Descending | Select-Object -First 1)
  $bm = Invoke-RestMethod "https://api.papermc.io/v2/projects/paper/versions/$version/builds/$build"
  $file = $bm.downloads.application.name
  Invoke-WebRequest "https://api.papermc.io/v2/projects/paper/versions/$version/builds/$build/downloads/$file" -OutFile $target
}
function Get-VelocityJar($version, $target) {
  $meta = Invoke-RestMethod "https://api.papermc.io/v2/projects/velocity/versions/$version"
  $build = ($meta.builds | Sort-Object -Descending | Select-Object -First 1)
  $bm = Invoke-RestMethod "https://api.papermc.io/v2/projects/velocity/versions/$version/builds/$build"
  $file = $bm.downloads.application.name
  Invoke-WebRequest "https://api.papermc.io/v2/projects/velocity/versions/$version/builds/$build/downloads/$file" -OutFile $target
}
$paperTemp = Join-Path $env:TEMP 'paper-1.21.11.jar'
Get-PaperJar '1.21.11' $paperTemp
foreach ($s in 'lobby-practice','event-1') { Copy-Item $paperTemp (Join-Path $root "$s/paper-1.21.11.jar") -Force }
$velTemp = Join-Path $env:TEMP 'velocity.jar'
Get-VelocityJar '3.4.0-SNAPSHOT' $velTemp
foreach ($v in 'velocity-na','velocity-eu') { Copy-Item $velTemp (Join-Path $root "$v/velocity.jar") -Force }
Write-Host 'Downloaded Paper 1.21.11 and Velocity jars.'
