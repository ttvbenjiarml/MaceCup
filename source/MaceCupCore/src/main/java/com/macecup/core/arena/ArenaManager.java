package com.macecup.core.arena;

import com.macecup.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Container;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ArenaManager implements org.bukkit.event.Listener {
    private final JavaPlugin plugin;
    private final Map<String, ArenaConfig> arenas = new HashMap<>();
    private final Map<String, ArenaSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final NamespacedKey arenaWandKey;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.arenaWandKey = new NamespacedKey(plugin, "arena_wand");
    }

    public void loadConfiguredArenas() {
        arenas.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("arenas");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            arenas.put(key.toLowerCase(Locale.ROOT), ArenaConfig.fromSection(key, section.getConfigurationSection(key)));
        }
    }

    public Collection<ArenaConfig> arenas() {
        return arenas.values();
    }

    public ArenaConfig arena(String name) {
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    // ── Save ──────────────────────────────────────────────────────────
    public boolean saveArena(String name, CommandSender sender, int radiusOverride) {
        if (sender instanceof Player player && radiusOverride <= 0) {
            Selection selection = selections.get(player.getUniqueId());
            if (selection != null && selection.complete()) {
                return saveSelectedArena(name, player, selection);
            }
        }

        ArenaConfig arena = arena(name);
        if (arena == null) {
            sender.sendMessage(Text.color("&cUnknown arena: " + name));
            return false;
        }
        World world = arena.world();
        if (world == null) {
            sender.sendMessage(Text.color("&cWorld is not loaded: " + arena.worldName()));
            return false;
        }

        int radius = radiusOverride > 0 ? radiusOverride :
                plugin.getConfig().getInt("arenas." + name + ".snapshot-radius",
                        Math.max(16, (int) Math.min(625, arena.borderSize() / 2)));

        broadcastRegen("&8[&dArena&8] &fSaving snapshot of &b" + arena.name()
                + " &f(radius &b" + radius + "&f)...");

        long start = System.currentTimeMillis();
        ArenaSnapshot snapshot = capture(arena, radius);
        long ms = System.currentTimeMillis() - start;

        snapshots.put(arena.name().toLowerCase(Locale.ROOT), snapshot);
        plugin.getConfig().set("arenas." + name + ".saved-at", System.currentTimeMillis());
        plugin.getConfig().set("arenas." + name + ".snapshot-radius", radius);
        plugin.getConfig().set("arenas." + name + ".snapshot-blocks", snapshot.blockCount());
        plugin.saveConfig();

        broadcastRegen("&8[&dArena&8] &aSaved &b" + snapshot.blockCount()
                + " &ablocks for &f" + arena.name() + " &ain &b" + ms + "ms&a.");
        return true;
    }

    public boolean saveArena(String name, CommandSender sender) {
        return saveArena(name, sender, -1);
    }

    public void giveArenaWand(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_SHOVEL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color("&dMaceCup Arena Selector"));
        meta.setLore(List.of(
                Text.color("&7Left-click a block for pos1."),
                Text.color("&7Right-click a block for pos2."),
                Text.color("&7Then run &f/mace savearena <name>&7.")
        ));
        meta.getPersistentDataContainer().set(arenaWandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        player.sendMessage(Text.color("&dArena selector: &fleft-click pos1, right-click pos2, then /mace savearena <name>."));
    }

    public String selectionStatus(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null) return "pos1=unset pos2=unset";
        return "pos1=" + format(selection.pos1()) + " pos2=" + format(selection.pos2());
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onArenaWandUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        ItemStack item = event.getItem();
        if (!isArenaWand(item)) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        Block block = event.getClickedBlock();
        Location location = block.getLocation();
        org.bukkit.event.block.Action action = event.getAction();
        if (action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            selection.setPos1(location);
            player.sendMessage(Text.color("&dArena pos1 set to &f" + format(location) + "&d."));
        } else if (action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            selection.setPos2(location);
            player.sendMessage(Text.color("&dArena pos2 set to &f" + format(location) + "&d."));
        }
        if (selection.complete()) {
            player.sendMessage(Text.color("&aSelection ready: &f" + selection.blockCount() + " blocks&a. Save it with &f/mace savearena <name>&a."));
        }
    }

    private boolean isArenaWand(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SHOVEL || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(arenaWandKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private boolean saveSelectedArena(String name, Player player, Selection selection) {
        if (!selection.sameWorld()) {
            player.sendMessage(Text.color("&cBoth arena selection positions must be in the same world."));
            return false;
        }
        long blockCount = selection.blockCount();
        long maxBlocks = plugin.getConfig().getLong("arena-selection.max-blocks", 2_000_000L);
        if (blockCount > maxBlocks) {
            player.sendMessage(Text.color("&cSelection is too large: " + blockCount + " blocks. Max is " + maxBlocks + "."));
            return false;
        }

        World world = selection.world();
        String path = "arenas." + name;
        Location center = selection.center();
        plugin.getConfig().set(path + ".world", world.getName());
        plugin.getConfig().set(path + ".center.x", center.getX());
        plugin.getConfig().set(path + ".center.y", center.getY());
        plugin.getConfig().set(path + ".center.z", center.getZ());
        plugin.getConfig().set(path + ".border-size", Math.max(selection.width(), selection.depth()));
        plugin.getConfig().set(path + ".width", selection.width());
        plugin.getConfig().set(path + ".depth", selection.depth());
        plugin.getConfig().set(path + ".selection.min", serialize(selection.min()));
        plugin.getConfig().set(path + ".selection.max", serialize(selection.max()));
        List<String> spawns = plugin.getConfig().getStringList(path + ".spawns");
        if (spawns.isEmpty()) spawns = List.of(player.getLocation().getX() + "," + player.getLocation().getY() + "," + player.getLocation().getZ());
        plugin.getConfig().set(path + ".spawns", spawns);
        plugin.saveConfig();
        loadConfiguredArenas();

        broadcastRegen("&8[&dArena&8] &fSaving selected arena &b" + name + " &f(&b" + blockCount + " &fblocks)...");
        long start = System.currentTimeMillis();
        ArenaSnapshot snapshot = captureCuboid(world, selection.min(), selection.max(), true);
        snapshots.put(name.toLowerCase(Locale.ROOT), snapshot);
        plugin.getConfig().set(path + ".saved-at", System.currentTimeMillis());
        plugin.getConfig().set(path + ".snapshot-blocks", snapshot.blockCount());
        plugin.saveConfig();
        player.sendMessage(Text.color("&aArena &f" + name + " &asaved from selection. Add spawns with &f/mace addspawn " + name + "&a."));
        broadcastRegen("&8[&dArena&8] &aSaved &b" + snapshot.blockCount() + " &ablocks for &f" + name + " &ain &b" + (System.currentTimeMillis() - start) + "ms&a.");
        return true;
    }

    // ── Regenerate ────────────────────────────────────────────────────
    public void regenerateAll(CommandSender sender) {
        for (ArenaConfig arena : arenas.values()) regenerate(arena.name(), sender);
    }

    public boolean regenerate(String name, CommandSender sender) {
        ArenaConfig arena = arena(name);
        if (arena == null) {
            sender.sendMessage(Text.color("&cUnknown arena: " + name));
            return false;
        }
        ArenaSnapshot snapshot = snapshots.get(arena.name().toLowerCase(Locale.ROOT));
        if (snapshot == null) {
            sender.sendMessage(Text.color("&cNo snapshot exists for &f" + arena.name()
                    + "&c. Use &f/mace savearena " + arena.name() + " &cfirst."));
            return false;
        }

        // ── Entity cleanup ──
        World world = arena.world();
        if (world != null) {
            int removed = 0;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;
                EntityType type = entity.getType();
                if (type == EntityType.ITEM || type == EntityType.ARROW
                        || type == EntityType.WIND_CHARGE || type == EntityType.EXPERIENCE_ORB
                        || type == EntityType.ENDER_PEARL || type == EntityType.FIREWORK_ROCKET
                        || type == EntityType.FALLING_BLOCK || type == EntityType.ITEM_DISPLAY
                        || type == EntityType.TEXT_DISPLAY || type == EntityType.AREA_EFFECT_CLOUD) {
                    entity.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                broadcastRegen("&8[&dArena&8] &7Cleaned &f" + removed + " &7entities.");
            }
        }

        // ── Block restore with progress ──
        broadcastRegen("&8[&dArena&8] &fRegenerating &b" + arena.name()
                + " &f(&b" + snapshot.blockCount() + " &fblocks)...");

        long startTime = System.currentTimeMillis();
        snapshot.restore(plugin, (percent, done) -> {
            if (percent < 100) {
                broadcastRegen("&8[&dArena&8] &f" + arena.name() + " &8▸ &b" + percent + "% &7(" + done + " blocks)");
            } else {
                long elapsed = System.currentTimeMillis() - startTime;
                broadcastRegen("&8[&dArena&8] &a✔ &f" + arena.name()
                        + " &afully regenerated &7(" + done + " blocks in &b" + elapsed + "ms&7)");
            }
        });
        return true;
    }

    // ── Center / Border / Spawn management ────────────────────────────
    public void setCenter(String name, Location location) {
        plugin.getConfig().set("arenas." + name + ".world", location.getWorld().getName());
        plugin.getConfig().set("arenas." + name + ".center.x", location.getX());
        plugin.getConfig().set("arenas." + name + ".center.y", location.getY());
        plugin.getConfig().set("arenas." + name + ".center.z", location.getZ());
        plugin.saveConfig();
        loadConfiguredArenas();
    }

    public void setBorder(String name, double size) {
        plugin.getConfig().set("arenas." + name + ".border-size", size);
        plugin.saveConfig();
        loadConfiguredArenas();
    }

    public void addSpawn(String name, Location location) {
        List<String> spawns = plugin.getConfig().getStringList("arenas." + name + ".spawns");
        spawns.add(location.getX() + "," + location.getY() + "," + location.getZ());
        plugin.getConfig().set("arenas." + name + ".spawns", spawns);
        plugin.saveConfig();
        loadConfiguredArenas();
    }

    public void removeSpawn(String name, int index) {
        List<String> spawns = plugin.getConfig().getStringList("arenas." + name + ".spawns");
        if (index >= 0 && index < spawns.size()) spawns.remove(index);
        plugin.getConfig().set("arenas." + name + ".spawns", spawns);
        plugin.saveConfig();
        loadConfiguredArenas();
    }

    // ── JIT loot (chunk-load based) ───────────────────────────────────
    private final java.util.Set<Long> lootFilledChunks = new java.util.HashSet<>();

    public void clearLootFilledChunks() {
        lootFilledChunks.clear();
    }

    public void fillChunkLoot(Chunk chunk, List<String> loot) {
        long key = ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
        if (lootFilledChunks.contains(key)) return;
        lootFilledChunks.add(key);

        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof Container container)) continue;
            container.getInventory().clear();
            for (String line : loot) {
                String[] parts = line.split(":");
                if (parts.length < 3) continue;
                Material material = Material.matchMaterial(parts[0]);
                if (material == null) continue;
                int min = Integer.parseInt(parts[1]);
                int max = Integer.parseInt(parts[2]);
                int amount = min + (int) (Math.random() * Math.max(1, max - min + 1));
                if (Math.random() >= 0.45) container.getInventory().addItem(new ItemStack(material, amount));
            }
            state.update(true);
        }
    }

    public void fillLoot(ArenaConfig arena) {
        World world = arena.world();
        if (world == null) return;
        List<String> loot = plugin.getConfig().getStringList("loot.event");
        clearLootFilledChunks();
        for (Chunk chunk : world.getLoadedChunks()) {
            fillChunkLoot(chunk, loot);
        }
    }

    @org.bukkit.event.EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!(plugin instanceof com.macecup.core.MaceCupCore core)) return;
        if (core.serverRole() != com.macecup.core.ServerRole.EVENT) return;
        com.macecup.core.event.EventManager em = core.getEventManager();
        if (em == null) return;
        com.macecup.core.event.EventState state = em.state();
        if (state == com.macecup.core.event.EventState.RUNNING || state == com.macecup.core.event.EventState.FINAL_CIRCLE) {
            List<String> loot = plugin.getConfig().getStringList("loot.event");
            fillChunkLoot(event.getChunk(), loot);
        }
    }

    // ── Broadcast helper ──────────────────────────────────────────────
    private void broadcastRegen(String message) {
        String colored = Text.color(message);
        Bukkit.getConsoleSender().sendMessage(colored);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("macecup.arena.regen")) {
                player.sendMessage(colored);
            }
        }
    }

    // ── Snapshot capture ──────────────────────────────────────────────
    private ArenaSnapshot capture(ArenaConfig arena, int radius) {
        World world = arena.world();
        List<BlockSnapshot> blocks = new ArrayList<>();
        if (world == null) return new ArenaSnapshot(blocks);
        Location center = arena.center();
        return captureCuboid(world,
                new Location(world, center.getBlockX() - radius, world.getMinHeight(), center.getBlockZ() - radius),
                new Location(world, center.getBlockX() + radius, world.getMaxHeight() - 1, center.getBlockZ() + radius),
                false);
    }

    private ArenaSnapshot captureCuboid(World world, Location min, Location max, boolean includeAir) {
        List<BlockSnapshot> blocks = new ArrayList<>();
        if (world == null) return new ArenaSnapshot(blocks);
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.max(world.getMinHeight(), Math.min(min.getBlockY(), max.getBlockY()));
        int maxY = Math.min(world.getMaxHeight() - 1, Math.max(min.getBlockY(), max.getBlockY()));
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!includeAir && block.getType().isAir()) continue;
                    blocks.add(BlockSnapshot.capture(block));
                }
            }
        }
        return new ArenaSnapshot(blocks);
    }

    private String format(Location location) {
        if (location == null || location.getWorld() == null) return "unset";
        return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private Map<String, Integer> serialize(Location location) {
        Map<String, Integer> values = new HashMap<>();
        values.put("x", location.getBlockX());
        values.put("y", location.getBlockY());
        values.put("z", location.getBlockZ());
        return values;
    }

    // ── Inner classes ─────────────────────────────────────────────────

    /**
     * Captures the complete state of a single block, including all tile entity
     * NBT data: container inventories (with full item NBT — enchantments,
     * custom names, lore, etc.), sign text, spawner config, banner patterns,
     * and more.
     */
    static final class BlockSnapshot {
        final int x, y, z;
        final String worldName;
        final String blockData;

        // Tile-entity data (only populated for the matching types)
        ItemStack[] containerContents;
        String[] signFrontLines;
        String[] signBackLines;
        boolean signWaxed;
        EntityType spawnerType;
        int spawnerDelay;
        int spawnerMinDelay;
        int spawnerMaxDelay;
        int spawnerMaxNearbyEntities;
        int spawnerSpawnCount;
        int spawnerSpawnRange;
        int spawnerRequiredPlayerRange;
        org.bukkit.DyeColor bannerBase;
        List<org.bukkit.block.banner.Pattern> bannerPatterns;

        BlockSnapshot(int x, int y, int z, String worldName, String blockData) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldName = worldName;
            this.blockData = blockData;
        }

        static BlockSnapshot capture(Block block) {
            BlockSnapshot snap = new BlockSnapshot(
                    block.getX(), block.getY(), block.getZ(),
                    block.getWorld().getName(),
                    block.getBlockData().getAsString());

            BlockState state = block.getState();

            // ── Containers: full NBT-preserving inventory snapshot ──
            if (state instanceof Container container) {
                ItemStack[] raw = container.getInventory().getContents();
                snap.containerContents = new ItemStack[raw.length];
                for (int i = 0; i < raw.length; i++) {
                    snap.containerContents[i] = raw[i] != null ? raw[i].clone() : null;
                }
            }

            // ── Signs: capture all text lines (front + back) ──
            if (state instanceof Sign sign) {
                try {
                    SignSide front = sign.getSide(Side.FRONT);
                    SignSide back = sign.getSide(Side.BACK);
                    snap.signFrontLines = new String[4];
                    snap.signBackLines = new String[4];
                    for (int i = 0; i < 4; i++) {
                        snap.signFrontLines[i] = front.getLine(i);
                        snap.signBackLines[i] = back.getLine(i);
                    }
                    snap.signWaxed = sign.isWaxed();
                } catch (Exception ignored) {
                    // Fallback for older API
                    snap.signFrontLines = sign.getLines().clone();
                }
            }

            // ── Spawners: full config ──
            if (state instanceof CreatureSpawner spawner) {
                snap.spawnerType = spawner.getSpawnedType();
                snap.spawnerDelay = spawner.getDelay();
                snap.spawnerMinDelay = spawner.getMinSpawnDelay();
                snap.spawnerMaxDelay = spawner.getMaxSpawnDelay();
                snap.spawnerMaxNearbyEntities = spawner.getMaxNearbyEntities();
                snap.spawnerSpawnCount = spawner.getSpawnCount();
                snap.spawnerSpawnRange = spawner.getSpawnRange();
                snap.spawnerRequiredPlayerRange = spawner.getRequiredPlayerRange();
            }

            // ── Banners: base color + pattern layers ──
            if (state instanceof Banner banner) {
                snap.bannerBase = banner.getBaseColor();
                snap.bannerPatterns = new ArrayList<>(banner.getPatterns());
            }

            return snap;
        }

        void restore() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            Block block = world.getBlockAt(x, y, z);

            // Set the block data (includes type, orientation, waterlogged state, etc.)
            block.setBlockData(Bukkit.createBlockData(blockData), false);

            BlockState state = block.getState();

            // ── Restore container inventory with full NBT ──
            if (containerContents != null && state instanceof Container container) {
                container.getInventory().setContents(containerContents);
                container.update(true, false);
                return; // update() already called
            }

            // ── Restore sign text ──
            if (signFrontLines != null && state instanceof Sign sign) {
                try {
                    SignSide front = sign.getSide(Side.FRONT);
                    for (int i = 0; i < Math.min(4, signFrontLines.length); i++) {
                        front.setLine(i, signFrontLines[i] != null ? signFrontLines[i] : "");
                    }
                    if (signBackLines != null) {
                        SignSide back = sign.getSide(Side.BACK);
                        for (int i = 0; i < Math.min(4, signBackLines.length); i++) {
                            back.setLine(i, signBackLines[i] != null ? signBackLines[i] : "");
                        }
                    }
                    sign.setWaxed(signWaxed);
                } catch (Exception ignored) {
                    // Fallback
                    for (int i = 0; i < Math.min(4, signFrontLines.length); i++) {
                        sign.setLine(i, signFrontLines[i] != null ? signFrontLines[i] : "");
                    }
                }
                sign.update(true, false);
                return;
            }

            // ── Restore spawner config ──
            if (spawnerType != null && state instanceof CreatureSpawner spawner) {
                spawner.setSpawnedType(spawnerType);
                spawner.setDelay(spawnerDelay);
                spawner.setMinSpawnDelay(spawnerMinDelay);
                spawner.setMaxSpawnDelay(spawnerMaxDelay);
                spawner.setMaxNearbyEntities(spawnerMaxNearbyEntities);
                spawner.setSpawnCount(spawnerSpawnCount);
                spawner.setSpawnRange(spawnerSpawnRange);
                spawner.setRequiredPlayerRange(spawnerRequiredPlayerRange);
                spawner.update(true, false);
                return;
            }

            // ── Restore banner patterns ──
            if (bannerPatterns != null && state instanceof Banner banner) {
                if (bannerBase != null) banner.setBaseColor(bannerBase);
                banner.setPatterns(bannerPatterns);
                banner.update(true, false);
            }
        }
    }

    /**
     * Holds a full snapshot of an arena. Restoration runs at 50,000 blocks per
     * tick (~2.5 M blocks/sec) for near-instant regen and broadcasts progress
     * at every 25% milestone to players with the regen permission.
     */
    static final class ArenaSnapshot {
        private final List<BlockSnapshot> blocks;

        ArenaSnapshot(List<BlockSnapshot> blocks) {
            this.blocks = blocks;
        }

        int blockCount() {
            return blocks.size();
        }

        void restore(JavaPlugin plugin, ProgressCallback callback) {
            if (blocks.isEmpty()) {
                callback.onProgress(100, 0);
                return;
            }

            final int batchSize = 50_000; // ~50k blocks per tick for near-instant regen
            final int total = blocks.size();
            final int quartile = Math.max(1, total / 4);

            new org.bukkit.scheduler.BukkitRunnable() {
                private int index = 0;
                private int lastReportedQuartile = 0;

                @Override
                public void run() {
                    if (index >= total) {
                        cancel();
                        callback.onProgress(100, total);
                        return;
                    }

                    int limit = Math.min(index + batchSize, total);
                    for (int i = index; i < limit; i++) {
                        blocks.get(i).restore();
                    }
                    index = limit;

                    // Report at 25%, 50%, 75%
                    int currentQuartile = index / quartile;
                    if (currentQuartile > lastReportedQuartile && index < total) {
                        int percent = Math.min(99, (index * 100) / total);
                        callback.onProgress(percent, index);
                        lastReportedQuartile = currentQuartile;
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    @FunctionalInterface
    interface ProgressCallback {
        void onProgress(int percent, int blocksRestored);
    }

    private static final class Selection {
        private Location pos1;
        private Location pos2;

        Location pos1() { return pos1 == null ? null : pos1.clone(); }
        Location pos2() { return pos2 == null ? null : pos2.clone(); }
        void setPos1(Location location) { this.pos1 = location.clone(); }
        void setPos2(Location location) { this.pos2 = location.clone(); }
        boolean complete() { return pos1 != null && pos2 != null; }
        boolean sameWorld() { return complete() && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld()); }
        World world() { return pos1.getWorld(); }
        int width() { return Math.abs(pos1.getBlockX() - pos2.getBlockX()) + 1; }
        int depth() { return Math.abs(pos1.getBlockZ() - pos2.getBlockZ()) + 1; }
        Location min() { return new Location(world(), Math.min(pos1.getBlockX(), pos2.getBlockX()), Math.min(pos1.getBlockY(), pos2.getBlockY()), Math.min(pos1.getBlockZ(), pos2.getBlockZ())); }
        Location max() { return new Location(world(), Math.max(pos1.getBlockX(), pos2.getBlockX()), Math.max(pos1.getBlockY(), pos2.getBlockY()), Math.max(pos1.getBlockZ(), pos2.getBlockZ())); }
        Location center() { return new Location(world(), (pos1.getBlockX() + pos2.getBlockX()) / 2.0, (pos1.getBlockY() + pos2.getBlockY()) / 2.0, (pos1.getBlockZ() + pos2.getBlockZ()) / 2.0); }
        long blockCount() { return (long) width() * (Math.abs(pos1.getBlockY() - pos2.getBlockY()) + 1L) * depth(); }
    }
}
