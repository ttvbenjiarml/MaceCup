package com.macecup.core.cosmetic;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CosmeticService {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<CosmeticCategory, List<String>> defaults = new EnumMap<>(CosmeticCategory.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private volatile long mysqlRetryAfter;

    public CosmeticService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cosmetics.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        defaults.put(CosmeticCategory.HATS, List.of("neon-crown", "silver-cap", "void-halo"));
        defaults.put(CosmeticCategory.PARTICLE_TRAILS, List.of("electric-blue", "amethyst-spark", "silver-burst"));
        defaults.put(CosmeticCategory.EMOTES, List.of("wave", "cheer", "clap", "dance", "facepalm"));
        defaults.put(CosmeticCategory.CUSTOM_EMOTES, List.of("approved-custom"));
        defaults.put(CosmeticCategory.VICTORY_DANCES, List.of("mace-spin", "trophy-pop", "lightning-step"));
        defaults.put(CosmeticCategory.LOBBY_GADGETS, List.of("pulse-gadget", "practice-bot-egg", "token-flip"));
        defaults.put(CosmeticCategory.JOIN_MESSAGES, List.of("entered-the-cup", "joins-the-arena", "mace-ready"));
        defaults.put(CosmeticCategory.KILL_MESSAGES, List.of("slammed", "outplayed", "launched"));
        defaults.put(CosmeticCategory.NAME_COLORS, List.of("purple", "silver", "electric-blue"));
        defaults.put(CosmeticCategory.CHAT_TAGS, List.of("plus", "champion", "hosted"));
        defaults.put(CosmeticCategory.WINNER_CROWN, List.of("solo-crown", "duo-crown", "cash-cup-crown"));
        defaults.put(CosmeticCategory.TROPHY_DISPLAY, List.of("cash-cup-trophy", "macecup-token", "leaderboard-trophy"));
    }

    public List<String> available(CosmeticCategory category) {
        return defaults.getOrDefault(category, List.of());
    }

    public boolean select(UUID uuid, CosmeticCategory category, String id) {
        if (!available(category).contains(id)) return false;
        yaml.set(path(uuid, category), id);
        save();
        saveCosmeticToMySql(uuid, category.key() + ":" + id, true);
        return true;
    }

    public void clear(UUID uuid, CosmeticCategory category) {
        String currentId = selected(uuid, category);
        yaml.set(path(uuid, category), null);
        save();
        if (!currentId.equals("none")) {
            saveCosmeticToMySql(uuid, category.key() + ":" + currentId, false);
        }
    }

    public String selected(UUID uuid, CosmeticCategory category) {
        return yaml.getString(path(uuid, category), "none");
    }

    public int categoryCount() {
        return defaults.size();
    }

    private String path(UUID uuid, CosmeticCategory category) {
        return "players." + uuid + ".selected." + category.key();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save cosmetics.yml: " + ex.getMessage());
        }
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void saveCosmeticToMySql(UUID uuid, String cosmeticKey, boolean selected) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = openConnection()) {
                if (connection == null) return;
                
                if (selected) {
                    // Set all cosmetics in the same category to selected = false
                    String categoryPrefix = cosmeticKey.split(":")[0] + ":%";
                    try (PreparedStatement unsetStmt = connection.prepareStatement("""
                            UPDATE cosmetics SET selected = FALSE WHERE uuid = ? AND cosmetic_key LIKE ?
                            """)) {
                        unsetStmt.setString(1, uuid.toString());
                        unsetStmt.setString(2, categoryPrefix);
                        unsetStmt.executeUpdate();
                    }
                    
                    // Insert or update this cosmetic to selected = true
                    try (PreparedStatement setStmt = connection.prepareStatement("""
                            INSERT INTO cosmetics(uuid, cosmetic_key, unlocked, selected) VALUES(?, ?, TRUE, TRUE)
                            ON DUPLICATE KEY UPDATE selected = TRUE
                            """)) {
                        setStmt.setString(1, uuid.toString());
                        setStmt.setString(2, cosmeticKey);
                        setStmt.executeUpdate();
                    }
                } else {
                    // Set this cosmetic to selected = false
                    try (PreparedStatement setStmt = connection.prepareStatement("""
                            UPDATE cosmetics SET selected = FALSE WHERE uuid = ? AND cosmetic_key = ?
                            """)) {
                        setStmt.setString(1, uuid.toString());
                        setStmt.setString(2, cosmeticKey);
                        setStmt.executeUpdate();
                    }
                }
            } catch (Exception ex) {
                markMysqlUnavailable();
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

    private void markMysqlUnavailable() {
        int cooldownSeconds = Math.max(5, plugin.getConfig().getInt("mysql.retry-cooldown-seconds", 60));
        mysqlRetryAfter = System.currentTimeMillis() + cooldownSeconds * 1000L;
    }
}
