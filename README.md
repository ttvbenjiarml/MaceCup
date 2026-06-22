# MaceCup Network

Production-oriented Minecraft Paper and Velocity network workspace for `macecup.xyz`.

This repository contains the complete local network layout for MaceCup: custom Paper plugin source, custom Velocity plugin source, generated resource pack and datapack projects, prepared runtime server folders, shared dependencies, database schema, network sync scripts, static validation scripts, local smoke-test helpers, a Next.js public website, and a local MCP control sidecar.

The project is built around hosted competitive Minecraft mace events. Players join through Velocity, land in the `lobby-practice` Paper backend, receive the official resource pack, use lobby tools and cosmetics, and can be selected into hosted event rounds on `event-1`. Event state is coordinated through Redis where available, player stats are persisted locally and optionally into MySQL, and the website reads public status, leaderboard, and player data through the Velocity-side HTTP API.

## Table of Contents

- [Project Purpose](#project-purpose)
- [Current Workspace Snapshot](#current-workspace-snapshot)
- [High-Level Architecture](#high-level-architecture)
- [Repository Layout](#repository-layout)
- [Runtime Components](#runtime-components)
- [Server Folders](#server-folders)
- [Source Projects](#source-projects)
- [Shared Assets](#shared-assets)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Build Workflow](#build-workflow)
- [Local Database Services](#local-database-services)
- [Server Jar Download](#server-jar-download)
- [Dependency Download](#dependency-download)
- [Pack Packaging](#pack-packaging)
- [Network Sync](#network-sync)
- [Validation and Testing](#validation-and-testing)
- [AMP Deployment](#amp-deployment)
- [Ports](#ports)
- [Velocity Proxy Configuration](#velocity-proxy-configuration)
- [Paper Backend Configuration](#paper-backend-configuration)
- [MaceCupCore Paper Plugin](#macecupcore-paper-plugin)
- [MaceCupProxyBridge Velocity Plugin](#macecupproxybridge-velocity-plugin)
- [Resource Pack](#resource-pack)
- [Datapack](#datapack)
- [Database Schema](#database-schema)
- [Redis Keys and Event Flow](#redis-keys-and-event-flow)
- [Commands](#commands)
- [Permissions](#permissions)
- [LuckPerms Groups](#luckperms-groups)
- [Gameplay Flow](#gameplay-flow)
- [Arena Management](#arena-management)
- [Cosmetics and Emotes](#cosmetics-and-emotes)
- [Stats and Leaderboards](#stats-and-leaderboards)
- [PlaceholderAPI](#placeholderapi)
- [TAB and Scoreboards](#tab-and-scoreboards)
- [Website](#website)
- [MCP Control Sidecar](#mcp-control-sidecar)
- [Scripts Reference](#scripts-reference)
- [Troubleshooting](#troubleshooting)
- [Known Current-State Notes](#known-current-state-notes)
- [Operational Checklist](#operational-checklist)

## Project Purpose

MaceCup is a competitive Minecraft network focused on mace PvP and hosted cup-style events.

The intended live setup is:

- Public players connect only to Velocity.
- Velocity authenticates users in online mode.
- Velocity forwards players to private Paper backends using modern forwarding.
- `lobby-practice` is the main player hub and practice backend.
- `event-1` is a protected backend used for hosted solo, duo, and cash cup events.
- `MaceCupCore` runs on Paper backends.
- `MaceCupProxyBridge` runs on Velocity proxies.
- MySQL stores durable public player data for the website.
- Redis coordinates live server and event state between backends.
- The resource pack is served by Velocity so players get one canonical pack prompt.
- Backend resource-pack URLs remain blank to avoid duplicate stale prompts.

## Current Workspace Snapshot

Root folder:

```text
E:\Game Work\MaceCup-Network
```

Current major folders:

```text
.gradle/
.tools/
.vscode/
build/
event-1/
gradle/
lobby-practice/
scripts/
shared/
source/
tools/
velocity-eu/
velocity-na/
website/
```

Current major root files:

```text
.gitignore
build.gradle
docker-compose.yml
gradle.properties
gradlew
gradlew.bat
README.md
README_SETUP.md
settings.gradle
TROUBLESHOOTING.md
```

There was no root `README.md` before this file was added. Existing setup material remains in:

- `README_SETUP.md`
- `TROUBLESHOOTING.md`
- `shared/docs/*.md`
- `shared/resource-pack/README_RESOURCEPACK.md`
- `shared/datapack/README_DATAPACK.md`
- `tools/macecup-mcp/README.md`
- `website/README.md`

## High-Level Architecture

```text
Players
  |
  v
Velocity proxy: velocity-na or velocity-eu
  - online-mode=true
  - modern forwarding
  - MaceCupProxyBridge
  - LuckPerms Velocity
  - resource-pack HTTP host
  - public API routes on pack HTTP server
  |
  +--> Paper backend: lobby-practice
  |      - online-mode=false
  |      - MaceCupCore
  |      - LuckPerms, PlaceholderAPI, TAB, Vault
  |      - FAWE/WorldGuard/Citizens/DecentHolograms
  |      - lobby, practice, cosmetics, hosting commands
  |
  +--> Paper backend: event-1
         - online-mode=false
         - MaceCupCore
         - protected event server
         - solo/duo/cashcup arena runtime
         - border, loot, winners, stats

MySQL
  - durable player and stat tables
  - website leaderboard/player profile data

Redis
  - live server states
  - pending event payloads
  - selected event players

Next.js website
  - reads /api/status, /api/leaderboard, /api/player from proxy API
```

## Repository Layout

### `source/`

Source assets and Java projects.

```text
source/
  MaceCupCore/
    build.gradle
    src/main/java/com/macecup/core/...
    src/main/resources/plugin.yml
    src/main/resources/config.yml
  MaceCupProxyBridge/
    build.gradle
    src/main/java/com/macecup/proxy/...
    src/main/resources/velocity-plugin.json
  MaceCupResourcePack/
    pack.mcmeta
    assets/macecup/...
    assets/minecraft/font/default.json
  MaceCupDatapack/
    pack.mcmeta
    data/macecup/function/load.mcfunction
    data/minecraft/tags/function/load.json
```

### `shared/`

Shared generated packages, dependencies, database files, Redis config, and documentation.

```text
shared/
  datapack/
    MaceCupDatapack.zip
    README_DATAPACK.md
  dependencies/
    Citizens-2.0.43-b4210.jar
    FastAsyncWorldEdit-2.15.2.jar
    luckperms-v5.5.53-bukkit.jar
    luckperms-v5.5.53-velocity-velocity.jar
    placeholderapi-2.12.2.jar
    tab-was-taken-6.1.0.jar
    TCPShield-2.8.1.jar
    vaultunlocked-2.20.1.jar
    worldedit-7.4.4-beta-01.jar
    worldguard-7.0.17.jar
    decentholograms-2.10.0.jar
  docs/
    ADMIN_GUIDE.md
    ARENA_NBT_GUIDE.md
    BOOT_TEST_REPORT.md
    DEPENDENCIES_INSTALLED.md
    DEPENDENCY_DOWNLOADS.txt
    LUCKPERMS_GROUPS.md
    MAP_REPORT.md
    PLAYER_GUIDE.md
    TAB_CONFIG.md
    TESTING_CHECKLIST.md
    WORLDGUARD_REGIONS.md
  mysql/
    schema.sql
  redis/
    redis-example.conf
  resource-pack/
    MaceCupResourcePack.zip
    SHA1.txt
    README_RESOURCEPACK.md
```

### `lobby-practice/`

Prepared Paper backend for lobby and practice.

Important files:

```text
lobby-practice/server.properties
lobby-practice/paper-global.yml
lobby-practice/paper-world-defaults.yml
lobby-practice/config/paper-global.yml
lobby-practice/config/paper-world-defaults.yml
lobby-practice/plugins/MaceCupCore/config.yml
lobby-practice/plugins/MaceCupCore/stats.yml
lobby-practice/plugins/MaceCupCore/cosmetics.yml
lobby-practice/plugins/MaceCupCore/custom-emotes.yml
lobby-practice/plugins/TAB/config.yml
lobby-practice/plugins/TAB/groups.yml
lobby-practice/plugins/WorldGuard/config.yml
lobby-practice/world/
lobby-practice/world_nether/
```

### `event-1/`

Prepared Paper backend for hosted events.

Important files:

```text
event-1/server.properties
event-1/paper-global.yml
event-1/paper-world-defaults.yml
event-1/config/paper-global.yml
event-1/config/paper-world-defaults.yml
event-1/plugins/MaceCupCore/config.yml
event-1/plugins/MaceCupCore/stats.yml
event-1/plugins/TAB/config.yml
event-1/plugins/TAB/groups.yml
event-1/plugins/WorldGuard/config.yml
event-1/event_world_nether/
event-1/world_nether/
```

### `velocity-na/` and `velocity-eu/`

Prepared Velocity proxy folders.

Important files:

```text
velocity-na/velocity.toml
velocity-na/forwarding.secret
velocity-na/plugins/MaceCupProxyBridge/config.yml
velocity-na/plugins/MaceCupProxyBridge/resource-pack/MaceCupResourcePack.zip
velocity-na/plugins/luckperms/config.yml

velocity-eu/velocity.toml
velocity-eu/forwarding.secret
velocity-eu/plugins/MaceCupProxyBridge/config.yml
velocity-eu/plugins/MaceCupProxyBridge/resource-pack/MaceCupResourcePack.zip
velocity-eu/plugins/luckperms/config.yml
```

### `scripts/`

Local build, packaging, sync, validation, and smoke-test helpers.

```text
apply-luckperms-setup.ps1
apply-resource-pack-url.ps1
download-dependencies.ps1
download-server-jars.ps1
full-network-smoke.ps1
mc-ping.mjs
package-packs.ps1
paper-selftest.ps1
paper-smoke.ps1
smoke-start.ps1
start-paper-optimized.ps1
start-velocity-optimized.ps1
sync-network-setup.ps1
test-everything-static.ps1
validate-network.mjs
velocity-smoke.ps1
```

### `website/`

Next.js app for public MaceCup web pages.

Important files:

```text
website/package.json
website/next.config.mjs
website/src/app/page.js
website/src/app/player/[query]/page.js
website/src/app/api/status/route.js
website/src/app/api/leaderboard/route.js
website/src/app/api/player/[query]/route.js
website/src/app/privacy/page.js
website/src/app/terms/page.js
website/src/app/globals.css
```

### `tools/macecup-mcp/`

Local MCP control server for development.

```text
tools/macecup-mcp/server.mjs
tools/macecup-mcp/package.json
tools/macecup-mcp/README.md
```

## Runtime Components

### Backends

- `lobby-practice`: Paper backend for lobby, practice, cosmetics, event hosting, leaderboards, and player-facing network status.
- `event-1`: Paper backend for protected hosted event execution.

### Proxies

- `velocity-na`: NA Velocity proxy.
- `velocity-eu`: EU Velocity proxy.

Both current proxy configs bind to `0.0.0.0:25565`. That works only if each proxy has its own host, container, IP, or AMP instance binding context.

### Shared Services

- MySQL 8.4 through `docker-compose.yml` or a managed/private MySQL service.
- Redis 7 Alpine through `docker-compose.yml` or a managed/private Redis service.

### Website

- Next.js 16 app in `website/`.
- React 19.
- Reads proxy API by `PROXY_API_URL`, defaulting to `http://127.0.0.1:24454`.

## Server Folders

This repo contains runnable server folder templates. They are intended to be copied or synchronized into AMP or another host.

The prepared runtime folders are:

```text
velocity-na/
velocity-eu/
lobby-practice/
event-1/
```

Only the server jars should be the runtime executables:

- Paper: `paper-1.21.11.jar`
- Velocity: `velocity.jar`

Do not configure AMP to run `.ps1`, `.bat`, Gradle, npm, or MCP scripts as server startup commands. Those scripts are local setup helpers.

## Source Projects

Gradle project:

```text
rootProject.name='MaceCup-Network'
include 'source:MaceCupCore','source:MaceCupProxyBridge'
```

Global Gradle values:

```text
group=com.macecup
version=1.0.0
org.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8
org.gradle.warning.mode=all
```

### `source:MaceCupCore`

Paper plugin:

- Java toolchain: 21
- Compile release: 21
- Output jar name: `MaceCupCore.jar`
- API dependency: `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT`
- Optional compile dependency: PlaceholderAPI
- Bundles `shared/datapack/MaceCupDatapack.zip`
- Bundles `shared/resource-pack/MaceCupResourcePack.zip`

### `source:MaceCupProxyBridge`

Velocity plugin:

- Java toolchain: 21
- Compile release: 21
- Shadow plugin: `com.gradleup.shadow` `8.3.0`
- Output jar name: `MaceCupProxyBridge.jar`
- Velocity API: `3.4.0-SNAPSHOT`
- Bundles MySQL Connector/J `8.4.0`
- Bundles `shared/resource-pack/MaceCupResourcePack.zip`

## Shared Assets

### Resource Pack Source

```text
source/MaceCupResourcePack/
```

Contains:

- `pack.mcmeta`
- custom item models under `assets/macecup/models/item`
- custom item textures under `assets/macecup/textures/item`
- font textures under `assets/macecup/textures/font`
- custom font mapping under `assets/minecraft/font/default.json`
- `assets/macecup/sounds.json`

Examples of custom items and textures:

- `macecup_mace`
- `heavy_slam_mace`
- `winner_crown`
- `duo_crown`
- `cash_cup_trophy`
- `leaderboard_trophy`
- `event_host_wand`
- `arena_regen_wand`
- `hat_selector`
- `emote_selector`
- `cosmetic_chest`
- `tracker_compass`
- `spectator_compass`
- `pulse_gadget`
- `loot_crate_key`

Examples of font icons:

- logo
- event logo
- player
- online
- ping
- rank
- wins
- kills
- rating
- event
- border
- coins
- level
- mode

### Datapack Source

```text
source/MaceCupDatapack/
```

Contains:

- `pack.mcmeta`
- `data/macecup/function/load.mcfunction`
- `data/minecraft/tags/function/load.json`

The generated zip is installed into configured world `datapacks/` folders.

## Requirements

Required:

- Java 21 JDK
- Gradle wrapper from this repo, or system Gradle
- Node.js for the website, Node scripts, and MCP sidecar
- npm for `website/` and `tools/macecup-mcp/`
- Docker Desktop if using local MySQL and Redis through `docker-compose.yml`
- PowerShell for the provided Windows helper scripts
- Minecraft server hosting capable of running Paper and Velocity, such as AMP

Recommended:

- Git
- A private network between Velocity and Paper backends
- Firewall rules so only Velocity is public
- Managed MySQL and Redis for production
- Separate IPs, containers, hosts, or AMP network bindings if multiple instances use port `25565`

## Quick Start

From the repo root on Windows:

```powershell
.\gradlew.bat build
.\scripts\package-packs.ps1
.\scripts\download-server-jars.ps1
.\scripts\download-dependencies.ps1
.\scripts\sync-network-setup.ps1
.\scripts\apply-luckperms-setup.ps1
node .\scripts\validate-network.mjs
.\scripts\test-everything-static.ps1
```

Start local MySQL and Redis:

```powershell
docker compose up -d
```

Start the website:

```powershell
cd website
npm install
npm run dev
```

Website default local URL:

```text
http://localhost:3000
```

## Build Workflow

Build both Java plugins:

```powershell
.\gradlew.bat build
```

Expected plugin outputs:

```text
source/MaceCupCore/build/libs/MaceCupCore.jar
source/MaceCupProxyBridge/build/libs/MaceCupProxyBridge.jar
```

After building, sync runtime folders:

```powershell
.\scripts\sync-network-setup.ps1
```

That script copies:

- `MaceCupCore.jar` into backend `plugins/`
- backend dependency jars from `shared/dependencies/`
- `MaceCupProxyBridge.jar` into proxy `plugins/`
- LuckPerms Velocity jar into proxy `plugins/`
- TCPShield jar into proxy `plugins/`
- resource pack zips and SHA1 files
- datapack zips into backend world datapack folders
- resource-pack SHA1 values into backend config/server properties
- selected Paper, Spigot, Bukkit, and plugin performance settings

## Local Database Services

`docker-compose.yml` defines local MySQL and Redis:

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: change-root
      MYSQL_DATABASE: macecup
      MYSQL_USER: macecup
      MYSQL_PASSWORD: change-me
    ports: ['127.0.0.1:3306:3306']
    volumes:
      - './shared/mysql/schema.sql:/docker-entrypoint-initdb.d/schema.sql:ro'
      - 'mysql-data:/var/lib/mysql'
  redis:
    image: redis:7-alpine
    command: ['redis-server', '/usr/local/etc/redis/redis.conf']
    ports: ['127.0.0.1:6379:6379']
    volumes:
      - './shared/redis/redis-example.conf:/usr/local/etc/redis/redis.conf:ro'
      - 'redis-data:/data'
```

Start:

```powershell
docker compose up -d
```

Stop:

```powershell
docker compose down
```

Production note:

- Keep MySQL and Redis private.
- Do not expose them to the public internet.
- Update all backend and proxy plugin configs if managed service hostnames, usernames, or passwords differ.

## Server Jar Download

Download Paper and Velocity jars:

```powershell
.\scripts\download-server-jars.ps1
```

The script downloads:

- Paper `1.21.11` to both `lobby-practice/paper-1.21.11.jar` and `event-1/paper-1.21.11.jar`
- Velocity `3.4.0-SNAPSHOT` to both `velocity-na/velocity.jar` and `velocity-eu/velocity.jar`

## Dependency Download

Download plugin dependencies:

```powershell
.\scripts\download-dependencies.ps1
```

Downloaded jars go into:

```text
shared/dependencies/
```

Current backend runtime plugin set:

- `MaceCupCore.jar`
- `luckperms-v5.5.53-bukkit.jar`
- `placeholderapi-2.12.2.jar`
- `tab-was-taken-6.1.0.jar`
- `vaultunlocked-2.20.1.jar`
- `FastAsyncWorldEdit-2.15.2.jar`
- `worldguard-7.0.17.jar`
- `Citizens-2.0.43-b4210.jar`
- `decentholograms-2.10.0.jar`

Current proxy runtime plugin set:

- `MaceCupProxyBridge.jar`
- `luckperms-v5.5.53-velocity-velocity.jar`
- `TCPShield-2.8.1.jar` if synced

Standalone WorldEdit is downloaded and kept in `shared/dependencies`, but the runtime sync avoids installing standalone WorldEdit because FAWE also registers as WorldEdit and duplicate plugin names can cause Paper startup rejection.

## Pack Packaging

Package resource pack and datapack:

```powershell
.\scripts\package-packs.ps1
```

The script:

- zips `source/MaceCupResourcePack/*` into `shared/resource-pack/MaceCupResourcePack.zip`
- zips `source/MaceCupDatapack/*` into `shared/datapack/MaceCupDatapack.zip`
- calculates SHA1 for the resource pack
- writes the SHA1 to `shared/resource-pack/SHA1.txt`
- updates `source/MaceCupCore/src/main/resources/config.yml`

Current backend configs observed in this workspace use resource-pack SHA1:

```text
a56922eb1a1a501342138d03e6654bf66800bd50
```

If you repackage the pack, rerun sync and validation so all runtime config values are updated.

## Network Sync

Primary sync:

```powershell
.\scripts\sync-network-setup.ps1
```

LuckPerms sync:

```powershell
.\scripts\apply-luckperms-setup.ps1
```

Resource-pack public URL helper:

```powershell
.\scripts\apply-resource-pack-url.ps1
```

Use the URL helper only when you want a direct hosted HTTPS zip URL instead of the Velocity-hosted derived URL.

## Validation and Testing

Static Node validation:

```powershell
node .\scripts\validate-network.mjs
```

Checks include:

- required files exist
- shared resource pack zip exists
- shared datapack zip exists
- resource-pack SHA1 matches `shared/resource-pack/SHA1.txt`
- resource pack `pack.mcmeta` parses as JSON
- datapack `pack.mcmeta` parses as JSON
- datapack advancement JSON parses if present

Large static PowerShell validation:

```powershell
.\scripts\test-everything-static.ps1
```

Checks include:

- setup docs exist
- Gradle files exist
- Docker compose exists
- server jars exist
- plugin descriptors exist
- pack/datapack zips parse
- custom item models exist
- font icons exist
- forwarding secrets are shared and strong
- backend `online-mode=false`
- backend view/simulation distances
- backend MOTD
- backend server ports
- Paper Velocity forwarding
- TAB config content
- dependency jars in runtime plugin folders
- proxy config values
- source restrictions such as no public queue/join event commands
- map markers and region files
- event arena spawn counts

Smoke helpers:

```powershell
.\scripts\paper-smoke.ps1
.\scripts\velocity-smoke.ps1
.\scripts\paper-selftest.ps1
.\scripts\smoke-start.ps1
.\scripts\full-network-smoke.ps1
```

Minecraft ping helper:

```powershell
node .\scripts\mc-ping.mjs
```

## AMP Deployment

Create one AMP instance per runtime folder:

```text
velocity-na
velocity-eu
lobby-practice
event-1
```

Use Java 21 for every instance.

Executable jars:

```text
Paper backends: paper-1.21.11.jar
Velocity proxies: velocity.jar
```

Do not point AMP at:

- `.ps1` scripts
- `.bat` scripts
- Gradle wrapper
- npm commands
- MCP sidecar

Suggested memory:

- Velocity: `512M` to `1536M`
- Lobby: `2G` to `4G`
- Event: `2G` to `6G`, depending on player count and world size

Optional JVM flags for Paper if AMP exposes additional JVM arguments:

```text
-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch
```

Optional JVM flags for Velocity:

```text
-XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+ParallelRefProcEnabled -XX:+DisableExplicitGC
```

Security model:

- Expose only Velocity to players.
- Keep Paper backends private to the same host, LAN, VPN, Docker/AMP network, or internal private IP.
- Backend `online-mode=false` is intentional because Velocity handles authentication and forwarding.
- Never let players connect directly to Paper backends.

## Ports

Current configured values:

```text
velocity-na:       0.0.0.0:25565
velocity-eu:       0.0.0.0:25565
lobby-practice:    127.0.0.1 / server-port=25565
event-1:           127.0.0.1 / server-port=25565
resource pack/API: 0.0.0.0:24454 on each Velocity plugin config
MySQL local:       127.0.0.1:3306
Redis local:       127.0.0.1:6379
Website dev:       localhost:3000
```

Important port note:

Multiple processes cannot bind the same IP and port on one machine. The current `25565` layout assumes separate IPs, containers, AMP instances with isolated networking, separate machines, or hostnames that resolve to different targets. If multiple instances share one physical host and one IP, give backends private ports and update `velocity.toml`.

## Velocity Proxy Configuration

Current `velocity.toml` essentials:

```toml
bind = "0.0.0.0:25565"
online-mode = true
force-key-authentication = true
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"

[servers]
lobby-practice = "lobby-practice:25565"
event-1 = "event-1:25565"
try = ["lobby-practice"]

[forced-hosts]
"macecup.xyz" = ["lobby-practice"]
```

Both `velocity-na` and `velocity-eu` have their own folder, language files, LuckPerms config, forwarding secret file, and MaceCupProxyBridge config.

The same generated Velocity forwarding secret is expected to be configured everywhere.

## Paper Backend Configuration

Current backend `server.properties` essentials:

```properties
online-mode=false
server-port=25565
spawn-protection=0
enable-command-block=false
view-distance=10
simulation-distance=6
network-compression-threshold=-1
resource-pack=
resource-pack-sha1=a56922eb1a1a501342138d03e6654bf66800bd50
require-resource-pack=false
```

`lobby-practice`:

```properties
level-name=world
max-players=500
```

`event-1`:

```properties
level-name=event_world
level-seed=macecup-crystal-rift-1250x1250
max-players=100
```

Backends leave the resource-pack URL blank because Velocity handles the actual required pack offer.

## MaceCupCore Paper Plugin

Plugin descriptor:

```yaml
name: MaceCupCore
version: 1.0.0
main: com.macecup.core.MaceCupCore
api-version: '1.21'
author: MaceCup
softdepend:
  - PlaceholderAPI
  - WorldGuard
  - WorldEdit
  - FastAsyncWorldEdit
  - DecentHolograms
  - HolographicDisplays
  - Citizens
  - TAB
  - Vault
  - LuckPerms
commands:
  mace:
    description: MaceCup network command
    aliases: [macecup, mcup]
```

Core responsibilities:

- load server role from config
- start player stats service
- start Redis state service
- export bundled resource pack
- install bundled datapack into configured worlds
- load configured arenas
- start event manager
- register listeners
- start performance optimizer
- register `/mace`
- register PlaceholderAPI expansion when PlaceholderAPI is present
- optionally run self-test on start

Important classes currently present:

```text
com.macecup.core.MaceCupCore
com.macecup.core.Text
com.macecup.core.PerformanceOptimizer
com.macecup.core.ServerRole
com.macecup.core.command.MaceCommand
com.macecup.core.event.EventManager
com.macecup.core.event.EventMode
com.macecup.core.event.EventState
com.macecup.core.arena.ArenaManager
com.macecup.core.arena.ArenaConfig
com.macecup.core.cosmetic.CosmeticService
com.macecup.core.cosmetic.CosmeticCategory
com.macecup.core.cosmetic.CustomEmoteService
com.macecup.core.gui.GuiManager
com.macecup.core.lobby.LobbyProtectionListener
com.macecup.core.pack.DatapackService
com.macecup.core.pack.ResourcePackService
com.macecup.core.placeholder.MaceCupPlaceholders
com.macecup.core.storage.PlayerStats
com.macecup.core.storage.RedisStateService
com.macecup.core.storage.StatsService
com.macecup.core.world.EventBoundaryListener
com.macecup.core.world.MapBuilder
```

Main backend config keys:

```yaml
server-role: LOBBY_PRACTICE
server-name: lobby-practice
network-name: MaceCup
domain: macecup.xyz
resource-pack: ...
datapack: ...
events: ...
arena-selection: ...
purge: ...
arenas: ...
loot: ...
mysql: ...
redis: ...
performance: ...
scoreboard: ...
bossbar: ...
practice: ...
cosmetics: ...
custom-emotes: ...
map: ...
self-test-on-start: false
```

Server roles:

- `LOBBY_PRACTICE`
- `EVENT`

## MaceCupProxyBridge Velocity Plugin

Plugin descriptor:

```json
{
  "id": "macecupproxybridge",
  "name": "MaceCupProxyBridge",
  "version": "1.0.0",
  "authors": ["MaceCup"],
  "dependencies": [],
  "main": "com.macecup.proxy.MaceCupProxyBridge"
}
```

Core responsibilities:

- send the MaceCup resource pack on login
- host the bundled resource pack over HTTP
- expose HTTP API routes for website data
- send new logins to `lobby-practice`
- protect event servers while active
- redirect kicks from event servers back to lobby
- provide `/maceproxy`
- optionally do simple GeoIP-based NA/EU transfer when enabled

Important classes:

```text
com.macecup.proxy.MaceCupProxyBridge
com.macecup.proxy.ResourcePackHost
com.macecup.proxy.HttpApiServer
```

Proxy config:

```yaml
region: NA
lobby-server: lobby-practice
event-servers: [event-1]
protected-event-servers: true
resource-pack:
  enabled: true
  host-enabled: true
  bind-address: 0.0.0.0
  port: 24454
  path: /resource-pack/MaceCupResourcePack.zip
  public-host: ''
  public-url: ''
  required: true
  prompt: Our server uses a custom resource pack!
mysql:
  jdbc-url: 'jdbc:mysql://127.0.0.1:3306/macecup?useSSL=false&allowPublicKeyRetrieval=true'
  username: 'macecup'
  password: ''
proxies:
  geoip-routing-enabled: false
  na-address: 'na.macecup.xyz'
  na-port: 25565
  eu-address: 'eu.macecup.xyz'
  eu-port: 25565
```

HTTP API routes registered on the resource-pack HTTP server:

```text
GET /api/status
GET /api/leaderboard?category=wins
GET /api/leaderboard?category=kills
GET /api/leaderboard?category=rating
GET /api/leaderboard?category=slam
GET /api/leaderboard?category=cashcup
GET /api/player/{usernameOrUuid}
GET /resource-pack/MaceCupResourcePack.zip
HEAD /resource-pack/MaceCupResourcePack.zip
```

## Resource Pack

The resource pack is designed to be bundled into both Java plugins:

- `MaceCupCore.jar` exports the pack into backend plugin data.
- `MaceCupProxyBridge.jar` exports and hosts the pack from Velocity plugin data.

Backend startup behavior:

- `MaceCupCore` copies bundled `MaceCupResourcePack.zip` to `plugins/MaceCupCore/resource-pack/MaceCupResourcePack.zip`.
- It calculates SHA1.
- It can track resource pack status.

Proxy startup behavior:

- `MaceCupProxyBridge` copies bundled `MaceCupResourcePack.zip` to `plugins/MaceCupProxyBridge/resource-pack/MaceCupResourcePack.zip`.
- It writes `plugins/MaceCupProxyBridge/resource-pack/SHA1.txt`.
- It hosts the pack over HTTP when `resource-pack.host-enabled: true`.
- It sends a required pack offer when players log in.

URL behavior:

- If `resource-pack.public-url` is set, that URL is used.
- If `public-url` is blank and `public-host` is set, the plugin builds `http://{public-host}:{port}{path}`.
- If both are blank, it tries to derive the host from the player's virtual host.

Production note:

- Direct HTTP pack serving on `:24454` is convenient for local/private setups.
- For production, a direct HTTPS zip URL is often cleaner if players or launchers reject non-HTTPS resource-pack URLs.

## Datapack

The datapack zip is bundled into `MaceCupCore.jar`.

Backend startup behavior:

- `MaceCupCore` verifies configured worlds.
- It installs `MaceCupDatapack.zip` into each configured world `datapacks/` folder.
- A restart after first install is recommended so datapack content loads cleanly.

Current config template:

```yaml
datapack:
  file: MaceCupDatapack.zip
  worlds: [world, event_world]
```

## Database Schema

`shared/mysql/schema.sql` creates the `macecup` database and tables.

Tables:

- `players`
- `stats`
- `cosmetics`
- `custom_emotes`
- `purge_pity`
- `resourcepack_status`
- `event_history`
- `cashcup_points`
- `parties`
- `punishments`
- `leaderboard_cache`
- `arena_snapshots`
- `arena_snapshot_blocks`
- `arena_snapshot_entities`

Important fields:

- player UUID and username
- first seen and last seen timestamps
- rank name
- wins, solo wins, duo wins
- kills, deaths
- rating
- highest slam
- heads
- totem pops
- event entries
- cash cup points
- selected cosmetics
- approved custom emotes
- purge pity
- resource pack status and SHA1
- event history
- arena snapshot data

`StatsService` behavior:

- uses local YAML in `plugins/MaceCupCore/stats.yml`
- caches player stats in memory
- attempts MySQL writes when configured and available
- falls back to local async YAML persistence when MySQL is unavailable
- applies a retry cooldown after database failures

## Redis Keys and Event Flow

Redis is used by `RedisStateService` for lightweight state coordination.

Known key patterns:

```text
macecup:server:{serverName}:state
macecup:event:{serverName}:payload
macecup:event:{serverName}:selected
```

Event payload format:

```text
MODE|arenaName|uuid1,uuid2,uuid3
```

Typical hosted event flow:

1. A host runs `/mace host solo <arena>`, `/mace host duo <arena>`, or `/mace host cashcup <solo|duo> <arena>` from `lobby-practice`.
2. `EventManager` verifies the server role is lobby/practice.
3. It verifies the event manager is waiting.
4. It verifies the arena exists.
5. It builds an eligible player list.
6. It includes guaranteed players first.
7. It selects remaining players using purge pity weighting.
8. It picks an available event server from config.
9. It writes event payload and selected-player keys to Redis.
10. It marks the target server reserved.
11. It transfers selected players to the event backend through the plugin message channel.
12. The event backend polls Redis.
13. The event backend starts countdown.
14. The event backend starts the match, fills loot, sets border, equips players, and tracks alive players.
15. Winners get stats, title, sound, and fireworks.
16. Players are returned to lobby.
17. The server state returns to waiting.

If Redis is unavailable, live network state stays local and cross-server event coordination will not work reliably.

## Commands

### Main Paper Command

Base command:

```text
/mace
```

Aliases:

```text
/macecup
/mcup
```

Subcommands from the current command handler:

```text
/mace host solo <arena>
/mace host duo <arena>
/mace host cashcup <solo|duo> <arena>
/mace cancelhost
/mace cosmetics
/mace cosmetic categories
/mace cosmetic list <category>
/mace cosmetic select <category> <id>
/mace cosmetic clear <category>
/mace emotes
/mace emote <wave|cheer|clap|dance|facepalm>
/mace customemote create <name> <text...>
/mace customemote list
/mace customemote use <name>
/mace customemote approve <uuid|player> <name>
/mace admin
/mace top [wins|kills|rating|slam|cashcup]
/mace datapack
/mace resourcepack
/mace network
/mace listarenas
/mace arenawand
/mace arenaselection
/mace savearena <arena> [radius]
/mace regenarena <arena|all>
/mace fillloot <arena>
/mace setcenter <arena>
/mace setborder <arena> <size>
/mace addspawn <arena>
/mace removespawn <arena> <id>
/mace selftest
```

There are intentionally no public queue or join event commands in the current command surface. Events are hosted and selection-based.

### Velocity Command

Base command:

```text
/maceproxy
```

Alias:

```text
/mcproxy
```

Subcommands:

```text
/maceproxy
/maceproxy pick
/maceproxy state <server> <waiting|reserved|running|ending>
/maceproxy send <player> <server>
/maceproxy status
```

Required permission:

```text
macecup.proxy.admin
```

## Permissions

Permissions declared in `plugin.yml`:

```text
macecup.host
macecup.cancelhost
macecup.admin
macecup.bypass
macecup.cosmetics
macecup.top
macecup.stats
macecup.party
macecup.practice
macecup.hats
macecup.emotes
macecup.leaderboard
macecup.emotes.custom
macecup.emotes.custom.approve
macecup.selftest
macecup.purge.vip
macecup.purge.boost.50
macecup.purge.guaranteed
macecup.event.guaranteed
macecup.arena.save
macecup.arena.regen
macecup.arena.wand
macecup.admin.build
macecup.event.manage
```

Command permission behavior:

- `macecup.admin` bypasses most command-specific checks.
- Host commands require `macecup.host`.
- Cancel host requires `macecup.cancelhost`.
- Arena save tools require `macecup.arena.save`.
- Arena regeneration and loot fill require `macecup.arena.regen`.
- Custom emote creation/use requires `macecup.emotes.custom`.
- Custom emote approval requires `macecup.emotes.custom.approve`.
- Self-test requires `macecup.selftest`.

## LuckPerms Groups

From `shared/docs/LUCKPERMS_GROUPS.md`:

```text
default:
  stats, leaderboards, practice, cosmetics, hats, standard emotes

vip:
  default plus custom emotes and macecup.purge.vip

vipplus:
  vip plus macecup.purge.guaranteed and macecup.event.guaranteed

host:
  host/cancel/manage events and staff TAB section

admin:
  host plus admin, bypass, arena save/regen/wand, build, emote approvals, selftest, proxy admin

owner:
  admin plus full owner prefix and owner/server management permissions
```

Paid rank commands for Tebex-style grant flow:

```text
lp user <username> parent add vip
lp user <username> parent add vipplus
```

VIP and VIP+ do not receive `macecup.host`; only Host, Admin, and Owner can host games.

Default setup commands live in:

```text
shared/docs/LuckPerms/setup-commands.txt
```

## Gameplay Flow

### Player Join

1. Player connects through Velocity.
2. Velocity authenticates the player.
3. `MaceCupProxyBridge` sends the required resource pack offer.
4. The player is connected to `lobby-practice`.
5. `MaceCupCore` records player information through `StatsService`.
6. Lobby GUIs, cosmetics, leaderboards, and practice features are available.

### Hosted Event Selection

1. Host starts an event from the lobby.
2. Eligible players are considered.
3. Players with guaranteed permissions are included first.
4. Remaining players are selected by purge pity chance.
5. Selected players have pity reset and event entries incremented.
6. Non-selected players receive increased pity.
7. Selected players are transferred to an event backend.

### Event Round

1. Event backend receives pending event payload through Redis.
2. Countdown begins.
3. Players are set to survival, inventory is cleared, and starting items are given.
4. Players are teleported to arena spawns.
5. Loot is filled.
6. World border starts at arena size and shrinks toward final border.
7. Deaths and kills are tracked.
8. High mace slams are recorded.
9. Winner detection runs when alive count reaches 1 for solo or 2 for duo.
10. Winners receive stats and celebration effects.
11. Players return to lobby.

## Arena Management

Configured arenas live under:

```yaml
arenas:
  solo:
    world: event_world
    center: { x: -650, y: 72, z: 0 }
    border-size: 900
    width: 900
    depth: 900
    spawns: []
  duo:
    world: event_world
    center: { x: 650, y: 72, z: 0 }
    border-size: 900
    width: 900
    depth: 900
    spawns: []
  default:
    world: event_world
    center: ...
    border-size: ...
    spawns: ...
```

Current `event-1` config contains a `default` arena with 100 spawn points around the arena.

Arena tool commands:

```text
/mace arenawand
/mace arenaselection
/mace savearena <arena> [radius]
/mace regenarena <arena|all>
/mace fillloot <arena>
/mace setcenter <arena>
/mace setborder <arena> <size>
/mace addspawn <arena>
/mace removespawn <arena> <id>
```

NBT restoration priority from `shared/docs/ARENA_NBT_GUIDE.md`:

1. FAWE schematic reset.
2. WorldEdit schematic reset.
3. Bukkit fallback.

Bukkit fallback preserves ordinary block states and containers, but exact production NBT regeneration should use FAWE or WorldEdit.

## Cosmetics and Emotes

Cosmetic categories are represented by `CosmeticCategory`.

Player-facing commands:

```text
/mace cosmetics
/mace cosmetic categories
/mace cosmetic list <category>
/mace cosmetic select <category> <id>
/mace cosmetic clear <category>
```

Standard emotes:

```text
wave
cheer
clap
dance
facepalm
```

Custom emotes:

```text
/mace customemote create <name> <text...>
/mace customemote list
/mace customemote use <name>
/mace customemote approve <uuid|player> <name>
```

Current custom emote config:

```yaml
custom-emotes:
  require-approval: true
  cooldown-seconds: 8
  max-length: 96
  blacklist:
    - slur
    - badword
```

Custom emotes require approval before use.

## Stats and Leaderboards

Tracked stats include:

- wins
- solo wins
- duo wins
- kills
- deaths
- rating
- highest slam
- heads
- totem pops
- event entries
- cash cup points
- purge pity

In-game leaderboard command:

```text
/mace top [wins|kills|rating|slam|cashcup]
```

Website leaderboard categories:

```text
wins
kills
rating
slam
cashcup
```

Website player profile data includes:

- UUID
- username
- stats
- K/D
- selected cosmetics
- approved custom emotes

## PlaceholderAPI

`MaceCupCore` registers a PlaceholderAPI expansion when PlaceholderAPI is enabled.

The expansion is implemented in:

```text
source/MaceCupCore/src/main/java/com/macecup/core/placeholder/MaceCupPlaceholders.java
```

TAB and scoreboard configs are expected to consume MaceCup placeholders plus font glyphs from the resource pack.

## TAB and Scoreboards

Documentation:

```text
shared/docs/TAB_CONFIG.md
```

Lobby scoreboard intent:

- MaceCup branding
- rank
- wins
- rating
- online players
- event status
- purge status
- resource pack status
- domain

Event scoreboard intent:

- alive players
- kills
- heads
- totem pops
- border size
- next shrink
- rating
- event server
- domain

The static validation script checks that TAB configs avoid stock placeholders and use icon glyphs from the resource pack.

## Website

Website folder:

```text
website/
```

Package:

```json
{
  "name": "website",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "eslint"
  }
}
```

Dependencies:

- Next `16.2.9`
- React `19.2.4`
- React DOM `19.2.4`
- Tailwind CSS 4 tooling
- ESLint 9

Local commands:

```powershell
cd website
npm install
npm run dev
npm run build
npm run lint
```

Environment:

```text
PROXY_API_URL=http://127.0.0.1:24454
```

If `PROXY_API_URL` is not set, the app defaults to `http://127.0.0.1:24454`.

Website routes:

```text
/
/player/[query]
/privacy
/terms
/api/status
/api/leaderboard
/api/player/[query]
```

Website backend proxying:

- `website/src/app/api/status/route.js` fetches `GET {PROXY_API_URL}/api/status`
- `website/src/app/api/leaderboard/route.js` fetches `GET {PROXY_API_URL}/api/leaderboard?category=...`
- `website/src/app/api/player/[query]/route.js` fetches `GET {PROXY_API_URL}/api/player/{query}`

The homepage shows:

- MaceCup brand
- player search
- leaderboard table
- wins and kills tabs in the current UI

The player page shows:

- player avatar/body render through Crafatar
- rating title
- UUID
- stats cards
- selected cosmetics
- approved custom emotes

## MCP Control Sidecar

Folder:

```text
tools/macecup-mcp/
```

Install:

```powershell
cd tools/macecup-mcp
npm install
```

Run manually:

```powershell
npm start
```

This is a local development control sidecar only.

It is not:

- an AMP startup script
- a Minecraft plugin
- a production service required by players

Exposed MCP tools:

- `list_servers`
- `sync_built_jars`
- `start_server`
- `stop_server`
- `send_console_command`
- `read_console`
- `list_files`
- `read_file`
- `write_file`
- `copy_file`

Tool access is restricted to:

- `lobby-practice`
- `event-1`
- `velocity-na`
- `velocity-eu`

Only servers started by the MCP process can receive console commands or graceful stop commands through it.

## Scripts Reference

### `scripts/package-packs.ps1`

Packages resource pack and datapack, calculates resource pack SHA1, updates source config SHA1.

### `scripts/download-server-jars.ps1`

Downloads Paper and Velocity server jars.

### `scripts/download-dependencies.ps1`

Downloads plugin dependency jars from Modrinth into `shared/dependencies`.

### `scripts/sync-network-setup.ps1`

Copies built plugin jars, dependencies, datapacks, resource packs, and key configs into runtime server folders.

### `scripts/apply-luckperms-setup.ps1`

Applies default LuckPerms groups to backend and proxy stores.

### `scripts/apply-resource-pack-url.ps1`

Applies a resource-pack URL to configs when using externally hosted pack delivery.

### `scripts/validate-network.mjs`

Lightweight Node validation for required files, pack/datapack JSON, and pack SHA1.

### `scripts/test-everything-static.ps1`

Large static validator for runtime folders, configs, dependencies, source restrictions, pack hashes, TAB content, maps, and spawns.

### `scripts/mc-ping.mjs`

Minecraft status ping helper.

### `scripts/start-paper-optimized.ps1`

Local optimized Paper starter.

### `scripts/start-velocity-optimized.ps1`

Local optimized Velocity starter.

### `scripts/paper-smoke.ps1`

Paper smoke helper.

### `scripts/velocity-smoke.ps1`

Velocity smoke helper.

### `scripts/paper-selftest.ps1`

Paper helper for running MaceCup self-test.

### `scripts/smoke-start.ps1`

Local startup smoke helper.

### `scripts/full-network-smoke.ps1`

Full local network smoke helper.

## Troubleshooting

### Gradle Java Version Error

Install JDK 21 and make sure it is first on `PATH`.

### AMP Starts the Wrong Thing

AMP should run only:

```text
paper-1.21.11.jar
velocity.jar
```

Do not run helper scripts as AMP startup commands.

### Port Already in Use

Two instances are trying to bind the same IP and port.

Fix by:

- using separate IPs
- using containers or isolated AMP networks
- changing backend private ports
- updating `velocity.toml`

### Backend Join Fails

Check:

- Velocity modern forwarding is enabled
- Paper Velocity forwarding is enabled
- forwarding secrets match
- backend is reachable from proxy
- backend is not publicly exposed

### Players Can Join Paper Directly

Firewall or private-network the backend. Only Velocity should be public.

### Resource Pack Warning or Mismatch

Run:

```powershell
.\scripts\package-packs.ps1
.\scripts\sync-network-setup.ps1
node .\scripts\validate-network.mjs
```

Then verify SHA1 values in:

```text
shared/resource-pack/SHA1.txt
lobby-practice/plugins/MaceCupCore/config.yml
event-1/plugins/MaceCupCore/config.yml
lobby-practice/server.properties
event-1/server.properties
velocity-na/plugins/MaceCupProxyBridge/resource-pack/SHA1.txt
velocity-eu/plugins/MaceCupProxyBridge/resource-pack/SHA1.txt
```

### MySQL Unavailable

`StatsService` falls back to local YAML and retries after a cooldown. Website leaderboard/player APIs require MySQL-backed data through the proxy HTTP API, so website data can return `503` until MySQL is available.

### Redis Unavailable

`RedisStateService` logs a warning and local state remains local. Hosted cross-server event coordination needs Redis.

### Arena NBT Problems

Use FAWE or WorldEdit schematic mode for exact production reset. Bukkit fallback is not a full NBT-safe replacement.

### Website API Unavailable

Check:

- Velocity is running.
- `MaceCupProxyBridge` loaded.
- resource-pack/API HTTP server is listening on `24454`.
- `PROXY_API_URL` points at the reachable proxy API.
- MySQL is reachable for leaderboard and player data.

## Known Current-State Notes

These notes reflect the workspace as inspected when this README was created.

1. Root `README.md` did not exist before this file.
2. `MaceCupCore` forced compilation succeeded with `.\gradlew.bat :source:MaceCupCore:compileJava --rerun-tasks`.
3. Some older docs mention dimensions like `1275 x 1750`, while the current observed `event-1/plugins/MaceCupCore/config.yml` uses a `1250 x 1250` crystal-rift setup and 100 spawns.
4. `shared/resource-pack/README_RESOURCEPACK.md` listed SHA1 `c44addc9989b94972b33b739e9365ade2664c3fb`, while current runtime configs observed in backend folders use `a56922eb1a1a501342138d03e6654bf66800bd50`. Run pack packaging and sync to make all SHA1 locations consistent.
5. Some UI/source text contains mojibake in the current files, such as rendered symbols appearing as garbled characters. That is separate from this README and may be worth cleaning in website and plugin message strings.
6. The PowerShell helper scripts use local file rewrites. Review diffs before committing generated config churn.

## Operational Checklist

Before local smoke testing:

- Install Java 21.
- Install Node.js and npm.
- Run `.\gradlew.bat build`.
- Run `.\scripts\package-packs.ps1`.
- Run `.\scripts\download-server-jars.ps1`.
- Run `.\scripts\download-dependencies.ps1`.
- Run `.\scripts\sync-network-setup.ps1`.
- Run `.\scripts\apply-luckperms-setup.ps1`.
- Start MySQL and Redis with `docker compose up -d`.
- Run `node .\scripts\validate-network.mjs`.
- Run `.\scripts\test-everything-static.ps1`.

Before AMP upload:

- Confirm plugin jars exist.
- Confirm runtime `plugins/` folders contain synced dependencies.
- Confirm server jars exist.
- Confirm `forwarding.secret` matches across Velocity and Paper configs.
- Confirm Paper backends are private.
- Confirm only Velocity is public.
- Confirm backend ports and `velocity.toml` target addresses match the hosting environment.
- Confirm MySQL and Redis credentials are not public defaults in production.
- Confirm resource-pack URL strategy is decided: Velocity-hosted or external HTTPS zip.

Before live player testing:

- Join through Velocity, not backend.
- Confirm resource pack prompt appears once.
- Confirm pack status is accepted.
- Confirm lobby GUI and compass behavior.
- Confirm cosmetics GUI.
- Confirm `/mace network`.
- Confirm `/mace datapack`.
- Confirm `/mace resourcepack`.
- Confirm `/mace selftest`.
- Confirm `/mace host solo default` or another configured arena with test players.
- Confirm transfer to event server.
- Confirm countdown, start items, world border, deaths, winner detection, stats, and return to lobby.
- Confirm website status, leaderboard, and player profile APIs.

## Useful Files

Setup and troubleshooting:

```text
README_SETUP.md
TROUBLESHOOTING.md
shared/docs/ADMIN_GUIDE.md
shared/docs/PLAYER_GUIDE.md
shared/docs/TESTING_CHECKLIST.md
shared/docs/BOOT_TEST_REPORT.md
```

Network config:

```text
velocity-na/velocity.toml
velocity-eu/velocity.toml
lobby-practice/server.properties
event-1/server.properties
lobby-practice/plugins/MaceCupCore/config.yml
event-1/plugins/MaceCupCore/config.yml
velocity-na/plugins/MaceCupProxyBridge/config.yml
velocity-eu/plugins/MaceCupProxyBridge/config.yml
```

Source:

```text
source/MaceCupCore/src/main/java/com/macecup/core/MaceCupCore.java
source/MaceCupCore/src/main/java/com/macecup/core/command/MaceCommand.java
source/MaceCupCore/src/main/java/com/macecup/core/event/EventManager.java
source/MaceCupCore/src/main/java/com/macecup/core/storage/StatsService.java
source/MaceCupCore/src/main/java/com/macecup/core/world/EventBoundaryListener.java
source/MaceCupCore/src/main/java/com/macecup/core/world/MapBuilder.java
source/MaceCupProxyBridge/src/main/java/com/macecup/proxy/MaceCupProxyBridge.java
source/MaceCupProxyBridge/src/main/java/com/macecup/proxy/HttpApiServer.java
source/MaceCupProxyBridge/src/main/java/com/macecup/proxy/ResourcePackHost.java
```

Website:

```text
website/src/app/page.js
website/src/app/player/[query]/page.js
website/src/app/api/status/route.js
website/src/app/api/leaderboard/route.js
website/src/app/api/player/[query]/route.js
```

Database:

```text
shared/mysql/schema.sql
docker-compose.yml
```

Generated packs:

```text
shared/resource-pack/MaceCupResourcePack.zip
shared/resource-pack/SHA1.txt
shared/datapack/MaceCupDatapack.zip
```
