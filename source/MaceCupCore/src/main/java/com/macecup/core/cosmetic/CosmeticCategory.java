package com.macecup.core.cosmetic;

import java.util.Locale;

public enum CosmeticCategory {
    HATS,
    PARTICLE_TRAILS,
    EMOTES,
    CUSTOM_EMOTES,
    VICTORY_DANCES,
    LOBBY_GADGETS,
    JOIN_MESSAGES,
    KILL_MESSAGES,
    NAME_COLORS,
    CHAT_TAGS,
    WINNER_CROWN,
    TROPHY_DISPLAY;

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static CosmeticCategory parse(String raw) {
        String normalized = raw.toUpperCase(Locale.ROOT).replace('-', '_');
        return valueOf(normalized);
    }
}
