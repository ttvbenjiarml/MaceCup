package com.macecup.core;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PerformanceOptimizer {
    private final JavaPlugin plugin;
    private long lastTickTime = System.currentTimeMillis();
    private int ticks;

    public PerformanceOptimizer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("performance.enabled", true)) return;
        applyWorldDefaults();
        int interval = Math.max(20, plugin.getConfig().getInt("performance.monitor-interval-ticks", 100));
        Bukkit.getScheduler().runTaskTimer(plugin, this::monitorAndOptimize, interval, interval);
    }

    private void monitorAndOptimize() {
        ticks++;
        double tps = getRecentTps();
        int targetViewDistance;
        int targetSimDistance;

        if (tps >= 19.5) {
            targetViewDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.high.view", 6);
            targetSimDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.high.simulation", 4);
        } else if (tps >= 18.0) {
            targetViewDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.medium.view", 5);
            targetSimDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.medium.simulation", 4);
        } else if (tps >= 15.0) {
            targetViewDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.low.view", 4);
            targetSimDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.low.simulation", 3);
        } else {
            targetViewDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.critical.view", 3);
            targetSimDistance = plugin.getConfig().getInt("performance.dynamic-view-distance.critical.simulation", 3);
        }

        if (plugin.getConfig().getBoolean("performance.dynamic-view-distance.enabled", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getViewDistance() != targetViewDistance) {
                    player.setViewDistance(targetViewDistance);
                }
                if (player.getSimulationDistance() != targetSimDistance) {
                    player.setSimulationDistance(targetSimDistance);
                }
            }
        }

        if (plugin.getConfig().getBoolean("performance.item-cleanup.enabled", true)) {
            for (World world : Bukkit.getWorlds()) {
                cleanupExcessItems(world);
            }
        }

        int memoryTrimEvery = Math.max(0, plugin.getConfig().getInt("performance.memory-trim-every-runs", 0));
        if (memoryTrimEvery > 0 && ticks % memoryTrimEvery == 0) {
            System.gc();
        }
    }

    private double getRecentTps() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > 0) {
                return tps[0];
            }
        } catch (Throwable ignored) {
        }
        
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickTime;
        lastTickTime = now;
        if (elapsed <= 0) return 20.0;
        return Math.min(20.0, 20000.0 / elapsed);
    }

    private void cleanupExcessItems(World world) {
        if (world.getPlayerCount() == 0 && plugin.getConfig().getBoolean("performance.item-cleanup.skip-empty-worlds", true)) return;
        java.util.Collection<Item> items = world.getEntitiesByClass(Item.class);
        int maxItems = Math.max(0, plugin.getConfig().getInt("performance.item-cleanup.max-items-per-world", 80));
        if (items.size() > maxItems) {
            List<Item> itemList = new ArrayList<>(items);
            itemList.sort(Comparator.comparingInt(Item::getTicksLived).reversed());
            int toRemove = items.size() - maxItems;
            for (int i = 0; i < toRemove; i++) {
                itemList.get(i).remove();
            }
        }
    }

    private void applyWorldDefaults() {
        if (!plugin.getConfig().getBoolean("performance.world-defaults.enabled", true)) return;
        boolean mobSpawning = plugin.getConfig().getBoolean("performance.world-defaults.do-mob-spawning", false);
        boolean fireTick = plugin.getConfig().getBoolean("performance.world-defaults.do-fire-tick", false);
        int randomTickSpeed = plugin.getConfig().getInt("performance.world-defaults.random-tick-speed", 0);
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, mobSpawning);
            world.setGameRule(GameRule.DO_FIRE_TICK, fireTick);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, randomTickSpeed);
        }
    }
}
