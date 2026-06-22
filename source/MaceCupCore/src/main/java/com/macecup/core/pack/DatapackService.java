package com.macecup.core.pack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;

public final class DatapackService {
    private final JavaPlugin plugin;
    private final List<String> lastMissing = new ArrayList<>();

    public DatapackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean verifyAndInstallConfiguredWorlds() {
        lastMissing.clear();
        boolean ok = true;
        for (String world : plugin.getConfig().getStringList("datapack.worlds")) {
            if (!installIfMissing(world)) ok = false;
        }
        return ok;
    }

    public String statusLine() {
        return lastMissing.isEmpty() ? "installed" : "missing: " + String.join(",", lastMissing);
    }

    public boolean installIfMissing(String worldName) {
        File datapacks = new File(Bukkit.getWorldContainer(), worldName + File.separator + "datapacks");
        if (!datapacks.exists() && !datapacks.mkdirs()) {
            lastMissing.add(worldName);
            return false;
        }
        File target = new File(datapacks, plugin.getConfig().getString("datapack.file", "MaceCupDatapack.zip"));
        try (InputStream in = plugin.getResource("MaceCupDatapack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("Bundled MaceCupDatapack.zip missing from plugin jar.");
                lastMissing.add(worldName);
                return false;
            }
            byte[] bytes = in.readAllBytes();
            boolean write = !target.exists() || !sha1(target).equals(sha1(bytes));
            if (write) {
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(bytes);
                }
                plugin.getLogger().warning("Installed MaceCup datapack into " + target + ". Restart recommended.");
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not install datapack into " + worldName + ": " + ex.getMessage());
            lastMissing.add(worldName);
            return false;
        }
    }

    private String sha1(File file) throws Exception {
        return sha1(Files.readAllBytes(file.toPath()));
    }

    private String sha1(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
