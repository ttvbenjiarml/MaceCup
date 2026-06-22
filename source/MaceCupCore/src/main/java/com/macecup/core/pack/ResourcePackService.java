package com.macecup.core.pack;

import com.macecup.core.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourcePackService implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> statuses = new ConcurrentHashMap<>();
    private String installStatus = "not checked";

    public ResourcePackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void installBundledPack() {
        if (!plugin.getConfig().getBoolean("resource-pack.auto-install", true)) {
            installStatus = "auto-install disabled";
            return;
        }
        String fileName = plugin.getConfig().getString("resource-pack.file", "MaceCupResourcePack.zip");
        String exportPath = plugin.getConfig().getString("resource-pack.export-path", "resource-pack/" + fileName);
        File target = new File(plugin.getDataFolder(), exportPath);
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                installStatus = "missing bundled " + fileName;
                plugin.getLogger().warning("Bundled " + fileName + " missing from plugin jar.");
                return;
            }
            byte[] bytes = in.readAllBytes();
            target.getParentFile().mkdirs();
            boolean write = !target.exists() || !sha1(target).equals(sha1(bytes));
            if (write) {
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(bytes);
                }
            }
            String hash = sha1(bytes);
            plugin.getConfig().set("resource-pack.sha1", hash);
            plugin.saveConfig();
            if (plugin.getConfig().getBoolean("resource-pack.update-server-properties", true)) updateServerProperties(hash);
            installStatus = "installed " + target.getPath() + " sha1=" + hash;
            plugin.getLogger().info("MaceCup resource pack " + installStatus + ".");
        } catch (Exception ex) {
            installStatus = "failed: " + ex.getMessage();
            plugin.getLogger().warning("Could not auto-install resource pack: " + ex.getMessage());
        }
    }

    public String installStatus() {
        return installStatus;
    }

    public String status(Player player) {
        return statuses.getOrDefault(player.getUniqueId(), PlayerResourcePackStatusEvent.Status.ACCEPTED).name();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        statuses.put(event.getPlayer().getUniqueId(), event.getStatus());
        boolean failed = event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD;
        if (failed && plugin.getConfig().getString("resource-pack.decline-action", "KICK").equalsIgnoreCase("KICK")) {
            event.getPlayer().kickPlayer(Text.color("&cMaceCup requires the official resource pack."));
        }
    }

    public void send(Player player) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        try {
            byte[] sha1 = hex(plugin.getConfig().getString("resource-pack.sha1", ""));
            player.setResourcePack(
                    url,
                    sha1,
                    plugin.getConfig().getString("resource-pack.prompt", "MaceCup resource pack"),
                    plugin.getConfig().getBoolean("resource-pack.required", true)
            );
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Resource pack SHA1 invalid; sending URL without hash.");
            player.setResourcePack(url);
        }
    }

    private byte[] hex(String input) {
        if (input == null || input.length() != 40) throw new IllegalArgumentException("sha1 must be 40 hex chars");
        byte[] out = new byte[20];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(input.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private void updateServerProperties(String hash) throws Exception {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) {
            plugin.getLogger().warning("Resource pack exported locally, but resource-pack.url is blank. Upload the exported zip or configure a direct HTTPS URL.");
            return;
        }
        File propertiesFile = new File(Bukkit.getWorldContainer(), "server.properties");
        if (!propertiesFile.exists()) return;
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(propertiesFile)) {
            properties.load(reader);
        }
        properties.setProperty("resource-pack", url);
        properties.setProperty("resource-pack-sha1", hash);
        properties.setProperty("require-resource-pack", Boolean.toString(plugin.getConfig().getBoolean("resource-pack.required", true)));
        properties.setProperty("resource-pack-prompt", plugin.getConfig().getString("resource-pack.prompt", "MaceCup requires the official resource pack."));
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            properties.store(writer, "Minecraft server properties");
        }
    }

    private String sha1(File file) throws Exception {
        return sha1(java.nio.file.Files.readAllBytes(file.toPath()));
    }

    private String sha1(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
