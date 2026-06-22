package com.macecup.core.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RedisStateService {
    private final JavaPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean available;

    public RedisStateService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        CompletableFuture.runAsync(() -> {
            try {
                command("PING");
                available = true;
                plugin.getLogger().info("Redis connection OK.");
            } catch (Exception ex) {
                available = false;
                plugin.getLogger().warning("Redis unavailable; live network state will stay local only: " + ex.getMessage());
            }
        }, executor);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public boolean available() {
        return available;
    }

    public void publishServerState(String serverName, String state) {
        setAsync("macecup:server:" + serverName + ":state", state);
    }

    public void publishEventSelection(String serverName, Collection<UUID> selectedPlayers) {
        StringBuilder value = new StringBuilder();
        for (UUID uuid : selectedPlayers) {
            if (!value.isEmpty()) value.append(',');
            value.append(uuid);
        }
        setAsync("macecup:event:" + serverName + ":selected", value.toString());
    }

    public void publishEventStart(String serverName, String mode, String arenaName, Collection<UUID> selectedPlayers) {
        StringBuilder value = new StringBuilder(mode).append('|').append(arenaName).append('|');
        for (UUID uuid : selectedPlayers) {
            if (value.charAt(value.length() - 1) != '|') value.append(',');
            value.append(uuid);
        }
        setAsync("macecup:event:" + serverName + ":payload", value.toString());
        publishEventSelection(serverName, selectedPlayers);
    }

    public void setAsync(String key, String value) {
        CompletableFuture.runAsync(() -> {
            try {
                command("SET", key, value);
            } catch (Exception ex) {
                available = false;
            }
        }, executor);
    }

    public String get(String key) {
        try {
            return command("GET", key);
        } catch (Exception ex) {
            available = false;
            return null;
        }
    }

    public void deleteAsync(String key) {
        CompletableFuture.runAsync(() -> {
            try {
                command("DEL", key);
            } catch (Exception ex) {
                available = false;
            }
        }, executor);
    }

    private String command(String... parts) throws Exception {
        String host = plugin.getConfig().getString("redis.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("redis.port", 6379);
        int timeout = Math.max(250, plugin.getConfig().getInt("redis.timeout-millis", 1000));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);
            try (
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {
                out.write(encode(parts));
                out.flush();
                return readReply(in);
            }
        }
    }

    private byte[] encode(String... parts) {
        StringBuilder builder = new StringBuilder("*").append(parts.length).append("\r\n");
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            builder.append('$').append(bytes.length).append("\r\n").append(part).append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String readReply(BufferedInputStream in) throws Exception {
        int type = in.read();
        if (type == -1) throw new IllegalStateException("empty redis reply");
        String line = readLine(in);
        if (type == '+' || type == ':') return line;
        if (type == '-') throw new IllegalStateException(line);
        if (type == '$') {
            int len = Integer.parseInt(line);
            if (len < 0) return null;
            byte[] bytes = in.readNBytes(len);
            in.readNBytes(2);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return line;
    }

    private String readLine(BufferedInputStream in) throws Exception {
        StringBuilder builder = new StringBuilder();
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                builder.setLength(builder.length() - 1);
                break;
            }
            builder.append((char) current);
            previous = current;
        }
        return builder.toString();
    }
}
