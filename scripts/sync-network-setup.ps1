$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Backends = @('lobby-practice','event-1')
$Proxies = @('velocity-na','velocity-eu')
$Sha = (Get-Content (Join-Path $Root 'shared\resource-pack\SHA1.txt') -Raw).Trim()

function Set-Line($Path, $Pattern, $Replacement) {
  $text = Get-Content $Path -Raw
  $text = $text -replace $Pattern, $Replacement
  Set-Content -LiteralPath $Path -Value $text -NoNewline
}

foreach ($server in $Backends) {
  $plugins = Join-Path $Root "$server\plugins"
  New-Item -ItemType Directory -Force $plugins | Out-Null

  Copy-Item (Join-Path $Root 'source\MaceCupCore\build\libs\MaceCupCore.jar') (Join-Path $plugins 'MaceCupCore.jar') -Force
  Get-ChildItem (Join-Path $Root 'shared\dependencies') -Filter *.jar |
    Where-Object { $_.Name -notlike 'worldedit-*.jar' -and $_.Name -notlike '*velocity*' } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $plugins $_.Name) -Force }

  $packDir = Join-Path $plugins 'MaceCupCore\resource-pack'
  New-Item -ItemType Directory -Force $packDir | Out-Null
  Copy-Item (Join-Path $Root 'shared\resource-pack\MaceCupResourcePack.zip') (Join-Path $packDir 'MaceCupResourcePack.zip') -Force

  foreach ($world in @('world','event_world')) {
    $datapacks = Join-Path $Root "$server\$world\datapacks"
    if (Test-Path (Split-Path -Parent $datapacks)) {
      New-Item -ItemType Directory -Force $datapacks | Out-Null
      Copy-Item (Join-Path $Root 'shared\datapack\MaceCupDatapack.zip') (Join-Path $datapacks 'MaceCupDatapack.zip') -Force
    }
  }

  $props = Join-Path $Root "$server\server.properties"
  Set-Line $props '(?m)^resource-pack=.*$' 'resource-pack='
  Set-Line $props '(?m)^resource-pack-sha1=.*$' "resource-pack-sha1=$Sha"
  Set-Line $props '(?m)^require-resource-pack=.*$' 'require-resource-pack=false'
  Set-Line $props '(?m)^enforce-secure-profile=.*$' 'enforce-secure-profile=false'
  Set-Line $props '(?m)^management-server-enabled=.*$' 'management-server-enabled=false'
  Set-Line $props '(?m)^broadcast-console-to-ops=.*$' 'broadcast-console-to-ops=false'
  Set-Line $props '(?m)^broadcast-rcon-to-ops=.*$' 'broadcast-rcon-to-ops=false'
  Set-Line $props '(?m)^view-distance=.*$' 'view-distance=10'
  Set-Line $props '(?m)^simulation-distance=.*$' 'simulation-distance=6'
  Set-Line $props '(?m)^network-compression-threshold=.*$' 'network-compression-threshold=-1'
  Set-Line $props '(?m)^entity-broadcast-range-percentage=.*$' 'entity-broadcast-range-percentage=75'

  $paperGlobalConfig = Join-Path $Root "$server\config\paper-global.yml"
  if (Test-Path $paperGlobalConfig) {
    Set-Line $paperGlobalConfig '(?m)^    secret:.*$' "    secret: '$((Get-Content (Join-Path $Root 'velocity-na\forwarding.secret') -Raw).Trim())'"
    Set-Line $paperGlobalConfig '(?m)^    enabled: false$' '    enabled: true'
  }

  $spigot = Join-Path $Root "$server\spigot.yml"
  if (Test-Path $spigot) {
    Set-Line $spigot '(?m)^  save-user-cache-on-stop-only: false$' '  save-user-cache-on-stop-only: true'
    Set-Line $spigot '(?m)^  log-villager-deaths: true$' '  log-villager-deaths: false'
    Set-Line $spigot '(?m)^  log-named-deaths: true$' '  log-named-deaths: false'
    Set-Line $spigot '(?m)^  sample-count: 12$' '  sample-count: 5'
    Set-Line $spigot '(?m)^  log: true$' '  log: false'
    Set-Line $spigot '(?m)^      players: 128$' '      players: 48'
    Set-Line $spigot '(?m)^      animals: 96$' '      animals: 24'
    Set-Line $spigot '(?m)^      monsters: 96$' '      monsters: 24'
    Set-Line $spigot '(?m)^      misc: 96$' '      misc: 24'
    Set-Line $spigot '(?m)^      other: 64$' '      other: 24'
    Set-Line $spigot '(?m)^      animals: 8$' '      animals: 4'
    Set-Line $spigot '(?m)^      monsters: 16$' '      monsters: 8'
    Set-Line $spigot '(?m)^      misc: 4$' '      misc: 2'
    Set-Line $spigot '(?m)^      tick-inactive-villagers: true$' '      tick-inactive-villagers: false'
    Set-Line $spigot '(?m)^    item-despawn-rate: 6000$' '    item-despawn-rate: 600'
  }

  $paperWorld = Join-Path $Root "$server\config\paper-world-defaults.yml"
  if (Test-Path $paperWorld) {
    Set-Line $paperWorld '(?m)^  max-entity-collisions: 8$' '  max-entity-collisions: 2'
    Set-Line $paperWorld '(?m)^  only-players-collide: false$' '  only-players-collide: true'
    Set-Line $paperWorld '(?ms)armor-stands:\s+do-collision-entity-lookups: true\s+tick: true' "armor-stands:`r`n    do-collision-entity-lookups: false`r`n    tick: false"
    Set-Line $paperWorld '(?m)^  optimize-explosions: false$' '  optimize-explosions: true'
    Set-Line $paperWorld '(?m)^  update-pathfinding-on-block-update: true$' '  update-pathfinding-on-block-update: false'
    Set-Line $paperWorld '(?m)^  allow-non-player-entities-on-scoreboards: true$' '  allow-non-player-entities-on-scoreboards: false'
    Set-Line $paperWorld '(?m)^  disable-world-ticking-when-empty: false$' '  disable-world-ticking-when-empty: true'
    Set-Line $paperWorld '(?m)^  auto-save-interval: default$' '  auto-save-interval: 6000'
    Set-Line $paperWorld '(?m)^  max-auto-save-chunks-per-tick: 24$' '  max-auto-save-chunks-per-tick: 8'
    Set-Line $paperWorld '(?ms)alt-item-despawn-rate:\s+enabled: false\s+items:\s+cobblestone: 300' "alt-item-despawn-rate:`r`n      enabled: true`r`n      items:`r`n        cobblestone: 200`r`n        firework_rocket: 100`r`n        ender_pearl: 200`r`n        potion: 200`r`n        arrow: 100"
  }

  $bukkit = Join-Path $Root "$server\bukkit.yml"
  if (Test-Path $bukkit) {
    Set-Line $bukkit '(?m)^  query-plugins: true$' '  query-plugins: false'
    Set-Line $bukkit '(?m)^  monster-spawns: 1$' '  monster-spawns: 4'
    Set-Line $bukkit '(?m)^  water-spawns: 1$' '  water-spawns: 4'
    Set-Line $bukkit '(?m)^  ambient-spawns: 1$' '  ambient-spawns: 4'
  }

  $coreConfig = Join-Path $plugins 'MaceCupCore\config.yml'
  if (Test-Path $coreConfig) {
    Set-Line $coreConfig '(?m)^  url:.*$' "  url: ''"
    Set-Line $coreConfig '(?m)^  sha1:.*$' "  sha1: '$Sha'"
    Set-Line $coreConfig '(?m)^  password: change-me$' "  password: ''"
    if ((Get-Content $coreConfig -Raw) -notmatch '(?m)^  countdown-seconds:') {
      $text = Get-Content $coreConfig -Raw
      $text = $text -replace "(?m)^  duo-teams: 50\r?$", "  duo-teams: 50`r`n  countdown-seconds: 30"
      Set-Content -LiteralPath $coreConfig -Value $text -NoNewline
    }
    if ((Get-Content $coreConfig -Raw) -notmatch '(?m)^  border-warning-seconds:') {
      $text = Get-Content $coreConfig -Raw
      $text = $text -replace "(?m)^  border-minutes: 28\r?$", "  border-minutes: 28`r`n  border-warning-seconds: 30`r`n  final-border-size: 80"
      Set-Content -LiteralPath $coreConfig -Value $text -NoNewline
    }
    if ((Get-Content $coreConfig -Raw) -match '(?m)^  plus-pity-step:') {
      Set-Line $coreConfig '(?m)^  plus-pity-step:.*$' '  vip-pity-step: 0.50'
    } elseif ((Get-Content $coreConfig -Raw) -notmatch '(?m)^  vip-pity-step:') {
      $text = Get-Content $coreConfig -Raw
      $text = $text -replace "(?m)^  normal-pity-step: 0.25\r?$", "  normal-pity-step: 0.25`r`n  vip-pity-step: 0.50"
      Set-Content -LiteralPath $coreConfig -Value $text -NoNewline
    }
    Set-Line $coreConfig '(?m)^  vip-pity-step:.*$' '  vip-pity-step: 0.50'
    if ((Get-Content $coreConfig -Raw) -notmatch '(?m)^  timeout-millis:') {
      $text = Get-Content $coreConfig -Raw
      $text = $text -replace "(?m)^  port: 6379\r?$", "  port: 6379`r`n  timeout-millis: 1000"
      Set-Content -LiteralPath $coreConfig -Value $text -NoNewline
    }
    if ((Get-Content $coreConfig -Raw) -notmatch '(?m)^  max-length:') {
      $text = Get-Content $coreConfig -Raw
      $text = $text -replace "(?m)^  cooldown-seconds: 8\r?$", "  cooldown-seconds: 8`r`n  max-length: 96"
      Set-Content -LiteralPath $coreConfig -Value $text -NoNewline
    }
    if ((Get-Content $coreConfig -Raw) -notmatch '(?m)^performance:\s*$') {
      Add-Content -LiteralPath $coreConfig -Value @'
performance:
  enabled: true
  monitor-interval-ticks: 100
  dynamic-view-distance:
    enabled: true
    high:
      view: 10
      simulation: 6
    medium:
      view: 8
      simulation: 5
    low:
      view: 6
      simulation: 4
    critical:
      view: 4
      simulation: 3
  item-cleanup:
    enabled: true
    max-items-per-world: 80
    skip-empty-worlds: true
  world-defaults:
    enabled: true
    do-mob-spawning: false
    do-fire-tick: false
    random-tick-speed: 0
  memory-trim-every-runs: 0
'@
    }
  }
}

