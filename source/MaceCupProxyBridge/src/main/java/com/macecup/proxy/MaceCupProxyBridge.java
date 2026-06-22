package com.macecup.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;
import java.net.URI;
import java.util.regex.Pattern;

@Plugin(id = "macecupproxybridge", name = "MaceCupProxyBridge", version = "1.0.0", authors = {"MaceCup"})
public final class MaceCupProxyBridge {
    private static final Pattern SAFE_HOST = Pattern.compile("^[A-Za-z0-9.-]{1,253}$");
    private final ProxyServer proxy;
    private final Logger logger;
    private final ResourcePackHost resourcePackHost;
    private final String lobby = "lobby-practice";
    private final List<String> eventServers = List.of("event-1");
    private final Map<String, ServerState> states = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUse = new ConcurrentHashMap<>();
    private final Map<UUID, String> expectedEventTarget = new ConcurrentHashMap<>();
    private HttpApiServer apiServer;

    @Inject
    public MaceCupProxyBridge(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.resourcePackHost = new ResourcePackHost(proxy, logger, dataDirectory);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        for (String server : eventServers) {
            states.put(server, ServerState.WAITING);
            lastUse.put(server, 0L);
        }
        states.put(lobby, ServerState.LOBBY);
        resourcePackHost.start();

        if (resourcePackHost.server() != null) {
            apiServer = new HttpApiServer(proxy, logger, states);
            var dbConfig = resourcePackHost.config();
            apiServer.configureDatabase(dbConfig.jdbcUrl(), dbConfig.mysqlUsername(), dbConfig.mysqlPassword());
            apiServer.registerRoutes(resourcePackHost.server());
            logger.info("MaceCup HTTP API Server routes registered.");
        }

        proxy.getCommandManager().register("maceproxy", new ProxyCommand(), "mcproxy");
        logger.info("MaceCupProxyBridge ready. lobby={} events={}", lobby, eventServers);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        resourcePackHost.stop();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        resourcePackHost.send(event.getPlayer());
        checkPlayerRouting(event.getPlayer());
        proxy.getServer(lobby).ifPresent(server -> event.getPlayer().createConnectionRequest(server).fireAndForget());
    }

