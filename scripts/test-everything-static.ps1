$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path '.').Path
$failures = New-Object System.Collections.Generic.List[string]
function Pass($m){ Write-Host "PASS $m" }
function Fail($m){ Write-Host "FAIL $m"; $script:failures.Add($m) }
function Assert($condition, $message){ if($condition){ Pass $message } else { Fail $message } }
function NeedFile($rel){ Assert (Test-Path (Join-Path $Root $rel)) $rel }
function VisibleConfig($text){ (($text -split "`r?`n") | Where-Object { $_ -notmatch '^\s*#' }) -join "`n" }

$required = @(
 'README_SETUP.md','TROUBLESHOOTING.md','settings.gradle','build.gradle','gradle.properties','docker-compose.yml',
 'shared/resource-pack/MaceCupResourcePack.zip','shared/resource-pack/SHA1.txt','shared/datapack/MaceCupDatapack.zip',
 'shared/mysql/schema.sql','shared/redis/redis-example.conf','velocity-na/velocity.jar','velocity-eu/velocity.jar',
 'lobby-practice/paper-1.21.11.jar','event-1/paper-1.21.11.jar',
 'source/MaceCupCore/src/main/resources/plugin.yml','source/MaceCupCore/src/main/java/com/macecup/core/MaceCupCore.java',
 'source/MaceCupProxyBridge/src/main/java/com/macecup/proxy/MaceCupProxyBridge.java'
)
$required | ForEach-Object { NeedFile $_ }

$sha = (Get-FileHash 'shared/resource-pack/MaceCupResourcePack.zip' -Algorithm SHA1).Hash.ToLowerInvariant()
$shaExpected = (Get-Content 'shared/resource-pack/SHA1.txt' -Raw).Trim()
Assert ($sha -eq $shaExpected) "resource pack SHA1 matches SHA1.txt"
foreach($cfg in Get-ChildItem -Recurse -Filter config.yml | Where-Object { $_.FullName -like '*MaceCupCore*' }){
  $text = Get-Content $cfg.FullName -Raw
  Assert ($text -match [regex]::Escape($sha)) "resource SHA1 present in $($cfg.FullName.Substring($Root.Length+1))"
}

