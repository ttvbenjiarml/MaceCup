package com.macecup.core.command;

import com.macecup.core.Text;
import com.macecup.core.arena.ArenaManager;
import com.macecup.core.cosmetic.CosmeticCategory;
import com.macecup.core.cosmetic.CosmeticService;
import com.macecup.core.cosmetic.CustomEmoteService;
import com.macecup.core.event.EventManager;
import com.macecup.core.event.EventMode;
import com.macecup.core.gui.GuiManager;
import com.macecup.core.pack.DatapackService;
import com.macecup.core.pack.ResourcePackService;
import com.macecup.core.storage.RedisStateService;
import com.macecup.core.storage.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MaceCommand implements CommandExecutor, TabCompleter {
    private static final UUID SELFTEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final Map<String, String> STANDARD_EMOTES = new LinkedHashMap<>();

    static {
        STANDARD_EMOTES.put("wave", "waves.");
        STANDARD_EMOTES.put("cheer", "cheers for the cup.");
        STANDARD_EMOTES.put("clap", "claps.");
        STANDARD_EMOTES.put("dance", "does a victory dance.");
        STANDARD_EMOTES.put("facepalm", "facepalms.");
    }

    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final ArenaManager arenaManager;
    private final DatapackService datapackService;
    private final ResourcePackService resourcePackService;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final RedisStateService redisStateService;
    private final CosmeticService cosmeticService;
    private final CustomEmoteService customEmoteService;

    public MaceCommand(JavaPlugin plugin, EventManager eventManager, ArenaManager arenaManager, DatapackService datapackService, ResourcePackService resourcePackService, GuiManager guiManager, StatsService statsService, RedisStateService redisStateService, CosmeticService cosmeticService, CustomEmoteService customEmoteService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.arenaManager = arenaManager;
        this.datapackService = datapackService;
        this.resourcePackService = resourcePackService;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.redisStateService = redisStateService;
        this.cosmeticService = cosmeticService;
        this.customEmoteService = customEmoteService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("cosmetics") && sender instanceof Player player) {
            guiManager.openCosmetics(player);
            return true;
        }
        if (sub.equals("cosmetic")) return cosmetic(sender, args);
        if (sub.equals("emotes")) return emotes(sender);
        if (sub.equals("emote")) return emote(sender, args);
        if (sub.equals("customemote") || sub.equals("customemotes")) return customEmote(sender, args);
        if (sub.equals("selftest")) return selftest(sender);
        if (sub.equals("admin") && sender instanceof Player player) {
            if (!require(sender, "macecup.admin")) return true;
            guiManager.openAdmin(player);
            return true;
        }
        if (sub.equals("top") && sender instanceof Player player) {
            guiManager.openLeaderboard(player, statsService.leaderboard(args.length >= 2 ? args[1] : "wins", 28));
            return true;
        }
        if (sub.equals("host")) return host(sender, args);
        if (sub.equals("cancelhost")) {
            if (!require(sender, "macecup.cancelhost")) return true;
            eventManager.cancel();
            return true;
        }
        if (sub.equals("datapack")) {
            datapackService.verifyAndInstallConfiguredWorlds();
            sender.sendMessage(Text.color("&dDatapack: &f" + datapackService.statusLine()));
            return true;
        }
        if (sub.equals("resourcepack")) {
            sender.sendMessage(Text.color("&dResource pack: &f" + resourcePackService.installStatus()));
            if (sender instanceof Player player) {
                resourcePackService.send(player);
                sender.sendMessage(Text.color("&aResource pack prompt resent."));
            }
            return true;
        }
        if (sub.equals("network")) {
            sender.sendMessage(Text.color("&dRedis: &f" + (redisStateService.available() ? "online" : "offline/local")));
            sender.sendMessage(Text.color("&dEvent: &f" + eventManager.state() + " &7arena=&f" + eventManager.arenaName()));
            return true;
        }
        if (sub.equals("listarenas")) {
            sender.sendMessage(Text.color("&dArenas: &f" + String.join(", ", arenaManager.arenas().stream().map(a -> a.name()).toList())));
            return true;
        }
        if (sub.equals("arenawand") && sender instanceof Player player) {
            if (!require(sender, "macecup.arena.save")) return true;
            arenaManager.giveArenaWand(player);
            return true;
        }
        if (sub.equals("arenaselection") && sender instanceof Player player) {
            if (!require(sender, "macecup.arena.save")) return true;
            sender.sendMessage(Text.color("&dArena selection: &f" + arenaManager.selectionStatus(player)));
            return true;
        }
        if (sub.equals("savearena")) {
            if (!require(sender, "macecup.arena.save")) return true;
            if (args.length < 2) return usage(sender, "/mace savearena <arena> [radius]");
            int radius = args.length >= 3 ? parseInt(sender, args[2], -1) : -1;
            arenaManager.saveArena(args[1], sender, radius);
            return true;
        }
        if (sub.equals("regenarena")) {
            if (!require(sender, "macecup.arena.regen")) return true;
            if (args.length < 2) return usage(sender, "/mace regenarena <arena|all>");
            if (args[1].equalsIgnoreCase("all")) arenaManager.regenerateAll(sender);
            else arenaManager.regenerate(args[1], sender);
            return true;
        }
        if (sub.equals("fillloot")) {
            if (!require(sender, "macecup.arena.regen")) return true;
            if (args.length < 2) return usage(sender, "/mace fillloot <arena>");
            var arena = arenaManager.arena(args[1]);
            if (arena == null) {
                sender.sendMessage(Text.color("&cUnknown arena: " + args[1]));
                return true;
            }
            arenaManager.fillLoot(arena);
            sender.sendMessage(Text.color("&aLoot refilled for &f" + arena.name() + "&a."));
            return true;
        }
        if (sub.equals("setcenter") && sender instanceof Player player) {
            if (!require(sender, "macecup.arena.save")) return true;
            if (args.length < 2) return usage(sender, "/mace setcenter <arena>");
            arenaManager.setCenter(args[1], player.getLocation());
            sender.sendMessage(Text.color("&aArena center updated."));
            return true;
        }
        if (sub.equals("setborder")) {
            if (!require(sender, "macecup.arena.save")) return true;
            if (args.length < 3) return usage(sender, "/mace setborder <arena> <size>");
            double size = parseDouble(sender, args[2], -1);
            if (size <= 0) return true;
            arenaManager.setBorder(args[1], size);
            sender.sendMessage(Text.color("&aArena border updated."));
            return true;
        }
        if (sub.equals("addspawn") && sender instanceof Player player) {
            if (!require(sender, "macecup.arena.save")) return true;
            if (args.length < 2) return usage(sender, "/mace addspawn <arena>");
            arenaManager.addSpawn(args[1], player.getLocation());
            sender.sendMessage(Text.color("&aArena spawn added."));
            return true;
        }
        if (sub.equals("removespawn")) {
            if (!require(sender, "macecup.arena.save")) return true;
            if (args.length < 3) return usage(sender, "/mace removespawn <arena> <id>");
            int index = parseInt(sender, args[2], -1);
            if (index < 0) return true;
            arenaManager.removeSpawn(args[1], index);
            sender.sendMessage(Text.color("&aArena spawn removed."));
            return true;
        }
        help(sender);
        return true;
    }

    private boolean cosmetic(CommandSender sender, String[] args) {
        if (!require(sender, "macecup.cosmetics")) return true;
        if (args.length < 2) return usage(sender, "/mace cosmetic list <category> | select <category> <id> | clear <category>");
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("categories")) {
            sender.sendMessage(Text.color("&dCosmetic categories: &f" + String.join(", ", categories())));
            return true;
        }
        if (args.length < 3) return usage(sender, "/mace cosmetic " + action + " <category>");
        CosmeticCategory category = parseCategory(sender, args[2]);
        if (category == null) return true;
        if (action.equals("list")) {
            sender.sendMessage(Text.color("&d" + category.key() + ": &f" + String.join(", ", cosmeticService.available(category))));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.color("&cOnly players can select cosmetics."));
            return true;
        }
        if (action.equals("select")) {
            if (args.length < 4) return usage(sender, "/mace cosmetic select <category> <id>");
            if (!cosmeticService.select(player.getUniqueId(), category, args[3])) {
                sender.sendMessage(Text.color("&cUnknown cosmetic: " + args[3]));
                return true;
            }
            sender.sendMessage(Text.color("&aSelected &f" + args[3] + " &afor &f" + category.key() + "&a."));
            return true;
        }
        if (action.equals("clear")) {
            cosmeticService.clear(player.getUniqueId(), category);
            sender.sendMessage(Text.color("&aCleared &f" + category.key() + "&a."));
            return true;
        }
        return usage(sender, "/mace cosmetic list <category> | select <category> <id> | clear <category>");
    }

    private boolean emotes(CommandSender sender) {
        if (!require(sender, "macecup.emotes")) return true;
        sender.sendMessage(Text.color("&dEmotes: &f" + String.join(", ", STANDARD_EMOTES.keySet())));
        return true;
    }

    private boolean emote(CommandSender sender, String[] args) {
        if (!require(sender, "macecup.emotes")) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.color("&cOnly players can use emotes."));
            return true;
        }
        if (args.length < 2) return usage(sender, "/mace emote <" + String.join("|", STANDARD_EMOTES.keySet()) + ">");
        String key = args[1].toLowerCase(Locale.ROOT);
        String body = STANDARD_EMOTES.get(key);
        if (body == null) return usage(sender, "/mace emote <" + String.join("|", STANDARD_EMOTES.keySet()) + ">");
        Bukkit.broadcastMessage(Text.color("&d" + player.getName() + " &f" + body));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.25F);
        return true;
    }

    private boolean customEmote(CommandSender sender, String[] args) {
        if (!require(sender, "macecup.emotes.custom")) return true;
        if (args.length < 2) return usage(sender, "/mace customemote create <name> <text...> | list | use <name> | approve <uuid|player> <name>");
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.color("&cOnly players can create custom emotes."));
                return true;
            }
            if (args.length < 4) return usage(sender, "/mace customemote create <name> <text...>");
            CustomEmoteService.CreateResult result = customEmoteService.create(player.getUniqueId(), args[2], join(args, 3));
            sender.sendMessage(Text.color(switch (result) {
                case CREATED -> "&aCustom emote saved. It can be used after approval.";
                case INVALID_NAME -> "&cUse 2-24 letters, numbers, underscores, or dashes.";
                case INVALID_BODY -> "&cUse 1-96 plain text characters for the emote body.";
                case BLACKLISTED -> "&cThat emote contains blocked text.";
            }));
            return true;
        }
        if (action.equals("list")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.color("&cOnly players can list their custom emotes."));
                return true;
            }
            sender.sendMessage(Text.color("&dCustom emotes: &f" + String.join(", ", customEmoteService.names(player.getUniqueId()))));
            return true;
        }
        if (action.equals("use")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.color("&cOnly players can use custom emotes."));
                return true;
            }
            if (args.length < 3) return usage(sender, "/mace customemote use <name>");
            CustomEmoteService.UseResult result = customEmoteService.use(player.getUniqueId(), args[2]);
            if (result == CustomEmoteService.UseResult.USED) {
                Bukkit.broadcastMessage(Text.color("&d" + player.getName() + " &f" + customEmoteService.body(player.getUniqueId(), args[2])));
            } else {
                sender.sendMessage(Text.color(switch (result) {
                    case NOT_FOUND -> "&cUnknown custom emote.";
                    case NOT_APPROVED -> "&cThat custom emote is waiting for approval.";
                    case COOLDOWN -> "&cCustom emote cooldown is still active.";
                    case USED -> "&aUsed.";
                }));
            }
            return true;
        }
        if (action.equals("approve")) {
            if (!require(sender, "macecup.emotes.custom.approve")) return true;
            if (args.length < 4) return usage(sender, "/mace customemote approve <uuid|player> <name>");
            UUID target = resolveUuid(args[2]);
            if (target == null) {
                sender.sendMessage(Text.color("&cUse an online player name or a UUID."));
                return true;
            }
            if (!customEmoteService.approve(target, args[3])) {
                sender.sendMessage(Text.color("&cCustom emote not found."));
                return true;
            }
            sender.sendMessage(Text.color("&aCustom emote approved."));
            return true;
        }
        return usage(sender, "/mace customemote create <name> <text...> | list | use <name> | approve <uuid|player> <name>");
    }

    private boolean selftest(CommandSender sender) {
        if (!require(sender, "macecup.selftest")) return true;
        List<String> failures = new ArrayList<>();
        check(failures, cosmeticService.categoryCount() == CosmeticCategory.values().length, "all cosmetic categories registered");
        check(failures, cosmeticService.available(CosmeticCategory.HATS).contains("neon-crown"), "hat cosmetic catalog loaded");
        check(failures, cosmeticService.select(SELFTEST_UUID, CosmeticCategory.HATS, "neon-crown"), "cosmetic select accepts valid id");
        check(failures, "neon-crown".equals(cosmeticService.selected(SELFTEST_UUID, CosmeticCategory.HATS)), "cosmetic selected value persists");
        cosmeticService.clear(SELFTEST_UUID, CosmeticCategory.HATS);
        check(failures, "none".equals(cosmeticService.selected(SELFTEST_UUID, CosmeticCategory.HATS)), "cosmetic clear resets selected value");
        check(failures, !cosmeticService.select(SELFTEST_UUID, CosmeticCategory.HATS, "missing-hat"), "cosmetic select rejects invalid id");
        check(failures, STANDARD_EMOTES.size() == 5 && STANDARD_EMOTES.containsKey("wave"), "standard emote catalog loaded");
        check(failures, customEmoteService.containsBlacklisted("contains badword here"), "custom emote blacklist blocks configured text");
        check(failures, customEmoteService.create(SELFTEST_UUID, "x", "hello") == CustomEmoteService.CreateResult.INVALID_NAME, "custom emote invalid names rejected");
        check(failures, customEmoteService.create(SELFTEST_UUID, "selftest", "throws a clean test emote.") == CustomEmoteService.CreateResult.CREATED, "custom emote creation works");
        check(failures, customEmoteService.use(SELFTEST_UUID, "selftest") == CustomEmoteService.UseResult.NOT_APPROVED, "custom emote approval gate works");
        check(failures, customEmoteService.approve(SELFTEST_UUID, "selftest"), "custom emote approval works");
        check(failures, customEmoteService.use(SELFTEST_UUID, "selftest") == CustomEmoteService.UseResult.USED, "custom emote use works");
        check(failures, customEmoteService.use(SELFTEST_UUID, "selftest") == CustomEmoteService.UseResult.COOLDOWN, "custom emote cooldown works");
        check(failures, !arenaManager.arenas().isEmpty(), "arenas loaded");
        check(failures, arenaManager.arena("solo") != null, "solo arena configured");
        check(failures, arenaManager.arena("duo") != null, "duo arena configured");
        check(failures, eventManager.state() != null, "event manager state available");
        check(failures, datapackService.statusLine() != null && !datapackService.statusLine().isBlank(), "datapack status available");

        if (failures.isEmpty()) {
            sender.sendMessage(Text.color("&aSELFTEST ALL PASSED"));
            plugin.getLogger().info("SELFTEST ALL PASSED");
        } else {
            for (String failure : failures) {
                sender.sendMessage(Text.color("&cSELFTEST FAIL " + failure));
                plugin.getLogger().severe("SELFTEST FAIL " + failure);
            }
        }
        return true;
    }

    private boolean host(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can host events from lobby-practice.");
            return true;
        }
        if (!require(sender, "macecup.host")) return true;
        EventMode mode = EventMode.parse(args);
        if (mode == null) return usage(sender, "/mace host solo <arena> | /mace host duo <arena> | /mace host cashcup <solo|duo> <arena>");
        if (mode.name().startsWith("CASHCUP") && args.length < 4) return usage(sender, "/mace host cashcup <solo|duo> <arena>");
        if (!mode.name().startsWith("CASHCUP") && args.length < 3) return usage(sender, "/mace host " + (mode.duo() ? "duo" : "solo") + " <arena>");
        String arena = mode.name().startsWith("CASHCUP") ? args[3] : args[2];
        return eventManager.host(player, mode, arena);
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("macecup.admin")) return true;
        sender.sendMessage(Text.color("&cMissing permission: " + permission));
        return false;
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(Text.color("&cUsage: " + usage));
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.color("&dMaceCup &8» &f/mace host, cosmetics, cosmetic, emotes, emote, customemote, top, datapack, resourcepack, network, listarenas, arenawand, savearena, regenarena, fillloot, setcenter, setborder, addspawn, removespawn, selftest"));
        sender.sendMessage(Text.color("&7Arena commands: &f/mace arenawand &8- &7Select pos1/pos2 with netherite shovel"));
        sender.sendMessage(Text.color("&7                &f/mace savearena <arena> [radius] &8- &7Saves selection or snapshot"));
        sender.sendMessage(Text.color("&7                &f/mace regenarena <arena|all> &8- &7Restores saved snapshot"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("host", "cancelhost", "cosmetics", "cosmetic", "emotes", "emote", "customemote", "admin", "top", "datapack", "resourcepack", "network", "listarenas", "arenawand", "arenaselection", "savearena", "regenarena", "fillloot", "setcenter", "setborder", "addspawn", "removespawn", "selftest");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("host")) return List.of("solo", "duo", "cashcup");
        if (args.length == 3 && args[0].equalsIgnoreCase("host") && args[1].equalsIgnoreCase("cashcup")) return List.of("solo", "duo");
        if (args[0].equalsIgnoreCase("host")) {
            if (args.length == 3 && (args[1].equalsIgnoreCase("solo") || args[1].equalsIgnoreCase("duo"))) return arenaNames();
            if (args.length == 4 && args[1].equalsIgnoreCase("cashcup")) return arenaNames();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cosmetic")) return List.of("categories", "list", "select", "clear");
        if (args.length == 3 && args[0].equalsIgnoreCase("cosmetic")) return categories();
        if (args.length == 4 && args[0].equalsIgnoreCase("cosmetic")) {
            CosmeticCategory category = parseCategory(args[2]);
            return category == null ? List.of() : cosmeticService.available(category);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("emote")) return new ArrayList<>(STANDARD_EMOTES.keySet());
        if (args.length == 2 && (args[0].equalsIgnoreCase("customemote") || args[0].equalsIgnoreCase("customemotes"))) return List.of("create", "list", "use", "approve");
        if (args.length >= 2 && List.of("savearena", "regenarena", "fillloot", "setcenter", "setborder", "addspawn", "removespawn").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> arenas = new ArrayList<>(arenaNames());
            if (args[0].equalsIgnoreCase("regenarena")) arenas.add("all");
            return arenas;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) return List.of("wins", "kills", "rating", "slam", "cashcup");
        return List.of();
    }

    private CosmeticCategory parseCategory(CommandSender sender, String raw) {
        CosmeticCategory category = parseCategory(raw);
        if (category == null) sender.sendMessage(Text.color("&cUnknown category. Use: " + String.join(", ", categories())));
        return category;
    }

    private CosmeticCategory parseCategory(String raw) {
        try {
            return CosmeticCategory.parse(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<String> categories() {
        return Arrays.stream(CosmeticCategory.values()).map(CosmeticCategory::key).toList();
    }

    private List<String> arenaNames() {
        return arenaManager.arenas().stream().map(a -> a.name()).toList();
    }

    private UUID resolveUuid(String value) {
        Player player = Bukkit.getPlayerExact(value);
        if (player != null) return player.getUniqueId();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private int parseInt(CommandSender sender, String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.color("&cInvalid number: " + value));
            return fallback;
        }
    }

    private double parseDouble(CommandSender sender, String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.color("&cInvalid number: " + value));
            return fallback;
        }
    }

    private void check(List<String> failures, boolean condition, String label) {
        if (condition) {
            plugin.getLogger().info("SELFTEST PASS " + label);
        } else {
            failures.add(label);
        }
    }
}
