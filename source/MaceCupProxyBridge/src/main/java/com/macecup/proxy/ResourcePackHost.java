package com.macecup.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

final class ResourcePackHost {
    private static final String PACK_FILE = "MaceCupResourcePack.zip";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("macecup-resource-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private HttpServer server;
    private byte[] packBytes;
    private byte[] sha1;
    private Config config;

    ResourcePackHost(ProxyServer proxy, Logger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    void start() {
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            config = Config.load(configPath);
            if (!config.enabled) {
                logger.info("MaceCup resource pack proxy hosting disabled.");
                return;
            }
            installBundledPack();
            if (config.hostEnabled) startHttpServer();
            logger.info("MaceCup resource pack ready. url={} sha1={}", configuredUrl(null), HexFormat.of().formatHex(sha1));
        } catch (Exception ex) {
            logger.error("Could not start MaceCup resource pack host", ex);
        }
    }

    void send(Player player) {
        if (config == null || !config.enabled || packBytes == null) return;
        String url = configuredUrl(player);
        if (url.isBlank()) {
            logger.warn("No resource pack URL available for {}. Set resource-pack.public-url in plugins/MaceCupProxyBridge/config.yml.", player.getUsername());
            return;
        }
        ResourcePackInfo pack = proxy.createResourcePackBuilder(url)
                .setId(PACK_ID)
                .setHash(sha1)
                .setPrompt(Component.text(config.prompt))
                .setShouldForce(config.required)
                .build();
        player.sendResourcePackOffer(pack);
    }

    void stop() {
        if (server != null) server.stop(0);
    }

    HttpServer server() {
        return server;
    }

    Config config() {
        return config;
    }

    private void installBundledPack() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(PACK_FILE)) {
            if (in == null) throw new IOException("Bundled " + PACK_FILE + " missing from proxy jar");
            packBytes = in.readAllBytes();
        }
        sha1 = MessageDigest.getInstance("SHA-1").digest(packBytes);
        Path target = dataDirectory.resolve("resource-pack").resolve(PACK_FILE);
        Files.createDirectories(target.getParent());
        if (!Files.exists(target) || !MessageDigest.isEqual(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(target)), sha1)) {
            Files.write(target, packBytes);
        }
        Files.writeString(dataDirectory.resolve("resource-pack").resolve("SHA1.txt"), HexFormat.of().formatHex(sha1));
    }

    private void startHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.bindAddress, config.port), 0);
        server.createContext(config.path, this::servePack);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
    }

    private void servePack(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!List.of("GET", "HEAD").contains(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + PACK_FILE + "\"");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
            exchange.getResponseHeaders().set("ETag", "\"" + HexFormat.of().formatHex(sha1) + "\"");
            exchange.sendResponseHeaders(200, exchange.getRequestMethod().equals("HEAD") ? -1 : packBytes.length);
            if (exchange.getRequestMethod().equals("GET")) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(packBytes);
                }
            }
        }
    }

    private String configuredUrl(Player player) {
        if (!config.publicUrl.isBlank()) return config.publicUrl;
        String host = config.publicHost;
        if (host.isBlank() && player != null && player.getVirtualHost().isPresent()) {
            host = player.getVirtualHost().get().getHostString();
        }
        if (host.isBlank()) return "";
        return "http://" + host + ":" + config.port + config.path;
    }

    record Config(
            boolean enabled,
            boolean hostEnabled,
            String bindAddress,
            int port,
            String path,
            String publicHost,
            String publicUrl,
            boolean required,
            String prompt,
            String jdbcUrl,
            String mysqlUsername,
            String mysqlPassword,
            String region,
            boolean geoipRoutingEnabled,
            String naAddress,
            int naPort,
            String euAddress,
            int euPort
    ) {
        static Config load(Path path) throws IOException {
            if (!Files.exists(path)) {
                Files.writeString(path, """
                        region: NA
                        lobby-server: lobby-practice
                        event-servers: [event-1]
                        protected-event-servers: true
                        resource-pack:
                          enabled: true
                          host-enabled: true
                          bind-address: 0.0.0.0
                          port: 24454
                          path: /resource-pack/MaceCupResourcePack.zip
                          public-host: ''
                          public-url: ''
                          required: true
                          prompt: MaceCup requires the official resource pack.
                        mysql:
                          jdbc-url: 'jdbc:mysql://127.0.0.1:3306/macecup?useSSL=false&allowPublicKeyRetrieval=true'
                          username: 'macecup'
                          password: ''
                        proxies:
                          geoip-routing-enabled: false
                          na-address: 'na.macecup.xyz'
                          na-port: 25565
                          eu-address: 'eu.macecup.xyz'
                          eu-port: 25565
                        """);
            }
            boolean inPack = false;
            boolean inMysql = false;
            boolean inProxies = false;
            boolean enabled = true;
            boolean hostEnabled = true;
            String bindAddress = "0.0.0.0";
            int port = 8080;
            String packPath = "/resource-pack/MaceCupResourcePack.zip";
            String publicHost = "";
            String publicUrl = "";
            boolean required = true;
            String prompt = "MaceCup requires the official resource pack.";
            String jdbcUrl = "";
            String mysqlUsername = "";
            String mysqlPassword = "";
            String region = "NA";
            boolean geoipRoutingEnabled = false;
            String naAddress = "na.macecup.xyz";
            int naPort = 25565;
            String euAddress = "eu.macecup.xyz";
            int euPort = 25565;
            for (String raw : Files.readAllLines(path)) {
                String line = raw.stripTrailing();
                if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
                if (!raw.startsWith(" ") && line.endsWith(":")) {
                    inPack = line.equals("resource-pack:");
                    inMysql = line.equals("mysql:");
                    inProxies = line.equals("proxies:");
                    continue;
                }
                if (!raw.startsWith(" ")) {
                    int split = line.indexOf(':');
                    if (split >= 0) {
                        String key = line.substring(0, split).trim();
                        String value = unquote(line.substring(split + 1).trim());
                        if (key.equals("region")) {
                            region = value;
                        }
                    }
                    continue;
                }
                String trimmed = line.strip();
                int split = trimmed.indexOf(':');
                if (split < 0) continue;
                String key = trimmed.substring(0, split).trim();
                String value = unquote(trimmed.substring(split + 1).trim());
                if (inPack) {
                    switch (key) {
                        case "enabled" -> enabled = Boolean.parseBoolean(value);
                        case "host-enabled" -> hostEnabled = Boolean.parseBoolean(value);
                        case "bind-address" -> bindAddress = value;
                        case "port" -> port = Integer.parseInt(value);
                        case "path" -> packPath = value.startsWith("/") ? value : "/" + value;
                        case "public-host" -> publicHost = value;
                        case "public-url" -> publicUrl = value;
                        case "required" -> required = Boolean.parseBoolean(value);
                        case "prompt" -> prompt = value;
                        default -> { }
                    }
                } else if (inMysql) {
                    switch (key) {
                        case "jdbc-url" -> jdbcUrl = value;
                        case "username" -> mysqlUsername = value;
                        case "password" -> mysqlPassword = value;
                        default -> { }
                    }
                } else if (inProxies) {
                    switch (key) {
                        case "geoip-routing-enabled" -> geoipRoutingEnabled = Boolean.parseBoolean(value);
                        case "na-address" -> naAddress = value;
                        case "na-port" -> naPort = Integer.parseInt(value);
                        case "eu-address" -> euAddress = value;
                        case "eu-port" -> euPort = Integer.parseInt(value);
                        default -> { }
                    }
                }
            }
            return new Config(enabled, hostEnabled, bindAddress, port, packPath, publicHost, publicUrl, required, prompt, jdbcUrl, mysqlUsername, mysqlPassword, region, geoipRoutingEnabled, naAddress, naPort, euAddress, euPort);
        }

        private static String unquote(String value) {
            if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
