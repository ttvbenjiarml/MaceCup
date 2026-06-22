package com.macecup.core;

import com.macecup.core.arena.ArenaManager;
import com.macecup.core.command.MaceCommand;
import com.macecup.core.cosmetic.CosmeticService;
import com.macecup.core.cosmetic.CustomEmoteService;
import com.macecup.core.event.EventManager;
import com.macecup.core.gui.GuiManager;
import com.macecup.core.lobby.LobbyProtectionListener;
import com.macecup.core.pack.DatapackService;
import com.macecup.core.pack.ResourcePackService;
import com.macecup.core.placeholder.MaceCupPlaceholders;
import com.macecup.core.storage.RedisStateService;
import com.macecup.core.storage.StatsService;
import com.macecup.core.world.EventBoundaryListener;
import com.macecup.core.world.MapBuilder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaceCupCore extends JavaPlugin {
    private ServerRole serverRole;
    private StatsService statsService;
    private RedisStateService redisStateService;
    private ResourcePackService resourcePackService;
    private DatapackService datapackService;
    private ArenaManager arenaManager;
    private GuiManager guiManager;
    private CosmeticService cosmeticService;
    private CustomEmoteService customEmoteService;
    private EventManager eventManager;
    private LobbyProtectionListener lobbyProtectionListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverRole = ServerRole.fromConfig(getConfig().getString("server-role"));

        statsService = new StatsService(this);
        redisStateService = new RedisStateService(this);
        resourcePackService = new ResourcePackService(this);
        datapackService = new DatapackService(this);
        arenaManager = new ArenaManager(this);
        guiManager = new GuiManager(this);
        cosmeticService = new CosmeticService(this);
        customEmoteService = new CustomEmoteService(this);
        eventManager = new EventManager(this, serverRole, statsService, redisStateService, arenaManager, guiManager);
        lobbyProtectionListener = new LobbyProtectionListener(this, serverRole, guiManager);

        statsService.start();
        redisStateService.start();
        resourcePackService.installBundledPack();
        datapackService.verifyAndInstallConfiguredWorlds();
        arenaManager.loadConfiguredArenas();
        new MapBuilder(this, serverRole).generateIfEnabled();
        arenaManager.loadConfiguredArenas();
        eventManager.start();

        Bukkit.getPluginManager().registerEvents(statsService, this);
        Bukkit.getPluginManager().registerEvents(resourcePackService, this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);
        Bukkit.getPluginManager().registerEvents(lobbyProtectionListener, this);
        Bukkit.getPluginManager().registerEvents(eventManager, this);
        Bukkit.getPluginManager().registerEvents(new EventBoundaryListener(this, serverRole), this);
        Bukkit.getPluginManager().registerEvents(arenaManager, this);

        new PerformanceOptimizer(this).start();

        MaceCommand command = new MaceCommand(this, eventManager, arenaManager, datapackService, resourcePackService, guiManager, statsService, redisStateService, cosmeticService, customEmoteService);
        getCommand("mace").setExecutor(command);
        getCommand("mace").setTabCompleter(command);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MaceCupPlaceholders(this, eventManager, statsService, resourcePackService, datapackService, redisStateService).register();
        }

        if (getConfig().getBoolean("self-test-on-start", false)) {
            Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mace selftest"), 40L);
        }

        getLogger().info("MaceCupCore enabled as " + serverRole + ".");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) eventManager.stop();
        if (redisStateService != null) redisStateService.stop();
        if (statsService != null) statsService.stop();
        if (cosmeticService != null) cosmeticService.stop();
        if (customEmoteService != null) customEmoteService.stop();
        getLogger().info("MaceCupCore disabled.");
    }

    public ServerRole serverRole() {
        return serverRole;
    }

    public EventManager getEventManager() {
        return eventManager;
    }
}
