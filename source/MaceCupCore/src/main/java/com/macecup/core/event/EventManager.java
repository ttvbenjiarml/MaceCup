package com.macecup.core.event;

import com.macecup.core.ServerRole;
import com.macecup.core.Text;
import com.macecup.core.arena.ArenaConfig;
import com.macecup.core.arena.ArenaManager;
import com.macecup.core.gui.GuiManager;
import com.macecup.core.storage.PlayerStats;
import com.macecup.core.storage.RedisStateService;
import com.macecup.core.storage.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class EventManager implements Listener {
    private final JavaPlugin plugin;
    private final ServerRole role;
    private final StatsService statsService;
    private final RedisStateService redisStateService;
    private final ArenaManager arenaManager;
    private final GuiManager guiManager;
    private final Set<UUID> selectedPlayers = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final List<String> eventServers;
    private BossBar bossBar;
    private BukkitTask ticker;
    private EventState state = EventState.WAITING;
    private EventMode mode;
    private ArenaConfig activeArena;
    private long startedAt;
    private long lastPendingEventPoll;
    private int lastCountdownSecond = -1;
    private int countdownSeconds;

    public EventManager(JavaPlugin plugin, ServerRole role, StatsService statsService, RedisStateService redisStateService, ArenaManager arenaManager, GuiManager guiManager) {
        this.plugin = plugin;
        this.role = role;
        this.statsService = statsService;
        this.redisStateService = redisStateService;
        this.arenaManager = arenaManager;
        this.guiManager = guiManager;
        this.eventServers = plugin.getConfig().getStringList("events.event-servers");
    }

    public void start() {
        redisStateService.publishServerState(serverName(), state.name());
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) ticker.cancel();
        if (bossBar != null) bossBar.removeAll();
        stopBorderInstantly();
        selectedPlayers.clear();
        alivePlayers.clear();
        state = EventState.WAITING;
        redisStateService.publishServerState(serverName(), state.name());
    }

    public EventState state() { return state; }
    public EventMode mode() { return mode; }
    public String arenaName() { return activeArena == null ? "none" : activeArena.name(); }
    public int aliveCount() { return alivePlayers.size(); }
    public Set<UUID> selectedPlayers() { return Set.copyOf(selectedPlayers); }

    public boolean host(Player host, EventMode requestedMode, String arenaName) {
        if (role != ServerRole.LOBBY_PRACTICE) {
            host.sendMessage(Text.color("&cEvents must be hosted from lobby-practice."));
            return true;
        }
        if (state != EventState.WAITING) {
            host.sendMessage(Text.color("&cAn event is already " + state + "."));
            return true;
        }
        ArenaConfig arena = arenaManager.arena(arenaName);
        if (arena == null) {
            host.sendMessage(Text.color("&cUnknown arena: " + arenaName));
            return true;
        }
        List<Player> eligible = new ArrayList<>(Bukkit.getOnlinePlayers().stream().filter(p -> !p.hasPermission("macecup.bypass") || p.hasPermission("macecup.event.guaranteed")).toList());
        List<Player> guaranteed = eligible.stream().filter(p -> p.hasPermission("macecup.purge.guaranteed") || p.hasPermission("macecup.event.guaranteed")).toList();
        if (guaranteed.size() > requestedMode.maxPlayers()) {
            host.sendMessage(Text.color("&cBLOCK_START: more than " + requestedMode.maxPlayers() + " guaranteed players are eligible."));
            return true;
        }
        List<Player> selected = selectPlayers(eligible, guaranteed, requestedMode.maxPlayers());
        String target = leastRecentlyUsedEventServer();
        if (target == null) {
            host.sendMessage(Text.color("&cAll event servers are busy."));
            return true;
        }
        this.state = selected.size() < eligible.size() ? EventState.PURGE : EventState.COUNTDOWN;
        this.mode = requestedMode;
        this.activeArena = arena;
        this.selectedPlayers.clear();
        selected.forEach(p -> selectedPlayers.add(p.getUniqueId()));
        redisStateService.publishEventStart(target, requestedMode.name(), arena.name(), selectedPlayers);
        redisStateService.publishServerState(target, "RESERVED");
        redisStateService.publishServerState(serverName(), state.name());
        for (Player player : eligible) {
            if (selectedPlayers.contains(player.getUniqueId())) {
                statsService.resetPity(player.getUniqueId());
                statsService.stats(player.getUniqueId()).addEntry();
                statsService.saveAsync(player.getUniqueId());
                transfer(player, target);
            } else {
                statsService.increasePity(player.getUniqueId(), 1);
                player.sendMessage(Text.color("&eYou were not selected this time. Your purge pity increased."));
            }
        }
        Bukkit.broadcastMessage(Text.color("&d&lMaceCup &8» &fHosted &b" + requestedMode.displayName() + " &fon &b" + arena.name() + "&f. Selected &b" + selected.size() + "&f players."));
        return true;
    }

    public boolean startLocalEvent(EventMode requestedMode, String arenaName, List<UUID> selected) {
        ArenaConfig arena = arenaManager.arena(arenaName);
        if (arena == null || role != ServerRole.EVENT || state != EventState.WAITING) return false;
        this.mode = requestedMode;
        this.activeArena = arena;
        this.selectedPlayers.clear();
        this.selectedPlayers.addAll(selected);
        this.alivePlayers.clear();
        this.alivePlayers.addAll(selected);
        startCountdown();
        return true;
    }

    public void cancel() {
        if (state == EventState.WAITING) return;
        Bukkit.broadcastMessage(Text.color("&cMaceCup event cancelled."));
        cleanupAndReset();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (role == ServerRole.EVENT && state != EventState.WAITING && !selectedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.getPlayer().kickPlayer(Text.color("&cThis event server is locked for hosted MaceCup players."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        alivePlayers.remove(event.getPlayer().getUniqueId());
        maybeFinish();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        alivePlayers.remove(player.getUniqueId());
        statsService.stats(player.getUniqueId()).addDeath();
        statsService.saveAsync(player.getUniqueId());
        Player killer = player.getKiller();
        if (killer != null) {
            statsService.stats(killer.getUniqueId()).addKill();
            statsService.saveAsync(killer.getUniqueId());
        }
        maybeFinish();
    }

    @EventHandler
    public void onSlam(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        double fall = Math.max(0, attacker.getFallDistance());
        if (fall < 20) return;
        PlayerStats stats = statsService.stats(attacker.getUniqueId());
        stats.recordSlam(fall);
        if (mode == EventMode.CASHCUP_SOLO || mode == EventMode.CASHCUP_DUO) stats.addHead();
        statsService.saveAsync(attacker.getUniqueId());
        attacker.sendMessage(Text.color("&bSlam height: &f" + Math.round(fall) + " blocks"));
        if (fall >= 50) Bukkit.broadcastMessage(Text.color("&d&lMaceCup &8» &f" + attacker.getName() + " hit a &b" + Math.round(fall) + "&f block slam on " + victim.getName() + "!"));
    }

    private void tick() {
        if (role == ServerRole.LOBBY_PRACTICE && state == EventState.WAITING) {
            showEntryChanceActionBars();
        }
        if (role == ServerRole.EVENT && state == EventState.WAITING) {
            pollPendingEvent();
        }
        if (state == EventState.RUNNING && activeArena != null) {
            long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
            int borderSeconds = plugin.getConfig().getInt("events.border-minutes", 28) * 60;
            if (elapsed >= borderSeconds && state != EventState.FINAL_CIRCLE) state = EventState.FINAL_CIRCLE;
            if (bossBar != null) {
                bossBar.setTitle(Text.color("&c&lMaceCup &8| &fAlive: &c" + alivePlayers.size() + " &8| &fBorder closing: &c" + Math.max(0, borderSeconds - elapsed) + "s"));
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, 1.0 - (double) elapsed / borderSeconds)));
            }
            maybeFinish();
        } else if (state == EventState.COUNTDOWN) {
            showCountdown();
            countdownSeconds--;
            if (countdownSeconds <= 0) begin();
        }
    }

    private void pollPendingEvent() {
        long now = System.currentTimeMillis();
        if (now - lastPendingEventPoll < 2000L) return;
        lastPendingEventPoll = now;
        String key = "macecup:event:" + serverName() + ":payload";
        String payload = redisStateService.get(key);
        if (payload == null || payload.isBlank()) return;
        PendingEvent pending = PendingEvent.parse(payload);
        if (pending == null) {
            plugin.getLogger().warning("Ignoring invalid pending event payload: " + payload);
            redisStateService.deleteAsync(key);
            return;
        }
        if (startLocalEvent(pending.mode(), pending.arenaName(), pending.selectedPlayers())) {
            redisStateService.deleteAsync(key);
            redisStateService.deleteAsync("macecup:event:" + serverName() + ":selected");
            plugin.getLogger().info("Started pending " + pending.mode().displayName() + " event on arena " + pending.arenaName() + " for " + pending.selectedPlayers().size() + " players.");
        }
    }

    private void startCountdown() {
        state = EventState.COUNTDOWN;
        countdownSeconds = Math.max(5, plugin.getConfig().getInt("events.countdown-seconds", 30));
        lastCountdownSecond = -1;
        redisStateService.publishServerState(serverName(), state.name());
        Bukkit.broadcastMessage(Text.color("&d&lMaceCup &8» &fCountdown started for &b" + mode.displayName() + "&f."));
        for (UUID uuid : selectedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.9F, 1.0F);
            }
        }
        showCountdown();
    }

    private void begin() {
        if (activeArena == null || activeArena.world() == null) {
            cleanupAndReset();
            return;
        }
        state = EventState.RUNNING;
        startedAt = System.currentTimeMillis();
        World world = activeArena.world();
        world.getWorldBorder().setCenter(activeArena.center());
        world.getWorldBorder().setSize(activeArena.borderSize());
        world.getWorldBorder().setWarningDistance(Math.max(24, (int) activeArena.borderSize()));
        world.getWorldBorder().setWarningTime(Math.max(15, plugin.getConfig().getInt("events.border-warning-seconds", 30)));
        world.getWorldBorder().setSize(plugin.getConfig().getDouble("events.final-border-size", 80.0), plugin.getConfig().getInt("events.border-minutes", 28) * 60L);
        arenaManager.fillLoot(activeArena);
        bossBar = Bukkit.createBossBar(Text.color("&c&lMaceCup Event"), BarColor.RED, BarStyle.SEGMENTED_10);
        int index = 0;
        List<Location> spawns = activeArena.spawns();
        for (UUID uuid : selectedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            alivePlayers.add(uuid);
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.getInventory().addItem(new ItemStack(Material.MACE), new ItemStack(Material.WIND_CHARGE, 8), new ItemStack(Material.COOKED_BEEF, 16));
            Location spawn = spawns.isEmpty() ? fallbackSpawn(activeArena, index) : spawns.get(index % spawns.size());
            player.teleport(spawn);
            bossBar.addPlayer(player);
            player.sendTitle(Text.color("&c&lFIGHT"), Text.color("&f" + mode.displayName() + " has started"), 5, 35, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7F, 1.35F);
            index++;
        }
        redisStateService.publishServerState(serverName(), state.name());
        Bukkit.broadcastMessage(Text.color("&d&lMaceCup &8» &f" + mode.displayName() + " running. " + (mode.duo() ? "Last duo wins." : "Last player wins.")));
    }

    private void maybeFinish() {
        if (state != EventState.RUNNING && state != EventState.FINAL_CIRCLE) return;
        if (alivePlayers.size() > (mode != null && mode.duo() ? 2 : 1)) return;
        finish();
    }

    private void finish() {
        state = EventState.ENDING;
        redisStateService.publishServerState(serverName(), state.name());
        List<UUID> winners = new ArrayList<>(alivePlayers);
        for (UUID winner : winners) {
            PlayerStats stats = statsService.stats(winner);
            stats.addWin(mode != null && mode.duo(), mode == EventMode.CASHCUP_SOLO || mode == EventMode.CASHCUP_DUO);
            statsService.saveAsync(winner);
        }
        if (activeArena != null && activeArena.world() != null) {
            World world = activeArena.world();
            world.getWorldBorder().setSize(world.getWorldBorder().getSize());
        }
        celebrateWinners(winners);
        Bukkit.broadcastMessage(Text.color("&d&lMaceCup &8» &fEvent complete. Winner" + (winners.size() == 1 ? "" : "s") + ": &b" + winnerNames(winners)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupAndReset(), 100L);
    }

    private void cleanupAndReset() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        for (UUID uuid : selectedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().clear();
                transfer(player, "lobby-practice");
            }
        }
        stopBorderInstantly();
        // Arena regeneration is intentionally NOT done here.
        // Use /mace regenarena <arena|all> (requires macecup.arena.regen) to restore arenas manually.
        selectedPlayers.clear();
        alivePlayers.clear();
        mode = null;
        activeArena = null;
        state = EventState.WAITING;
        redisStateService.publishServerState(serverName(), state.name());
    }

    private List<Player> selectPlayers(List<Player> eligible, List<Player> guaranteed, int cap) {
        if (eligible.size() <= cap) return eligible;
        List<Player> pool = new ArrayList<>(eligible);
        pool.removeAll(guaranteed);
        pool.sort(Comparator.comparingDouble(this::purgeWeight).reversed());
        List<Player> out = new ArrayList<>(guaranteed);
        for (Player player : pool) if (out.size() < cap) out.add(player);
        return out;
    }

    private Location fallbackSpawn(ArenaConfig arena, int index) {
        Location center = arena.center();
        double radius = Math.max(8.0, Math.min(48.0, arena.borderSize() / 3.0));
        double angle = Math.toRadians((index % Math.max(1, mode == null ? 100 : mode.maxPlayers())) * 137.5);
        return center.clone().add(Math.cos(angle) * radius, 24, Math.sin(angle) * radius);
    }

    private double purgeWeight(Player player) {
        return entryChance(player) + Math.random() * 0.02;
    }

    private double entryChance(Player player) {
        if (player.hasPermission("macecup.purge.guaranteed") || player.hasPermission("macecup.event.guaranteed")) return 1.0;
        double normalStep = Math.max(0.01, plugin.getConfig().getDouble("purge.normal-pity-step", 0.25));
        double vipStep = Math.max(normalStep, plugin.getConfig().getDouble("purge.vip-pity-step", 0.375));
        double step = (player.hasPermission("macecup.purge.vip") || player.hasPermission("macecup.purge.boost.50")) ? vipStep : normalStep;
        return Math.min(1.0, (statsService.pity(player.getUniqueId()) + 1) * step);
    }

    private void showEntryChanceActionBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("macecup.bypass") && !player.hasPermission("macecup.event.guaranteed")) continue;
            int chance = (int) Math.round(entryChance(player) * 100.0);
            String rank;
            if (player.hasPermission("macecup.purge.guaranteed") || player.hasPermission("macecup.event.guaranteed")) {
                rank = "&e&lVIP+";
            } else if (player.hasPermission("macecup.purge.vip") || player.hasPermission("macecup.purge.boost.50")) {
                rank = "&b&lVIP";
            } else {
                rank = "&7&lPLAYER";
            }
            String chanceColor = chance >= 100 ? "&a&l" : chance >= 75 ? "&2&l" : chance >= 50 ? "&e&l" : "&c&l";
            player.sendActionBar(Text.color("&d\uE009 &fEntry Chance: " + chanceColor + chance + "% &8| &fRank: &d\uE005 " + rank));
        }
    }

    private void showCountdown() {
        if (countdownSeconds == lastCountdownSecond) return;
        lastCountdownSecond = countdownSeconds;
        String title = countdownSeconds <= 5 ? "&c&l" + countdownSeconds : "&d&l" + mode.promptName();
        String subtitle = countdownSeconds <= 5 ? "&fGet ready" : "&fStarting in &b" + countdownSeconds + "s";
        Sound sound = countdownSeconds <= 5 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_HAT;
        float pitch = countdownSeconds <= 5 ? Math.max(0.7F, 2.0F - countdownSeconds * 0.15F) : 1.0F;
        for (UUID uuid : selectedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.sendTitle(Text.color(title), Text.color(subtitle), 0, 22, 4);
            player.playSound(player.getLocation(), sound, 0.85F, pitch);
        }
    }

    private void stopBorderInstantly() {
        if (activeArena == null || activeArena.world() == null) return;
        World world = activeArena.world();
        world.getWorldBorder().setCenter(activeArena.center());
        world.getWorldBorder().setSize(activeArena.borderSize());
        world.getWorldBorder().setWarningDistance(32);
        world.getWorldBorder().setWarningTime(15);
    }

    private void celebrateWinners(List<UUID> winners) {
        for (UUID uuid : winners) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.sendTitle(Text.color("&6&lVICTORY"), Text.color("&fYou won " + (mode == null ? "the event" : mode.displayName()) + "&f!"), 5, 55, 15);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            for (int i = 0; i < 4; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnWinnerFirework(player.getLocation()), i * 12L);
            }
        }
    }

    private void spawnWinnerFirework(Location location) {
        Firework firework = location.getWorld().spawn(location.clone().add(0, 1.0, 0), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.FUCHSIA, Color.AQUA)
                .withFade(Color.WHITE)
                .trail(true)
                .flicker(true)
                .build());
        firework.setFireworkMeta(meta);
    }

    private String leastRecentlyUsedEventServer() {
        if (eventServers.isEmpty()) return null;
        return eventServers.stream()
                .filter(name -> {
                    String remoteState = redisStateService.get("macecup:server:" + name + ":state");
                    return remoteState == null || remoteState.equalsIgnoreCase("WAITING");
                })
                .min(Comparator.comparingLong(name -> 0L))
                .orElse(eventServers.get(0));
    }

    private void transfer(Player player, String server) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            player.sendMessage(Text.color("&aSending you to &f" + server + "&a."));
        } catch (Exception ex) {
            player.sendMessage(Text.color("&cCould not transfer automatically: " + ex.getMessage()));
        }
    }

    private String winnerName(UUID winner) {
        if (winner == null) return "none";
        Player player = Bukkit.getPlayer(winner);
        return player == null ? winner.toString().substring(0, 8) : player.getName();
    }

    private String winnerNames(List<UUID> winners) {
        if (winners.isEmpty()) return "none";
        return String.join(", ", winners.stream().map(this::winnerName).toList());
    }

    private String serverName() {
        return plugin.getConfig().getString("server-name", plugin.getServer().getName()).toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private record PendingEvent(EventMode mode, String arenaName, List<UUID> selectedPlayers) {
        static PendingEvent parse(String payload) {
            if (payload.length() > 5000) return null;
            String[] parts = payload.split("\\|", 3);
            if (parts.length != 3) return null;
            if (!parts[1].matches("[A-Za-z0-9_-]{1,48}")) return null;
            EventMode mode;
            try {
                mode = EventMode.valueOf(parts[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
            List<UUID> selected = new ArrayList<>();
            if (!parts[2].isBlank()) {
                for (String raw : parts[2].split(",")) {
                    try {
                        selected.add(UUID.fromString(raw.trim()));
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }
            return new PendingEvent(mode, parts[1], selected);
        }
    }
}
