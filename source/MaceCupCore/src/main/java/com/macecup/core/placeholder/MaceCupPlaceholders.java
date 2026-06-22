package com.macecup.core.placeholder;

import com.macecup.core.event.EventManager;
import com.macecup.core.pack.DatapackService;
import com.macecup.core.pack.ResourcePackService;
import com.macecup.core.storage.PlayerStats;
import com.macecup.core.storage.RedisStateService;
import com.macecup.core.storage.StatsService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaceCupPlaceholders extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final StatsService statsService;
    private final ResourcePackService resourcePackService;
    private final DatapackService datapackService;
    private final RedisStateService redisStateService;

    public MaceCupPlaceholders(JavaPlugin plugin, EventManager eventManager, StatsService statsService, ResourcePackService resourcePackService, DatapackService datapackService, RedisStateService redisStateService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.statsService = statsService;
        this.resourcePackService = resourcePackService;
        this.datapackService = datapackService;
        this.redisStateService = redisStateService;
    }

    @Override public String getIdentifier() { return "macecup"; }
    @Override public String getAuthor() { return "MaceCup"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        PlayerStats stats = offlinePlayer == null ? null : statsService.stats(offlinePlayer.getUniqueId());
        return switch (params.toLowerCase()) {
            case "event_state" -> eventManager.state().name();
            case "event_arena" -> eventManager.arenaName();
            case "event_mode" -> eventManager.mode() == null ? "none" : eventManager.mode().displayName();
            case "event_alive" -> String.valueOf(eventManager.aliveCount());
            case "datapack_status" -> datapackService.statusLine();
            case "redis_status" -> redisStateService.available() ? "online" : "offline";
            case "wins" -> stats == null ? "0" : String.valueOf(stats.wins());
            case "kills" -> stats == null ? "0" : String.valueOf(stats.kills());
            case "rating" -> stats == null ? "1000" : String.valueOf(stats.rating());
            case "highest_slam" -> stats == null ? "0" : String.valueOf(Math.round(stats.highestSlam()));
            case "cashcup_points" -> stats == null ? "0" : String.valueOf(stats.cashCupPoints());
            default -> {
                if (offlinePlayer instanceof Player player && params.equalsIgnoreCase("resource_pack_status")) yield resourcePackService.status(player);
                yield "";
            }
        };
    }
}
