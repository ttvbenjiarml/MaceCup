# Testing Checklist

Completed locally:

- Build MaceCupCore with Gradle.
- Build MaceCupProxyBridge with Gradle.
- Run a clean Gradle build.
- Validate project structure and pack/datapack JSON with `scripts/validate-network.mjs`.
- Validate configs, jars, secrets, dependency presence, maps, spawns, pack hashes, and source command restrictions with `scripts/test-everything-static.ps1`.
- Zip resource pack and calculate SHA1.
- Zip datapack and install it into every configured datapack folder.
- Download dependency jars into `shared/dependencies`.
- Install dependencies into runtime plugin folders.
- Generate lobby/practice and event maps by booting the servers.
- Start `velocity-na`.
- Start `velocity-eu`.
- Start `lobby-practice`.
- Start `event-1`.
- Verify all custom plugins load.
- Verify dependency plugin set loads.
- Verify `view-distance=12` and `simulation-distance=7` in every backend config.
- Verify both Velocity proxies answer Minecraft status pings.
- Scan smoke logs for startup errors.

Needs live/integration environment:

- Start MySQL/Redis with Docker or managed services.
- Join with authenticated Minecraft clients to test compass slot locking, GUI interaction, resource-pack status, hosted event transfer, purge behavior, loot, winner detection, return-to-lobby, and arena regeneration after real players leave.
