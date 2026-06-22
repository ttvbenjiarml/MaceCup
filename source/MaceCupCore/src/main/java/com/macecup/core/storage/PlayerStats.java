package com.macecup.core.storage;

import java.util.UUID;

public final class PlayerStats {
    private final UUID uuid;
    private int wins;
    private int soloWins;
    private int duoWins;
    private int kills;
    private int deaths;
    private int rating = 1000;
    private double highestSlam;
    private int heads;
    private int totemPops;
    private int eventEntries;
    private int cashCupPoints;

    public PlayerStats(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public int wins() { return wins; }
    public int soloWins() { return soloWins; }
    public int duoWins() { return duoWins; }
    public int kills() { return kills; }
    public int deaths() { return deaths; }
    public int rating() { return rating; }
    public double highestSlam() { return highestSlam; }
    public int heads() { return heads; }
    public int totemPops() { return totemPops; }
    public int eventEntries() { return eventEntries; }
    public int cashCupPoints() { return cashCupPoints; }

    public void addEntry() { eventEntries++; }
    public void addKill() { kills++; rating += 6; }
    public void addDeath() { deaths++; rating = Math.max(0, rating - 3); }
    public void addWin(boolean duo, boolean cashCup) {
        wins++;
        if (duo) duoWins++; else soloWins++;
        rating += cashCup ? 45 : 25;
        if (cashCup) cashCupPoints += 10;
    }
    public void recordSlam(double height) { highestSlam = Math.max(highestSlam, height); }
    public void addHead() { heads++; }
    public void addTotemPop() { totemPops++; }

    public void load(int wins, int soloWins, int duoWins, int kills, int deaths, int rating, double highestSlam, int heads, int totemPops, int eventEntries, int cashCupPoints) {
        this.wins = wins;
        this.soloWins = soloWins;
        this.duoWins = duoWins;
        this.kills = kills;
        this.deaths = deaths;
        this.rating = rating;
        this.highestSlam = highestSlam;
        this.heads = heads;
        this.totemPops = totemPops;
        this.eventEntries = eventEntries;
        this.cashCupPoints = cashCupPoints;
    }
}
