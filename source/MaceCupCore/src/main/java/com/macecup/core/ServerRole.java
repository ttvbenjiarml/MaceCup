package com.macecup.core;

import java.util.Locale;

public enum ServerRole {
    LOBBY_PRACTICE,
    EVENT;

    public static ServerRole fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return LOBBY_PRACTICE;
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
