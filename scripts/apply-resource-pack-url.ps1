param(
    [Parameter(Mandatory = $true)]
    [string] $Url,
    [string] $PackId = 'bafd3464-423c-4e03-930b-0e5ba2d83594',
    [string] $PromptJson = '{"text":"Our server uses a custom resource pack!", "color":"gold"}',
    [string] $PromptText = 'Our server uses a custom resource pack!'
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pack = Join-Path $root 'shared\resource-pack\MaceCupResourcePack.zip'
$sha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $pack).Hash.ToLowerInvariant()

$proxyConfigs = @(
    'velocity-na\plugins\MaceCupProxyBridge\config.yml',
    'velocity-eu\plugins\MaceCupProxyBridge\config.yml'
)

foreach ($rel in $proxyConfigs) {
    $path = Join-Path $root $rel
    $text = Get-Content -Raw -LiteralPath $path
    $text = $text -replace "(?m)^  host-enabled: .*$", "  host-enabled: false"
    $text = $text -replace "(?m)^  public-url: .*$", "  public-url: '$Url'"
    Set-Content -LiteralPath $path -Value $text -NoNewline
}

$backendConfigs = @(
    'lobby-practice\plugins\MaceCupCore\config.yml',
    'event-1\plugins\MaceCupCore\config.yml'
)

foreach ($rel in $backendConfigs) {
    $path = Join-Path $root $rel
    $text = Get-Content -Raw -LiteralPath $path
    $text = $text -replace "(?m)^  update-server-properties: .*$", "  update-server-properties: false"
    $text = $text -replace "(?m)^  url: .*$", "  url: $Url"
    $text = $text -replace "(?m)^  sha1: .*$", "  sha1: $sha1"
    $text = $text -replace "(?m)^  prompt: .*$", "  prompt: $PromptText"
    Set-Content -LiteralPath $path -Value $text -NoNewline
}

$serverProperties = @(
    'lobby-practice\server.properties',
    'event-1\server.properties'
)

foreach ($rel in $serverProperties) {
    $path = Join-Path $root $rel
    $text = Get-Content -Raw -LiteralPath $path
    $text = $text -replace "(?m)^resource-pack=.*$", "resource-pack=$Url"
    $text = $text -replace "(?m)^resource-pack-sha1=.*$", "resource-pack-sha1=$sha1"
    $text = $text -replace "(?m)^resource-pack-id=.*$", "resource-pack-id=$PackId"
    $text = $text -replace "(?m)^resource-pack-prompt=.*$", "resource-pack-prompt=$PromptJson"
    $text = $text -replace "(?m)^require-resource-pack=.*$", "require-resource-pack=true"
    Set-Content -LiteralPath $path -Value $text -NoNewline
}

Write-Host "Set proxy resource-pack.public-url to $Url"
Write-Host "Resource pack SHA1: $sha1"
