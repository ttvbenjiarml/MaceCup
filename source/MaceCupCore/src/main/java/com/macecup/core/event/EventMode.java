package com.macecup.core.event;

import java.util.Locale;

public enum EventMode {
    SOLO(100, false, "Solo Royale"),
    DUO(100, true, "Duo Royale"),
    CASHCUP_SOLO(100, false, "Cash Cup Solo"),
    CASHCUP_DUO(100, true, "Cash Cup Duo");

    private final int maxPlayers;
    private final boolean duo;
    private final String displayName;

    EventMode(int maxPlayers, boolean duo, String displayName) {
        this.maxPlayers = maxPlayers;
        this.duo = duo;
        this.displayName = displayName;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public boolean duo() {
        return duo;
    }

    public String displayName() {
        return displayName;
    }

    public String promptName() {
        return displayName;
    }

    public static EventMode parse(String[] args) {
        if (args.length < 2) return null;
        String first = args[1].toLowerCase(Locale.ROOT);
        if (first.equals("solo")) return SOLO;
        if (first.equals("duo")) return DUO;
        if (first.equals("cashcup") && args.length >= 3) {
            String second = args[2].toLowerCase(Locale.ROOT);
            if (second.equals("solo")) return CASHCUP_SOLO;
            if (second.equals("duo")) return CASHCUP_DUO;
        }
        return null;
    }
}
