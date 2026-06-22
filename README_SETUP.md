# MaceCup Network

Production-oriented Paper/Velocity network scaffold for macecup.xyz.

Requirements: Java 21, Gradle, Docker Desktop or managed MySQL/Redis. On AMP, each instance only needs to run the server `.jar`; all MaceCup features, pack handling, arena tools, permissions, TAB, scoreboard, Redis, and MySQL behavior live in the plugin jars and config folders.

Build:
1. Install JDK 21 and Gradle.
2. From this folder, run `.\gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS.
3. Copy `source/MaceCupCore/build/libs/MaceCupCore.jar` into every backend `plugins/` folder.
4. Copy `source/MaceCupProxyBridge/build/libs/MaceCupProxyBridge.jar` into both Velocity `plugins/` folders.
5. Run `scripts/download-server-jars.ps1`.
6. Start MySQL and Redis with `docker compose up -d`, or point the configs at your managed/private MySQL and Redis services.
7. Upload/sync the prepared server folders into AMP and start Velocity NA/EU, then lobby-practice and event-1.

AMP launch setup:
- Create one AMP instance per folder: `velocity-na`, `velocity-eu`, `lobby-practice`, and `event-1`.
- Set the backend server executable jar to `paper-1.21.11.jar`.
- Set the proxy server executable jar to `velocity.jar`.
- Do not configure AMP to run `.ps1`, `.bat`, or wrapper scripts at runtime. The scripts in this repo are local build/sync/test helpers only.
- Use Java 21 in AMP. If AMP exposes JVM arguments, set memory there; if it only lets you choose the jar and memory sliders, that is enough.
- Suggested memory: Velocity `512M-1536M`, lobby `2G-4G`, event `2G-6G` depending on player count.
- Only expose Velocity to players. Keep Paper backend ports private to the same host, LAN, VPN, or AMP network.
- Backend `online-mode=false` is intentional because Velocity handles authentication and modern forwarding.

Optional JVM flags if AMP provides an "additional JVM arguments" field:
- Paper: `-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch`
- Velocity: `-XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+ParallelRefProcEnabled -XX:+DisableExplicitGC`

For separate EU/NA home hosts, run the matching Velocity folder publicly on each host and keep backend Paper servers private to that same host/LAN. GeoIP transfer is disabled by default to avoid an external HTTP lookup on every login; set `proxies.geoip-routing-enabled: true` in the Velocity plugin config only if you want automatic cross-region transfer.

Local full sync before uploading to AMP:
1. Run `scripts/sync-network-setup.ps1` to copy plugins, dependency jars, datapacks, resource packs, and pack settings into every server folder.
2. Run `scripts/apply-luckperms-setup.ps1` to apply the default LuckPerms groups to every backend and proxy store.
3. Run `scripts/test-everything-static.ps1` to verify the full local layout.

Local MCP control sidecar:
- The MCP server lives in `tools/macecup-mcp` and is for local development control only.
- It can sync built plugin jars, start/stop local Paper/Velocity jars, send console commands, read captured console logs, and inspect/copy/write files inside the four known local server folders.
- It is already declared in `.vscode/mcp.json` as `macecup-control`.
- Install it once with `cd tools/macecup-mcp; npm install`.
- AMP still runs only the server `.jar` files. Do not point AMP at the MCP sidecar or any script.

The same generated Velocity forwarding secret is already configured everywhere.

Port layout:
- `velocity-na` and `velocity-eu` both bind to `0.0.0.0:25565`.
- `lobby-practice` and `event-1` both use `server-port=25565`.
- Velocity targets `lobby-practice:25565` and `event-1:25565`.
- This assumes each folder runs as its own AMP instance, hosted server, container, machine, or private IP. If multiple AMP instances share one IP on one physical machine, only one process can bind `25565` on that IP; give each backend a private port in AMP and update the matching server address in `velocity.toml`.

Pack install behavior:
- `MaceCupCore.jar` bundles `MaceCupDatapack.zip` and `MaceCupResourcePack.zip`.
- On backend startup, the plugin copies the datapack into each configured world `datapacks/` folder.
- On backend startup, the plugin exports the resource pack to `plugins/MaceCupCore/resource-pack/MaceCupResourcePack.zip` and calculates SHA1.
- `MaceCupProxyBridge.jar` also bundles the resource pack and exports it to `plugins/MaceCupProxyBridge/resource-pack/MaceCupResourcePack.zip`.
- The bundled pack is offered by `MaceCupProxyBridge` through Velocity's resource-pack host.
- Keep `resource-pack.host-enabled: true` and leave `resource-pack.public-url` blank to derive the URL from the player's virtual host, or set `public-url` to a direct HTTPS ZIP URL after uploading `shared/resource-pack/MaceCupResourcePack.zip`.
- Players are required to use the pack through Velocity; backend `server.properties` resource-pack URLs stay blank to avoid duplicate stale prompts.
