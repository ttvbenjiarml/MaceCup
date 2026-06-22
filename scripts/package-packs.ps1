# MaceCup Network Pack Packaging Tool
$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$resourcePackSrc = Join-Path $Root "source\MaceCupResourcePack"
$resourcePackDest = Join-Path $Root "shared\resource-pack\MaceCupResourcePack.zip"
$datapackSrc = Join-Path $Root "source\MaceCupDatapack"
$datapackDest = Join-Path $Root "shared\datapack\MaceCupDatapack.zip"

Write-Host "Compressing Resource Pack..."
if (Test-Path $resourcePackDest) { Remove-Item $resourcePackDest -Force }
# Zip contents, not the folder itself
Compress-Archive -Path (Join-Path $resourcePackSrc "*") -DestinationPath $resourcePackDest -Force

Write-Host "Compressing Datapack..."
if (Test-Path $datapackDest) { Remove-Item $datapackDest -Force }
Compress-Archive -Path (Join-Path $datapackSrc "*") -DestinationPath $datapackDest -Force

Write-Host "Calculating SHA-1 hash for Resource Pack..."
$sha1 = (Get-FileHash $resourcePackDest -Algorithm SHA1).Hash.ToLowerInvariant()
$sha1Path = Join-Path $Root "shared\resource-pack\SHA1.txt"
$sha1 | Out-File -FilePath $sha1Path -NoNewline -Encoding ascii

Write-Host "New Resource Pack SHA-1: $sha1"

# Update SHA-1 config value in backend config template
$coreConfigPath = Join-Path $Root "source\MaceCupCore\src\main\resources\config.yml"
if (Test-Path $coreConfigPath) {
    $content = Get-Content $coreConfigPath
    $content = $content -replace "sha1: '.*'", "sha1: '$sha1'"
    $content | Set-Content $coreConfigPath
    Write-Host "Updated SHA-1 in source config.yml."
}

Write-Host "Packaging completed successfully!"
