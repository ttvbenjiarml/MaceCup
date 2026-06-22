package com.macecup.core.cosmetic;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CustomEmoteService {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private volatile long mysqlRetryAfter;

    public CustomEmoteService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-emotes.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public CreateResult create(UUID uuid, String name, String body) {
        String key = normalize(name);
        if (!key.matches("[a-z0-9_-]{2,24}")) return CreateResult.INVALID_NAME;
        String cleanBody = sanitizeBody(body);
        if (cleanBody.isBlank() || cleanBody.length() > plugin.getConfig().getInt("custom-emotes.max-length", 96)) return CreateResult.INVALID_BODY;
        if (containsBlacklisted(cleanBody)) return CreateResult.BLACKLISTED;
        yaml.set(path(uuid, key) + ".body", cleanBody);
        boolean approved = !plugin.getConfig().getBoolean("custom-emotes.require-approval", true);
        yaml.set(path(uuid, key) + ".approved", approved);
        save();
        saveEmoteToMySql(uuid, key, cleanBody, approved);
        return CreateResult.CREATED;
    }

    public boolean approve(UUID uuid, String name) {
        String key = normalize(name);
        if (!yaml.contains(path(uuid, key))) return false;
        yaml.set(path(uuid, key) + ".approved", true);
        save();
        approveEmoteInMySql(uuid, key);
        return true;
    }

    public UseResult use(UUID uuid, String name) {
        String key = normalize(name);
        String base = path(uuid, key);
        if (!yaml.contains(base)) return UseResult.NOT_FOUND;
        if (!yaml.getBoolean(base + ".approved")) return UseResult.NOT_APPROVED;
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("custom-emotes.cooldown-seconds", 8L) * 1000L;
        long last = cooldowns.getOrDefault(uuid, 0L);
        if (now - last < cooldownMs) return UseResult.COOLDOWN;
        cooldowns.put(uuid, now);
        return UseResult.USED;
    }

    public String body(UUID uuid, String name) {
        return yaml.getString(path(uuid, normalize(name)) + ".body", "");
    }

    public List<String> names(UUID uuid) {
        if (yaml.getConfigurationSection("players." + uuid) == null) return List.of();
        return yaml.getConfigurationSection("players." + uuid).getKeys(false).stream().sorted().toList();
    }

    public int count(UUID uuid) {
        return names(uuid).size();
    }

    public boolean containsBlacklisted(String body) {
        String lowered = body.toLowerCase(Locale.ROOT);
        for (String word : plugin.getConfig().getStringList("custom-emotes.blacklist")) {
            if (!word.isBlank() && lowered.contains(word.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private String path(UUID uuid, String key) {
        return "players." + uuid + "." + key;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save custom-emotes.yml: " + ex.getMessage());
        }
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void saveEmoteToMySql(UUID uuid, String name, String body, boolean approved) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = openConnection()) {
                if (connection == null) return;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO custom_emotes(uuid, name, body, approved) VALUES(?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE body = ?, approved = ?
                        """)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, name);
                    statement.setString(3, body);
                    statement.setBoolean(4, approved);
                    statement.setString(5, body);
                    statement.setBoolean(6, approved);
                    statement.executeUpdate();
                }
            } catch (Exception ex) {
                markMysqlUnavailable();
            }
        }, executor);
    }

    private void approveEmoteInMySql(UUID uuid, String name) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = openConnection()) {
                if (connection == null) return;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE custom_emotes SET approved = TRUE WHERE uuid = ? AND name = ?
                        """)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, name);
                    statement.executeUpdate();
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

    private String sanitizeBody(String body) {
        if (body == null) return "";
        return body.replaceAll("(?i)&[0-9A-FK-ORX]", "")
                .replaceAll("[\\r\\n\\t]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private void markMysqlUnavailable() {
        int cooldownSeconds = Math.max(5, plugin.getConfig().getInt("mysql.retry-cooldown-seconds", 60));
        mysqlRetryAfter = System.currentTimeMillis() + cooldownSeconds * 1000L;
    }

    public enum CreateResult {
        CREATED,
        INVALID_NAME,
        INVALID_BODY,
        BLACKLISTED
    }

    public enum UseResult {
        USED,
        NOT_FOUND,
        NOT_APPROVED,
        COOLDOWN
    }
}
