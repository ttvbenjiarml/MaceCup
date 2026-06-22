# Troubleshooting

- If Gradle fails with a Java version error, install JDK 21 and put it first on PATH.
- In AMP, set the executable to the server jar only: `paper-1.21.11.jar` for Paper instances and `velocity.jar` for proxy instances. Do not use the PowerShell helper scripts as AMP startup commands.
- If an AMP instance says the port is already in use, two instances are trying to bind the same IP and port. Put each instance on its own IP/container/network, or change backend private ports in AMP and update `velocity.toml`.
- If backend joins fail, check Velocity modern forwarding and the shared secret.
- If players can join a Paper backend directly, firewall or AMP-network it off. Only Velocity should be public.
- If resource pack warnings appear, rerun `scripts/validate-network.mjs` and paste the SHA1 into MaceCupCore configs.
- For exact NBT arena restore, install FAWE or WorldEdit and use schematic mode.
- Keep MySQL and Redis private.
