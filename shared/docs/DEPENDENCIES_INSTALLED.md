# Dependencies Installed

Downloaded jars are stored in `shared/dependencies`.

Runtime backend plugin set:

- `MaceCupCore.jar`
- `luckperms-v5.5.53-bukkit.jar`
- `placeholderapi-2.12.2.jar`
- `tab-was-taken-6.1.0.jar`
- `vaultunlocked-2.20.1.jar`
- `FastAsyncWorldEdit-2.15.2.jar`
- `worldguard-7.0.17.jar`
- `Citizens-2.0.43-b4210.jar`
- `decentholograms-2.10.0.jar`

Runtime proxy plugin set:

- `MaceCupProxyBridge.jar`
- `luckperms-v5.5.53-velocity-velocity.jar`

Notes:

- Paper 1.21.11 bundles spark, so no separate spark jar was installed.
- Standalone WorldEdit was downloaded and kept in `shared/dependencies`, but it is not installed at runtime because FAWE also registers as `WorldEdit`. Installing both caused Paper to reject the duplicate plugin name. FAWE is the active runtime WorldEdit provider.
