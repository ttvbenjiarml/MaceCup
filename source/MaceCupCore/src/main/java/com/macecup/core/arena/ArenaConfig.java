package com.macecup.core.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class ArenaConfig {
    private final String name;
    private final String worldName;
    private final Location center;
    private final double borderSize;
    private final List<Location> spawns;

    public ArenaConfig(String name, String worldName, Location center, double borderSize, List<Location> spawns) {
        this.name = name;
        this.worldName = worldName;
        this.center = center;
        this.borderSize = borderSize;
        this.spawns = spawns;
    }

    public String name() { return name; }
    public String worldName() { return worldName; }
    public Location center() { return center.clone(); }
    public double borderSize() { return borderSize; }
    public List<Location> spawns() { return spawns.stream().map(Location::clone).toList(); }
    public World world() { return Bukkit.getWorld(worldName); }

    public static ArenaConfig fromSection(String name, ConfigurationSection section) {
        String worldName = section.getString("world", "event_world");
        World world = Bukkit.getWorld(worldName);
        double x = section.getDouble("center.x", 0);
        double y = section.getDouble("center.y", 80);
        double z = section.getDouble("center.z", 0);
        Location center = new Location(world, x, y, z);
        List<Location> spawns = new ArrayList<>();
        for (String raw : section.getStringList("spawns")) {
            String[] parts = raw.split(",");
            if (parts.length >= 3) {
                try {
                    spawns.add(new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new ArenaConfig(name, worldName, center, section.getDouble("border-size", 1200), spawns);
    }
}
