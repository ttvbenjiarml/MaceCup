package com.macecup.proxy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HttpApiServer {
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{2,16}$");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Map<String, ?> serverStates;
    private final Gson gson = new Gson();
    
    private String jdbcUrl = "";
    private String username = "";
    private String password = "";
    private volatile long databaseRetryAfter;

    public HttpApiServer(ProxyServer proxy, Logger logger, Map<String, ?> serverStates) {
        this.proxy = proxy;
        this.logger = logger;
        this.serverStates = serverStates;
    }

    public void configureDatabase(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        
        // Eagerly test connection
        if (!jdbcUrl.isEmpty()) {
            try (Connection conn = openConnection()) {
                if (conn != null) {
                    logger.info("HttpApiServer database connection test OK.");
                }
            } catch (Exception ex) {
                logger.warn("HttpApiServer database unavailable; API will return 503 until MySQL is online: {}", ex.getMessage());
                markDatabaseUnavailable();
            }
        }
    }

    public void registerRoutes(HttpServer server) {
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/leaderboard", this::handleLeaderboard);
        server.createContext("/api/player", this::handlePlayer);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!requireGet(exchange)) return;
        
        JsonObject response = new JsonObject();
        response.addProperty("onlinePlayers", proxy.getPlayerCount());
        
        JsonObject servers = new JsonObject();
        for (Map.Entry<String, ?> entry : serverStates.entrySet()) {
            servers.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        response.add("servers", servers);

        sendJson(exchange, 200, response);
    }

    private void handleLeaderboard(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!requireGet(exchange)) return;

        URI requestUri = exchange.getRequestURI();
        String query = requestUri.getQuery();
        String category = "wins";
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && pair[0].equalsIgnoreCase("category")) {
                    category = URLDecoder.decode(pair[1], StandardCharsets.UTF_8).toLowerCase();
                }
            }
        }

        String sortColumn = switch (category) {
            case "kills" -> "kills";
            case "rating" -> "rating";
            case "slam" -> "highest_slam";
            case "cashcup" -> "cashcup_points";
            default -> "wins"; // Default to wins
        };

        JsonArray leaderboard = new JsonArray();
        try (Connection conn = openConnection()) {
            if (conn == null) {
                sendError(exchange, 503, "Database unavailable");
                return;
            }

            String sql = String.format("""
                SELECT p.uuid, p.username, s.wins, s.solo_wins, s.duo_wins, s.kills, s.deaths, s.rating, s.highest_slam, s.event_entries, s.cashcup_points
                FROM stats s
                INNER JOIN players p ON s.uuid = p.uuid
                ORDER BY s.%s DESC
                LIMIT 50
                """, sortColumn);

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                int rank = 1;
                while (rs.next()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("rank", rank++);
                    entry.addProperty("uuid", rs.getString("uuid"));
                    entry.addProperty("username", rs.getString("username"));
                    entry.addProperty("wins", rs.getInt("wins"));
                    entry.addProperty("soloWins", rs.getInt("solo_wins"));
                    entry.addProperty("duoWins", rs.getInt("duo_wins"));
                    entry.addProperty("kills", rs.getInt("kills"));
                    entry.addProperty("deaths", rs.getInt("deaths"));
                    entry.addProperty("rating", rs.getInt("rating"));
                    entry.addProperty("highestSlam", rs.getDouble("highest_slam"));
                    entry.addProperty("eventEntries", rs.getInt("event_entries"));
                    entry.addProperty("cashCupPoints", rs.getInt("cashcup_points"));
                    
                    double kd = rs.getInt("deaths") == 0 ? rs.getInt("kills") : (double) rs.getInt("kills") / rs.getInt("deaths");
                    entry.addProperty("kd", Math.round(kd * 100.0) / 100.0);
                    
                    double value = switch (category) {
                        case "kills" -> rs.getInt("kills");
                        case "rating" -> rs.getInt("rating");
                        case "slam" -> rs.getDouble("highest_slam");
                        case "cashcup" -> rs.getInt("cashcup_points");
                        default -> rs.getInt("wins");
                    };
                    entry.addProperty("value", value);

                    leaderboard.add(entry);
                }
            }
        } catch (Exception ex) {
            logger.error("Error fetching leaderboard", ex);
            sendError(exchange, 500, "Internal server error");
            return;
        }

        sendJson(exchange, 200, leaderboard);
    }

    private void handlePlayer(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!requireGet(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        // Path is like /api/player/username_or_uuid
        String[] parts = path.split("/");
        if (parts.length < 4 || parts[3].isEmpty()) {
            sendError(exchange, 400, "Missing player name or UUID");
            return;
        }

        String search = URLDecoder.decode(parts[3], StandardCharsets.UTF_8);
        if (!UUID_PATTERN.matcher(search).matches() && !USERNAME_PATTERN.matcher(search).matches()) {
            sendError(exchange, 400, "Invalid player name or UUID");
            return;
        }
        String targetUuidStr = null;
        String targetName = null;

        try (Connection conn = openConnection()) {
            if (conn == null) {
                sendError(exchange, 503, "Database unavailable");
                return;
            }

            // Check if search query is a UUID
            if (UUID_PATTERN.matcher(search).matches()) {
                targetUuidStr = search;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT username FROM players WHERE uuid = ? LIMIT 1")) {
                    stmt.setString(1, targetUuidStr);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            targetName = rs.getString("username");
                        }
                    }
                }
            } else {
                // Otherwise lookup UUID by username
                try (PreparedStatement stmt = conn.prepareStatement("SELECT uuid, username FROM players WHERE username = ? LIMIT 1")) {
                    stmt.setString(1, search);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            targetUuidStr = rs.getString("uuid");
                            targetName = rs.getString("username");
                        }
                    }
                }
            }

            if (targetUuidStr == null) {
                sendError(exchange, 404, "Player not found");
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("uuid", targetUuidStr);
            response.addProperty("username", targetName == null ? search : targetName);

            // Fetch Stats
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT wins, solo_wins, duo_wins, kills, deaths, rating, highest_slam, heads, totem_pops, event_entries, cashcup_points
                FROM stats WHERE uuid = ?
                """)) {
                stmt.setString(1, targetUuidStr);
                try (ResultSet rs = stmt.executeQuery()) {
                    JsonObject stats = new JsonObject();
                    if (rs.next()) {
                        stats.addProperty("wins", rs.getInt("wins"));
                        stats.addProperty("soloWins", rs.getInt("solo_wins"));
                        stats.addProperty("duoWins", rs.getInt("duo_wins"));
                        stats.addProperty("kills", rs.getInt("kills"));
                        stats.addProperty("deaths", rs.getInt("deaths"));
                        stats.addProperty("rating", rs.getInt("rating"));
                        stats.addProperty("highestSlam", rs.getDouble("highest_slam"));
                        stats.addProperty("heads", rs.getInt("heads"));
                        stats.addProperty("totemPops", rs.getInt("totem_pops"));
                        stats.addProperty("eventEntries", rs.getInt("event_entries"));
                        stats.addProperty("cashCupPoints", rs.getInt("cashcup_points"));
                        
                        double kd = rs.getInt("deaths") == 0 ? rs.getInt("kills") : (double) rs.getInt("kills") / rs.getInt("deaths");
                        stats.addProperty("kd", Math.round(kd * 100.0) / 100.0);
                    } else {
                        // Default stats if none exist yet
                        stats.addProperty("wins", 0);
                        stats.addProperty("soloWins", 0);
                        stats.addProperty("duoWins", 0);
                        stats.addProperty("kills", 0);
                        stats.addProperty("deaths", 0);
                        stats.addProperty("rating", 1000);
                        stats.addProperty("highestSlam", 0.0);
                        stats.addProperty("heads", 0);
                        stats.addProperty("totemPops", 0);
                        stats.addProperty("eventEntries", 0);
                        stats.addProperty("cashCupPoints", 0);
                        stats.addProperty("kd", 0.0);
                    }
                    response.add("stats", stats);
                }
            }

            // Fetch active selected cosmetics
            try (PreparedStatement stmt = conn.prepareStatement("SELECT cosmetic_key FROM cosmetics WHERE uuid = ? AND selected = TRUE")) {
                stmt.setString(1, targetUuidStr);
                try (ResultSet rs = stmt.executeQuery()) {
                    JsonObject cosmetics = new JsonObject();
                    while (rs.next()) {
                        String key = rs.getString("cosmetic_key");
                        String[] pair = key.split(":", 2);
                        if (pair.length == 2) {
                            cosmetics.addProperty(pair[0], pair[1]);
                        }
                    }
                    response.add("cosmetics", cosmetics);
                }
            }

            // Fetch approved custom emotes
            try (PreparedStatement stmt = conn.prepareStatement("SELECT name, body FROM custom_emotes WHERE uuid = ? AND approved = TRUE")) {
                stmt.setString(1, targetUuidStr);
                try (ResultSet rs = stmt.executeQuery()) {
                    JsonObject emotes = new JsonObject();
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String body = rs.getString("body");
                        if (name != null && body != null && name.length() <= 24 && body.length() <= 96) {
                            emotes.addProperty(name, body);
                        }
                    }
                    response.add("customEmotes", emotes);
                }
            }

            sendJson(exchange, 200, response);

        } catch (Exception ex) {
            logger.error("Error fetching player stats", ex);
            sendError(exchange, 500, "Internal server error");
        }
    }

    private boolean handleCors(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private boolean requireGet(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("GET")) return true;
        exchange.getResponseHeaders().set("Allow", "GET, OPTIONS");
        sendError(exchange, 405, "Method not allowed");
        return false;
    }

    private void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, bytes.length);
        try (java.io.OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, status, error);
    }

    private Connection openConnection() throws Exception {
        if (jdbcUrl.isEmpty()) return null;
        if (System.currentTimeMillis() < databaseRetryAfter) return null;
        DriverManager.setLoginTimeout(2);
        Class.forName("com.mysql.cj.jdbc.Driver");
        try {
            return DriverManager.getConnection(jdbcUrl, username, password);
        } catch (Exception ex) {
            markDatabaseUnavailable();
            throw ex;
        }
    }

    private void markDatabaseUnavailable() {
        databaseRetryAfter = System.currentTimeMillis() + 60_000L;
    }
}
