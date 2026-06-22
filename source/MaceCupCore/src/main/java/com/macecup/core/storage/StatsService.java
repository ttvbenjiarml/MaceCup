package com.macecup.core.storage;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StatsService implements Listener {
    private final JavaPlugin plugin;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> purgePity = new ConcurrentHashMap<>();
    private File file;
    private YamlConfiguration yaml;
    private volatile long mysqlRetryAfter;

    public StatsService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        file = new File(plugin.getDataFolder(), "stats.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        loadPityFromYaml();
        testDatabaseAsync();
    }

    public void stop() {
        saveAll();
        executor.shutdownNow();
    }

    public PlayerStats stats(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadStats);
    }

    public int pity(UUID uuid) {
        return purgePity.getOrDefault(uuid, yaml.getInt("pity." + uuid, 0));
    }

    public void increasePity(UUID uuid, int steps) {
        purgePity.merge(uuid, steps, Integer::sum);
        yaml.set("pity." + uuid, pity(uuid));
        saveYamlAsync();
    }

    public void resetPity(UUID uuid) {
        purgePity.remove(uuid);
        yaml.set("pity." + uuid, null);
        saveYamlAsync();
    }

    public void saveAsync(UUID uuid) {
        PlayerStats stats = cache.get(uuid);
        if (stats == null) return;
        CompletableFuture.runAsync(() -> saveStats(stats), executor);
    }

    public void saveAll() {
        cache.values().forEach(this::saveStats);
        saveYaml();
    }

    public List<String> leaderboard(String field, int limit) {
        Comparator<PlayerStats> comparator = switch (field.toLowerCase()) {
            case "kills" -> Comparator.comparingInt(PlayerStats::kills);
            case "rating" -> Comparator.comparingInt(PlayerStats::rating);
            case "slam" -> Comparator.comparingDouble(PlayerStats::highestSlam);
            case "cashcup" -> Comparator.comparingInt(PlayerStats::cashCupPoints);
            default -> Comparator.comparingInt(PlayerStats::wins);
        };
        List<PlayerStats> rows = new ArrayList<>(cache.values());
        rows.sort(comparator.reversed());
        return rows.stream().limit(limit).map(s -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(s.uuid());
            String name = player.getName() == null ? s.uuid().toString().substring(0, 8) : player.getName();
            return name + " " + valueFor(s, field);
        }).toList();
    }

    private String valueFor(PlayerStats stats, String field) {
        return switch (field.toLowerCase()) {
            case "kills" -> String.valueOf(stats.kills());
            case "rating" -> String.valueOf(stats.rating());
            case "slam" -> String.valueOf(Math.round(stats.highestSlam()));
            case "cashcup" -> String.valueOf(stats.cashCupPoints());
            default -> String.valueOf(stats.wins());
        };
    }

    private PlayerStats loadStats(UUID uuid) {
        PlayerStats stats = new PlayerStats(uuid);
        String path = "stats." + uuid + ".";
        stats.load(
                yaml.getInt(path + "wins"),
                yaml.getInt(path + "solo-wins"),
                yaml.getInt(path + "duo-wins"),
                yaml.getInt(path + "kills"),
                yaml.getInt(path + "deaths"),
                yaml.getInt(path + "rating", 1000),
                yaml.getDouble(path + "highest-slam"),
                yaml.getInt(path + "heads"),
                yaml.getInt(path + "totem-pops"),
                yaml.getInt(path + "event-entries"),
                yaml.getInt(path + "cashcup-points")
        );
        return stats;
    }

    private void saveStats(PlayerStats stats) {
        String path = "stats." + stats.uuid() + ".";
        yaml.set(path + "wins", stats.wins());
        yaml.set(path + "solo-wins", stats.soloWins());
        yaml.set(path + "duo-wins", stats.duoWins());
        yaml.set(path + "kills", stats.kills());
        yaml.set(path + "deaths", stats.deaths());
        yaml.set(path + "rating", stats.rating());
        yaml.set(path + "highest-slam", stats.highestSlam());
        yaml.set(path + "heads", stats.heads());
        yaml.set(path + "totem-pops", stats.totemPops());
        yaml.set(path + "event-entries", stats.eventEntries());
        yaml.set(path + "cashcup-points", stats.cashCupPoints());
        saveYaml();
        trySaveStatsToMySql(stats);
    }

    private void loadPityFromYaml() {
        if (yaml.getConfigurationSection("pity") == null) return;
        for (String key : yaml.getConfigurationSection("pity").getKeys(false)) {
            try {
                purgePity.put(UUID.fromString(key), yaml.getInt("pity." + key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveYamlAsync() {
        CompletableFuture.runAsync(this::saveYaml, executor);
    }

    private synchronized void saveYaml() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save stats.yml: " + ex.getMessage());
        }
    }

    private void testDatabaseAsync() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = openConnection()) {
                if (connection != null) plugin.getLogger().info("MySQL connection OK.");
            } catch (Exception ex) {
                markMysqlUnavailable(ex, true);
            }
        }, executor);
    }

    private void trySaveStatsToMySql(PlayerStats stats) {
        try (Connection connection = openConnection()) {
            if (connection == null) return;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO stats(uuid,wins,solo_wins,duo_wins,kills,deaths,rating,highest_slam,heads,totem_pops,event_entries,cashcup_points)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE wins=?,solo_wins=?,duo_wins=?,kills=?,deaths=?,rating=?,highest_slam=?,heads=?,totem_pops=?,event_entries=?,cashcup_points=?
                    """)) {
                bindStats(statement, stats);
                statement.executeUpdate();
            }
        } catch (Exception ex) {
            markMysqlUnavailable(ex, false);
        }
    }

    private void bindStats(PreparedStatement statement, PlayerStats stats) throws Exception {
        int i = 1;
        statement.setString(i++, stats.uuid().toString());
        statement.setInt(i++, stats.wins());
        statement.setInt(i++, stats.soloWins());
        statement.setInt(i++, stats.duoWins());
        statement.setInt(i++, stats.kills());
        statement.setInt(i++, stats.deaths());
        statement.setInt(i++, stats.rating());
        statement.setDouble(i++, stats.highestSlam());
        statement.setInt(i++, stats.heads());
        statement.setInt(i++, stats.totemPops());
        statement.setInt(i++, stats.eventEntries());
        statement.setInt(i++, stats.cashCupPoints());
        statement.setInt(i++, stats.wins());
        statement.setInt(i++, stats.soloWins());
        statement.setInt(i++, stats.duoWins());
        statement.setInt(i++, stats.kills());
        statement.setInt(i++, stats.deaths());
        statement.setInt(i++, stats.rating());
        statement.setDouble(i++, stats.highestSlam());
        statement.setInt(i++, stats.heads());
        statement.setInt(i++, stats.totemPops());
        statement.setInt(i++, stats.eventEntries());
        statement.setInt(i, stats.cashCupPoints());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getName();
        savePlayerToDatabase(uuid, username);
    }

    public void savePlayerToDatabase(UUID uuid, String username) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = openConnection()) {
                if (connection == null) return;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO players(uuid, username) VALUES(?, ?)
                        ON DUPLICATE KEY UPDATE username = ?
                        """)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, username);
                    statement.setString(3, username);
                    statement.executeUpdate();
                }
            } catch (Exception ex) {
                markMysqlUnavailable(ex, false);
            }
        }, executor);
    }

    private Connection openConnection() throws Exception {
        String url = plugin.getConfig().getString("mysql.jdbc-url", "");
        if (url.isBlank()) return null;
        if (System.currentTimeMillis() < mysqlRetryAfter) return null;
        DriverManager.setLoginTimeout(Math.max(1, plugin.getConfig().getInt("mysql.login-timeout-seconds", 2)));
        return DriverManager.getConnection(url, plugin.getConfig().getString("mysql.username"), plugin.getConfig().getString("mysql.password"));
    }

    private void markMysqlUnavailable(Exception ex, boolean log) {
        int cooldownSeconds = Math.max(5, plugin.getConfig().getInt("mysql.retry-cooldown-seconds", 60));
        mysqlRetryAfter = System.currentTimeMillis() + cooldownSeconds * 1000L;
        if (log) {
            plugin.getLogger().warning("MySQL unavailable; using local async YAML persistence: " + ex.getMessage());
        }
    }
}
