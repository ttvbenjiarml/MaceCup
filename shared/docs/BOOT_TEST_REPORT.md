# Boot Test Report

Date: 2026-06-20

## Passed

- `gradle build --no-daemon`
- `gradle clean build --no-daemon`
- `scripts/validate-network.mjs`
- `scripts/test-everything-static.ps1`
- `scripts/paper-selftest.ps1 -Name lobby-command-selftest -WorkDir lobby-practice -Jar paper-1.21.11.jar`
- `scripts/paper-smoke.ps1 -Name lobby-map-v2d -WorkDir lobby-practice -Jar paper-1.21.11.jar`
- `scripts/paper-smoke.ps1 -Name event-1-map-v2d -WorkDir event-1 -Jar paper-1.21.11.jar`
- Downloaded and installed public dependency jars:
  - LuckPerms
  - PlaceholderAPI
  - TAB
  - VaultUnlocked
  - FAWE
  - WorldGuard
  - Citizens
  - DecentHolograms
  - spark is bundled by Paper 1.21.11
- Paper 1.21.11 startup smoke:
  - `lobby-practice` reached `Done` and loaded `MaceCupCore`.
  - `event-1` reached `Done` and loaded `MaceCupCore`.
- Full dependency backend startup smoke:
  - `lobby-practice-full2` reached `Done` with MaceCupCore, LuckPerms, PlaceholderAPI, TAB, Vault, FAWE, WorldGuard, Citizens, and DecentHolograms.
  - `event-1-full2` reached `Done` with the same dependency set.
- Velocity startup smoke:
  - `velocity-na` reached `Done`, listened on `25565`, and loaded `MaceCupProxyBridge`.
  - `velocity-eu` reached `Done`, listened on `25566`, and loaded `MaceCupProxyBridge`.
- Full network smoke:
  - `lobby-practice`, `event-1`, `velocity-na`, and `velocity-eu` were running at the same time.
  - Velocity NA returned a valid Minecraft status response on `127.0.0.1:25565`.
  - Velocity EU returned a valid Minecraft status response on `127.0.0.1:25566`.
  - Latest run:
    - `lobby-practice` ready.
    - `event-1` ready.
    - `velocity-na` ready/listening on `25565`.
    - `velocity-eu` ready/listening on `25566`.
- Command/cosmetic/emote self-test:
  - `SELFTEST ALL PASSED` in `shared/docs/test-logs/lobby-command-selftest.log`.
  - Verified all 12 cosmetic categories are registered.
  - Verified cosmetic select, persistence, clear, and invalid-id rejection.
  - Verified standard emote catalog loads.
  - Verified custom emote blacklist, invalid names, create, approval gate, approval, use, and cooldown.
  - Verified arenas, event state, and datapack status are available after plugin load.
- Automatic pack install:
  - `MaceCupCore.jar` contains `MaceCupDatapack.zip` and `MaceCupResourcePack.zip`.
  - Every backend exported `plugins/MaceCupCore/resource-pack/MaceCupResourcePack.zip`.
  - Exported resource pack SHA1 matched the active `shared/resource-pack/SHA1.txt` value.
  - Every backend `server.properties` was patched with the resource pack URL, SHA1, and `require-resource-pack=true`.
  - Datapack zip exists in each configured `world/datapacks` and `event_world/datapacks` location.
- Log scans found no `ERROR`, `SEVERE`, `Exception`, `Could not load`, or `Failed to start` entries in the saved smoke logs.

## Saved Logs

- `shared/docs/test-logs/lobby-practice.log`
- `shared/docs/test-logs/event-1.log`
- `shared/docs/test-logs/velocity-na.log`
- `shared/docs/test-logs/velocity-eu.log`
- `shared/docs/test-logs/lobby-command-selftest.log`
- `shared/docs/test-logs/full-network/`

## Map Generation

- The plugin generated a starter lobby/practice map in `lobby-practice/world`.
- The plugin generated event arena maps in:
  - `event-1/event_world`: `crystal-rift`, seed `macecup-crystal-rift-1275x1750`
- The event server config now has 100 generated arena spawn points.
- The event arena config has `width: 1275`, `depth: 1750`, `border-size: 1750`, `map.event-width: 1275`, and `map.event-depth: 1750`.
- `EventBoundaryListener` enforces the rectangular `1275 x 1750` gameplay boundary because Minecraft's native `WorldBorder` is square.
- One-time v2 map marker files were written under each `plugins/MaceCupCore/` folder so production restarts do not rewrite map config every boot.

## Local Limits

- Docker is not installed on PATH in this environment, so MySQL and Redis were not started locally.
- Live player workflow tests such as authenticated player login, compass movement, resource-pack accept/decline, real event transfer, WorldGuard region flag interaction, and PvP winner detection require Minecraft clients or a protocol bot with authenticated sessions. Velocity is configured with `online-mode=true`, so offline bot login was not used.