    private void checkPlayerRouting(Player player) {
        var dbConfig = resourcePackHost.config();
        if (dbConfig == null || dbConfig.region() == null) return;
        if (!dbConfig.geoipRoutingEnabled()) return;
        
        String currentRegion = dbConfig.region().toUpperCase(Locale.ROOT);
        String playerIp = player.getRemoteAddress().getAddress().getHostAddress();
        
        // Perform an async IP GeoIP check
        if (playerIp.equals("127.0.0.1") || playerIp.startsWith("192.168.") || playerIp.startsWith("10.") || playerIp.equals("0:0:0:0:0:0:0:1")) {
            return;
        }
        
        java.net.http.HttpClient.newHttpClient().sendAsync(
            java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://ip-api.com/json/" + playerIp))
                .timeout(java.time.Duration.ofSeconds(3))
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("status") && json.get("status").getAsString().equals("success")) {
                    String countryCode = json.get("countryCode").getAsString().toUpperCase(Locale.ROOT);
                    String continent = json.has("timezone") && json.get("timezone").getAsString().startsWith("Europe/") ? "EU" : "NA";
                    
                    List<String> euCountries = List.of("GB", "FR", "DE", "IT", "ES", "NL", "PL", "SE", "NO", "FI", "DK", "PT", "CH", "AT", "BE", "UA", "RO", "GR", "CZ", "HU", "IE");
                    boolean isEuCountry = euCountries.contains(countryCode) || continent.equals("EU");
                    
                    if (isEuCountry && currentRegion.equals("NA")) {
                        redirectPlayer(player, dbConfig.euAddress(), dbConfig.euPort(), "Redirecting you to the EU proxy for lower latency.");
                    } else if (!isEuCountry && currentRegion.equals("EU")) {
                        redirectPlayer(player, dbConfig.naAddress(), dbConfig.naPort(), "Redirecting you to the NA proxy for lower latency.");
                    }
                }
            } catch (Exception ex) {
                // Ignore API parsing failures
            }
        });
    }

    private void redirectPlayer(Player player, String host, int port, String reason) {
        if (!SAFE_HOST.matcher(host).matches() || port < 1 || port > 65535) {
            logger.warn("Blocked unsafe transfer target from config: {}:{}", host, port);
            return;
        }
        player.sendMessage(Component.text("§dMaceCup §8» §f" + reason));
        try {
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), "transfer " + player.getUsername() + " " + host + " " + port);
        } catch (Exception ex) {
            logger.error("Could not run transfer command for player {}: {}", player.getUsername(), ex.getMessage());
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getOriginalServer();
        String targetName = target.getServerInfo().getName();
        Player player = event.getPlayer();
        if (!eventServers.contains(targetName)) return;
        boolean expected = targetName.equals(expectedEventTarget.get(player.getUniqueId()));
        if (!expected && states.getOrDefault(targetName, ServerState.WAITING) != ServerState.WAITING) {
            proxy.getServer(lobby).ifPresent(server -> event.setResult(ServerPreConnectEvent.ServerResult.allowed(server)));
            player.sendMessage(Component.text("That MaceCup event server is active. Sending you to lobby-practice."));
            return;
        }
        lastUse.put(targetName, System.currentTimeMillis());
        expectedEventTarget.remove(player.getUniqueId());
    }

    @Subscribe
    public void onKick(KickedFromServerEvent event) {
        if (event.getServer().getServerInfo().getName().equals(lobby)) return;
        proxy.getServer(lobby).ifPresent(server -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(server, Component.text("Returned to lobby-practice."))));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        expectedEventTarget.remove(event.getPlayer().getUniqueId());
    }

    private Optional<RegisteredServer> leastRecentlyUsedAvailable() {
        return eventServers.stream()
                .filter(name -> states.getOrDefault(name, ServerState.WAITING) == ServerState.WAITING)
                .min(Comparator.comparingLong(name -> lastUse.getOrDefault(name, 0L)))
                .flatMap(proxy::getServer);
    }

    private final class ProxyCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 0) {
                invocation.source().sendMessage(Component.text("MaceCupProxyBridge " + states));
                return;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("pick")) {
                Optional<RegisteredServer> server = leastRecentlyUsedAvailable();
                invocation.source().sendMessage(Component.text("Selected event server: " + server.map(s -> s.getServerInfo().getName()).orElse("none")));
                return;
            }
            if (sub.equals("state") && args.length >= 3) {
                states.put(args[1], ServerState.parse(args[2]));
                invocation.source().sendMessage(Component.text("Updated " + args[1] + " to " + states.get(args[1])));
                return;
            }
            if (sub.equals("send") && args.length >= 3) {
                Optional<Player> player = proxy.getPlayer(args[1]);
                Optional<RegisteredServer> server = proxy.getServer(args[2]);
                if (player.isPresent() && server.isPresent()) {
                    expectedEventTarget.put(player.get().getUniqueId(), server.get().getServerInfo().getName());
                    player.get().createConnectionRequest(server.get()).fireAndForget();
                    invocation.source().sendMessage(Component.text("Sent " + args[1] + " to " + args[2]));
                } else {
                    invocation.source().sendMessage(Component.text("Player or server not found."));
                }
                return;
            }
            if (sub.equals("status")) {
                List<String> lines = new ArrayList<>();
                for (String server : eventServers) lines.add(server + "=" + states.get(server) + " lastUse=" + lastUse.getOrDefault(server, 0L));
                invocation.source().sendMessage(Component.text(String.join(" | ", lines)));
                return;
            }
            invocation.source().sendMessage(Component.text("Usage: /maceproxy pick|state <server> <waiting|reserved|running|ending>|send <player> <server>|status"));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("macecup.proxy.admin");
        }
    }

    private enum ServerState {
        LOBBY,
        WAITING,
        RESERVED,
        COUNTDOWN,
        RUNNING,
        ENDING,
        REGENERATING;

        static ServerState parse(String raw) {
            try {
                return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return WAITING;
            }
        }
    }
}
