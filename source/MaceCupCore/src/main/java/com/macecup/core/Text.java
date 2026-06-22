package com.macecup.core;

import org.bukkit.ChatColor;

public final class Text {
    private Text() {
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String strip(String text) {
        return ChatColor.stripColor(text);
    }
}
