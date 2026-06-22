package com.macecup.core.gui;

import com.macecup.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class GuiManager implements Listener {
    public static final String PREFIX = Text.color("&5MaceCup ");
    private final JavaPlugin plugin;

    public GuiManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openCosmetics(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, PREFIX + "Cosmetics");
        List<MenuItem> items = List.of(
                new MenuItem(10, Material.GOLDEN_HELMET, "&dHats"),
                new MenuItem(11, Material.BLAZE_POWDER, "&dParticle trails"),
                new MenuItem(12, Material.NAME_TAG, "&dEmotes"),
                new MenuItem(13, Material.WRITABLE_BOOK, "&dCustom emotes"),
                new MenuItem(14, Material.FIREWORK_ROCKET, "&dVictory dances"),
                new MenuItem(15, Material.ENDER_EYE, "&dLobby gadgets"),
                new MenuItem(16, Material.PAPER, "&dJoin messages"),
                new MenuItem(28, Material.IRON_SWORD, "&dKill messages"),
                new MenuItem(29, Material.PINK_DYE, "&dName colors"),
                new MenuItem(30, Material.OAK_SIGN, "&dChat tags"),
                new MenuItem(31, Material.NETHER_STAR, "&dWinner crown"),
                new MenuItem(32, Material.CHEST, "&dTrophy display"),
                new MenuItem(49, Material.BARRIER, "&cClose")
        );
        for (MenuItem item : items) inventory.setItem(item.slot(), icon(item.material(), item.name()));
        player.openInventory(inventory);
    }

    public void openAdmin(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, PREFIX + "Admin");
        inventory.setItem(10, icon(Material.BEACON, "&dHost Solo Default"));
        inventory.setItem(12, icon(Material.RECOVERY_COMPASS, "&dNetwork Status"));
        inventory.setItem(14, icon(Material.BRUSH, "&dRegenerate Arenas"));
        inventory.setItem(16, icon(Material.CHEST, "&dRefill Loot"));
        inventory.setItem(28, icon(Material.AMETHYST_SHARD, "&dCancel Event"));
        inventory.setItem(30, icon(Material.MAP, "&dDatapack Status"));
        inventory.setItem(32, icon(Material.COMPASS, "&dResource Pack Status"));
        inventory.setItem(49, icon(Material.BARRIER, "&cClose"));
        player.openInventory(inventory);
    }

    public void openLeaderboard(Player player, List<String> rows) {
        Inventory inventory = Bukkit.createInventory(null, 54, PREFIX + "Leaderboards");
        int slot = 10;
        for (String row : rows) {
            inventory.setItem(slot++, icon(Material.GOLD_INGOT, "&f" + row));
            if (slot == 17) slot = 28;
        }
        inventory.setItem(49, icon(Material.BARRIER, "&cClose"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(PREFIX)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        if (current == null) return;
        if (current.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }
        if (!event.getView().getTitle().equals(PREFIX + "Admin")) return;
        String command = switch (event.getRawSlot()) {
            case 10 -> "mace host solo default";
            case 12 -> "mace network";
            case 14 -> "mace regenarena all";
            case 16 -> "mace fillloot default";
            case 28 -> "mace cancelhost";
            case 30 -> "mace datapack";
            case 32 -> "mace resourcepack";
            default -> null;
        };
        if (command != null) {
            player.closeInventory();
            Bukkit.dispatchCommand(player, command);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith(PREFIX)) event.setCancelled(true);
    }

    private ItemStack icon(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(name));
        item.setItemMeta(meta);
        return item;
    }

    private record MenuItem(int slot, Material material, String name) {
    }
}