$tmp = Join-Path $env:TEMP ('macecup-validate-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force $tmp | Out-Null
try {
  Expand-Archive 'shared/resource-pack/MaceCupResourcePack.zip' -DestinationPath "$tmp/resource" -Force
  Expand-Archive 'shared/datapack/MaceCupDatapack.zip' -DestinationPath "$tmp/data" -Force
  Get-Content "$tmp/resource/pack.mcmeta" -Raw | ConvertFrom-Json | Out-Null
  Get-Content "$tmp/data/pack.mcmeta" -Raw | ConvertFrom-Json | Out-Null
  Get-Content "$tmp/resource/assets/minecraft/font/default.json" -Raw | ConvertFrom-Json | Out-Null
  Get-ChildItem "$tmp/resource/assets/macecup/models/item" -Filter *.json | ForEach-Object { Get-Content $_.FullName -Raw | ConvertFrom-Json | Out-Null }
  if (Test-Path "$tmp/data/data/macecup/advancement") {
    Get-ChildItem "$tmp/data/data/macecup/advancement" -Filter *.json | ForEach-Object { Get-Content $_.FullName -Raw | ConvertFrom-Json | Out-Null }
  }
  Assert $true 'resource pack and datapack JSON parse'
  Assert ((Get-ChildItem "$tmp/resource/assets/macecup/models/item" -Filter *.json).Count -ge 20) 'resource pack has custom item models'
  Assert ((Get-ChildItem "$tmp/resource/assets/macecup/textures/font" -Filter *.png).Count -ge 14) 'resource pack has scoreboard font icons'
  $advancementCount = if (Test-Path "$tmp/data/data/macecup/advancement") { (Get-ChildItem "$tmp/data/data/macecup/advancement" -Filter *.json).Count } else { 0 }
  Assert ($advancementCount -eq 0) 'datapack has no custom advancements'
} finally { Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue }

$secretNA = (Get-Content 'velocity-na/forwarding.secret' -Raw).Trim()
$secretEU = (Get-Content 'velocity-eu/forwarding.secret' -Raw).Trim()
Assert ($secretNA.Length -ge 32 -and $secretNA -eq $secretEU) 'Velocity forwarding secret is strong and shared'
$backendMotd = 'motd=\u00A78\u00A7lMaceCup \u00A77| \u00A7b\u00A7lMace PvP\n\u00A77IP\: \u00A7bmacecup.xyz \u00A78» \u00A7fMacePvP'
$velocityMotd = 'motd = "<dark_gray><bold>MaceCup</bold> <gray>| <aqua><bold>Mace PvP</bold>\n<gray>IP: <aqua>macecup.xyz <dark_gray>» <white>MacePvP"'
foreach($s in 'lobby-practice','event-1'){
  $props = Get-Content "$s/server.properties" -Raw
  Assert ($props -match '(?m)^online-mode=false\r?$') "$s backend online-mode=false"
  Assert ($props -match '(?m)^view-distance=10\r?$') "$s view-distance=10"
  Assert ($props -match '(?m)^simulation-distance=6\r?$') "$s simulation-distance=6"
  Assert ($props -match '(?m)^spawn-protection=0\r?$') "$s spawn-protection=0"
  Assert ($props -match '(?m)^server-port=25565\r?$') "$s server-port=25565"
  Assert ($props -match '(?m)^motd=(\\u00A7|§)8(\\u00A7|§)lMaceCup\s+(\\u00A7|§)7\|\s+(\\u00A7|§)b(\\u00A7|§)lMace\s+PvP\\n(\\u00A7|§)7IP\\:\s+(\\u00A7|§)bmacecup\.xyz\s+(\\u00A7|§)8»\s+(\\u00A7|§)fMacePvP\r?$') "$s backend MOTD configured"
  Assert ($props -match '(?m)^enable-command-block=false\r?$') "$s command blocks disabled"
  if($s -eq 'lobby-practice'){ Assert ($props -match '(?m)^level-name=world\r?$') "$s level-name=world" } else { Assert ($props -match '(?m)^level-name=event_world\r?$') "$s level-name=event_world" }
  $paper = Get-Content "$s/paper-global.yml" -Raw
  Assert ($paper -match 'velocity:' -and $paper -match 'enabled: true' -and $paper -match [regex]::Escape($secretNA)) "$s Paper Velocity forwarding configured"
  $spigot = Get-Content "$s/spigot.yml" -Raw
  Assert ($spigot -match "(?ms)^advancements:\s+disable-saving: true\s+disabled:\s+- '\*'" -and $spigot -match "(?ms)^stats:\s+disable-saving: true") "$s advancements and legacy achievements disabled"
  Assert ($spigot -match '(?m)^      players: 48\r?$') "$s spigot players tracking range optimized"
  Assert ($spigot -match '(?m)^    item-despawn-rate: 600\r?$') "$s spigot item despawn rate optimized"
  $paperWorldText = Get-Content "$s/config/paper-world-defaults.yml" -Raw
  Assert ($paperWorldText -match '(?m)^  optimize-explosions: true\r?$') "$s paper optimize-explosions enabled"
  Assert ($paperWorldText -match '(?m)^  only-players-collide: true\r?$') "$s paper only-players-collide enabled"
  NeedFile "$s/world/datapacks/MaceCupDatapack.zip"
}
NeedFile 'event-1/event_world/datapacks/MaceCupDatapack.zip'

foreach($v in 'velocity-na','velocity-eu'){
  $toml = Get-Content "$v/velocity.toml" -Raw
  Assert ($toml -match '(?m)^bind = "0\.0\.0\.0:25565"\r?$') "$v binds to 25565"
  Assert ($toml -match ('(?m)^' + [regex]::Escape($velocityMotd) + '\r?$')) "$v Velocity MOTD configured"
  Assert ($toml -match 'online-mode = true') "$v online-mode=true"
  Assert ($toml -match 'player-info-forwarding-mode = "modern"') "$v modern forwarding"
  foreach($server in 'lobby-practice','event-1'){
    Assert ($toml -match "${server} = `"${server}:25565`"") "$v registers $server on 25565"
  }
  NeedFile "$v/plugins/MaceCupProxyBridge.jar"
  Assert ((Get-ChildItem "$v/plugins" -Filter '*luckperms*velocity*.jar').Count -ge 1) "$v has LuckPerms Velocity"
}

foreach($s in 'lobby-practice','event-1'){
  NeedFile "$s/plugins/MaceCupCore.jar"
  NeedFile "$s/plugins/MaceCupCore/resource-pack/MaceCupResourcePack.zip"
  $runtimePackSha = (Get-FileHash "$s/plugins/MaceCupCore/resource-pack/MaceCupResourcePack.zip" -Algorithm SHA1).Hash.ToLowerInvariant()
  Assert ($runtimePackSha -eq $shaExpected) "$s exported resource pack SHA1 matches"
  $propsForPack = Get-Content "$s/server.properties" -Raw
  Assert ($propsForPack -match '(?m)^resource-pack=\r?$') "$s backend resource-pack URL is delegated to Velocity"
  Assert ($propsForPack -match "(?m)^resource-pack-sha1=$shaExpected\r?$") "$s server.properties resource-pack SHA1 configured"
  Assert ($propsForPack -match '(?m)^resource-pack-id=bafd3464-423c-4e03-930b-0e5ba2d83594\r?$') "$s server.properties resource-pack ID configured"
  Assert ($propsForPack -match '(?m)^require-resource-pack=false\r?$') "$s backend does not duplicate Velocity resource-pack prompt"
  foreach($dep in 'luckperms','placeholderapi','tab','vault','FastAsyncWorldEdit','worldguard','Citizens','decentholograms'){
    Assert ((Get-ChildItem "$s/plugins" -Filter "*$dep*.jar" -ErrorAction SilentlyContinue).Count -ge 1) "$s has $dep dependency"
  }
  Assert ((Get-ChildItem "$s/plugins" -Filter 'worldedit-*.jar' -ErrorAction SilentlyContinue).Count -eq 0) "$s avoids duplicate standalone WorldEdit with FAWE"
}

foreach($v in 'velocity-na','velocity-eu'){
  $proxyCfg = Get-Content "$v/plugins/MaceCupProxyBridge/config.yml" -Raw
  Assert ($proxyCfg -match '(?m)^resource-pack:\r?$') "$v proxy resource-pack config exists"
  Assert ($proxyCfg -match '(?m)^  host-enabled: true\r?$') "$v hosts the bundled resource pack"
  Assert ($proxyCfg -match '(?m)^  public-url: ''''\r?$') "$v derives resource-pack URL from the player virtual host"
}

$glyphLogo = [char]0xE000
$glyphEventLogo = [char]0xE001
$glyphPlayer = [char]0xE002
$glyphOnline = [char]0xE003
$glyphPing = [char]0xE004
$glyphRank = [char]0xE005
$glyphWins = [char]0xE006
$glyphKills = [char]0xE007
$glyphRating = [char]0xE008
$glyphEvent = [char]0xE009
$glyphBorder = [char]0xE00A
$retiredEventServerPattern = 'event-[23]'
$lobbyTab = Get-Content 'lobby-practice/plugins/TAB/config.yml' -Raw -Encoding UTF8
$lobbyTabVisible = VisibleConfig $lobbyTab
Assert ($lobbyTabVisible -notmatch 'macecup\.xyz|myserver\.net|www\.domain\.com|Datapack:|Arena:|Pack:|Rank:|Wins:|Kills:|Rating:|Online:|Event:|Ping:|Website:|Use /macecup') 'lobby TAB visible labels are removed'
Assert ($lobbyTabVisible -notmatch '&[0-9a-fk-or][^\r\n]*%(macecup_|online|ping|player|group|luckperms)') 'lobby TAB display strings are icon-only'
Assert ($lobbyTab -notmatch 'Test header|Test footer|Used memory|Teamspeak|Store|spyserver|lobby1|server1|%animation:Welcome%|%animation:web%') 'lobby TAB config has no stock TAB placeholders'
Assert ($lobbyTab.Contains($glyphLogo) -and $lobbyTab.Contains($glyphPlayer) -and $lobbyTab.Contains($glyphOnline) -and $lobbyTab.Contains($glyphPing) -and $lobbyTab.Contains($glyphWins) -and $lobbyTab.Contains($glyphKills) -and $lobbyTab.Contains($glyphRating) -and $lobbyTab.Contains($glyphEvent) -and $lobbyTab -match 'network:\s+[\s\S]*lobby-practice[\s\S]*event-1' -and $lobbyTab -notmatch $retiredEventServerPattern) 'lobby TAB uses icon glyphs and single event network playerlist'
Assert ($lobbyTab -match 'GROUPS:owner,admin,host,vipplus,vip,default' -and $lobbyTab -notmatch 'macecupchampion|macecupplus|GROUPS:.*staff' -and (Get-Content 'lobby-practice/plugins/TAB/groups.yml' -Raw -Encoding UTF8) -match 'vipplus:' -and (Get-Content 'lobby-practice/plugins/TAB/groups.yml' -Raw -Encoding UTF8) -match 'vip:') 'lobby TAB uses owner/admin/host/vipplus/vip groups'
foreach($s in 'event-1'){
  $tab = Get-Content "$s/plugins/TAB/config.yml" -Raw -Encoding UTF8
  $tabVisible = VisibleConfig $tab
  Assert ($tabVisible -notmatch 'macecup\.xyz|myserver\.net|www\.domain\.com|Datapack:|Arena:|Alive:|Rating:|Highest Slam:|Pack:|Kills:|Ping:|Fight inside|Website:|Rank:|Online:|Border:|Mode:|Stay inside') "$s TAB visible labels are removed"
  Assert ($tabVisible -notmatch '&[0-9a-fk-or][^\r\n]*%(macecup_|online|ping|player|group|luckperms)') "$s TAB display strings are icon-only"
  Assert ($tab -notmatch 'Test header|Test footer|Used memory|Teamspeak|Store|spyserver|lobby1|server1|%animation:Welcome%|%animation:web%') "$s TAB config has no stock TAB placeholders"
  Assert ($tab.Contains($glyphEventLogo) -and $tab.Contains($glyphPlayer) -and $tab.Contains($glyphOnline) -and $tab.Contains($glyphPing) -and $tab.Contains($glyphKills) -and $tab.Contains($glyphBorder)) "$s TAB config has icon-only event header, footer, and scoreboard"
  Assert ($tab -match 'GROUPS:owner,admin,host,vipplus,vip,default' -and $tab -notmatch 'macecupchampion|macecupplus|GROUPS:.*staff' -and (Get-Content "$s/plugins/TAB/groups.yml" -Raw -Encoding UTF8) -match 'vipplus:' -and (Get-Content "$s/plugins/TAB/groups.yml" -Raw -Encoding UTF8) -match 'vip:') "$s TAB uses owner/admin/host/vipplus/vip groups"
  Assert (($tab | Select-String -Pattern ([regex]::Escape($glyphKills) + "'?`r?`n") -AllMatches).Matches.Count -ge 3) "$s TAB scoreboard/belowname/footer use kill icon lines"
  $animations = Get-Content "$s/plugins/TAB/animations.yml" -Raw -Encoding UTF8
  Assert ($animations -notmatch 'www\.domain\.com|vote\.domain\.com|ServerName|Welcome:|MaceCup') "$s TAB animations are icon-only"
}
$lobbyAnimations = Get-Content 'lobby-practice/plugins/TAB/animations.yml' -Raw -Encoding UTF8
Assert ($lobbyAnimations -notmatch 'www\.domain\.com|vote\.domain\.com|ServerName|Welcome:|MaceCup') 'lobby TAB animations are icon-only'

$pluginYml = Get-Content 'source/MaceCupCore/src/main/resources/plugin.yml' -Raw
Assert ($pluginYml -match 'main: com.macecup.core.MaceCupCore') 'plugin.yml main class correct'
Assert ($pluginYml -match 'api-version: ''1.21''') 'plugin.yml api-version set'
Assert ($pluginYml -match 'mace:') 'plugin.yml mace command exists'
$sourceText = (Get-ChildItem source/MaceCupCore/src/main/java -Recurse -Filter *.java | ForEach-Object { Get-Content $_.FullName -Raw }) -join "`n"
Assert ($sourceText -notmatch '/mace join|join solo|join duo|queue') 'source has no public queue/join event commands'
Assert ($sourceText -match 'InventoryClickEvent' -and $sourceText -match 'InventoryDragEvent' -and $sourceText -match 'PlayerDropItemEvent' -and $sourceText -match 'PlayerSwapHandItemsEvent') 'compass abuse listeners present'
Assert ($sourceText -match 'PlaceholderExpansion') 'PlaceholderAPI expansion present'
Assert ($sourceText -match 'PlayerResourcePackStatusEvent') 'resource pack status tracking present'
Assert ($sourceText -match 'map-built') 'one-time map generation marker present'
Assert ($sourceText -match 'CosmeticService' -and $sourceText -match 'CosmeticCategory') 'cosmetic catalog and selection service present'
Assert ($sourceText -match 'CustomEmoteService' -and $sourceText -match 'customemote') 'custom emote service and command present'
Assert ($sourceText -match 'STANDARD_EMOTES' -and $sourceText -match 'SELFTEST ALL PASSED') 'standard emotes and selftest command present'
Assert ($sourceText -match 'sendTitle' -and $sourceText -match 'sendActionBar' -and $sourceText -match 'spawnWinnerFirework' -and $sourceText -match 'setWarningDistance' -and $sourceText -match 'BarColor.RED') 'event countdown, actionbar chance, red border, and winner fireworks present'
$lpCommands = Get-Content 'shared/docs/LuckPerms/setup-commands.txt' -Raw
Assert ($lpCommands -match 'creategroup vip' -and $lpCommands -match 'creategroup vipplus' -and $lpCommands -match 'macecup\.purge\.vip' -and $lpCommands -match 'macecup\.purge\.guaranteed' -and $lpCommands -notmatch 'macecupplus|macecupchampion') 'LuckPerms commands use vip/vipplus paid ranks'

Assert ((Get-ChildItem 'lobby-practice/world/region' -Filter *.mca -ErrorAction SilentlyContinue).Count -gt 0) 'lobby map region files exist'
foreach($s in 'event-1'){
  Assert ((Get-ChildItem "$s/event_world/region" -Filter *.mca -ErrorAction SilentlyContinue).Count -gt 0) "$s event map region files exist"
  Assert ((Select-String -Path "$s/plugins/MaceCupCore/config.yml" -Pattern '^    - ''?-?\d').Count -eq 100) "$s has 100 arena spawns"
  $eventCfg = Get-Content "$s/plugins/MaceCupCore/config.yml" -Raw
  $eventProps = Get-Content "$s/server.properties" -Raw
  Assert ($eventCfg -match '(?m)^    border-size: 1250\r?$') "$s border-size=1250"
  Assert ($eventCfg -match '(?m)^    width: 1250\r?$' -and $eventCfg -match '(?m)^    depth: 1250\r?$') "$s arena footprint is 1250x1250"
  Assert ($eventCfg -match '(?m)^  event-width: 1250\r?$' -and $eventCfg -match '(?m)^  event-depth: 1250\r?$') "$s map footprint config is 1250x1250"
  if($s -eq 'event-1'){
    Assert ($eventProps -match '(?m)^level-seed=macecup-crystal-rift-1250x1250\r?$' -and $eventCfg -match '(?m)^  event-profile: crystal-rift\r?$') "$s uses crystal-rift seed/profile"
  }
  Assert ((Get-ChildItem "$s/plugins/MaceCupCore" -Filter 'map-built-event-v2.marker').Count -eq 1) "$s has v2 map generation marker"
}
Assert ((Get-ChildItem 'lobby-practice/plugins/MaceCupCore' -Filter 'map-built-lobby_practice-v2.marker').Count -eq 1) 'lobby v2 map generation marker exists'

if($failures.Count -gt 0){ Write-Host "FAILED $($failures.Count) checks"; $failures | ForEach-Object { Write-Host " - $_" }; exit 1 }
Write-Host 'ALL STATIC CHECKS PASSED'