foreach ($proxy in $Proxies) {
  $plugins = Join-Path $Root "$proxy\plugins"
  New-Item -ItemType Directory -Force $plugins | Out-Null

  Copy-Item (Join-Path $Root 'source\MaceCupProxyBridge\build\libs\MaceCupProxyBridge.jar') (Join-Path $plugins 'MaceCupProxyBridge.jar') -Force
  Copy-Item (Join-Path $Root 'shared\dependencies\luckperms-v5.5.53-velocity-velocity.jar') (Join-Path $plugins 'luckperms-v5.5.53-velocity-velocity.jar') -Force
  Copy-Item (Join-Path $Root 'shared\dependencies\TCPShield-2.8.1.jar') (Join-Path $plugins 'TCPShield-2.8.1.jar') -Force

  $packDir = Join-Path $plugins 'MaceCupProxyBridge\resource-pack'
  New-Item -ItemType Directory -Force $packDir | Out-Null
  Copy-Item (Join-Path $Root 'shared\resource-pack\MaceCupResourcePack.zip') (Join-Path $packDir 'MaceCupResourcePack.zip') -Force
  Set-Content -LiteralPath (Join-Path $packDir 'SHA1.txt') -Value $Sha -NoNewline

  $proxyConfig = Join-Path $plugins 'MaceCupProxyBridge\config.yml'
  if (Test-Path $proxyConfig) {
    Set-Line $proxyConfig '(?m)^  enabled:.*$' '  enabled: true'
    Set-Line $proxyConfig '(?m)^  host-enabled:.*$' '  host-enabled: true'
    Set-Line $proxyConfig '(?m)^  public-url:.*$' "  public-url: ''"
    Set-Line $proxyConfig "(?m)^  password: 'change-me'$" "  password: ''"
    if ((Get-Content $proxyConfig -Raw) -notmatch '(?m)^  geoip-routing-enabled:') {
      $text = Get-Content $proxyConfig -Raw
      $text = $text -replace "(?m)^proxies:\r?$", "proxies:`r`n  geoip-routing-enabled: false"
      Set-Content -LiteralPath $proxyConfig -Value $text -NoNewline
    }
    if ($proxy -eq 'velocity-na') { Set-Line $proxyConfig '(?m)^  port:.*$' '  port: 24454' }
    if ($proxy -eq 'velocity-eu') { Set-Line $proxyConfig '(?m)^  port:.*$' '  port: 24454' }
  }
}

Copy-Item (Join-Path $Root 'shared\resource-pack\MaceCupResourcePack.zip') (Join-Path $Root 'source\MaceCupCore\build\resources\main\MaceCupResourcePack.zip') -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $Root 'shared\resource-pack\MaceCupResourcePack.zip') (Join-Path $Root 'source\MaceCupProxyBridge\build\resources\main\MaceCupResourcePack.zip') -Force -ErrorAction SilentlyContinue
Write-Host "Network setup synced. Resource pack SHA1: $Sha"
