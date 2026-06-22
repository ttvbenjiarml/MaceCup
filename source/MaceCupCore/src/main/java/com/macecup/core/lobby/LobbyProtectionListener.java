package com.macecup.core.lobby;

import com.macecup.core.ServerRole;
import com.macecup.core.Text;
import com.macecup.core.gui.GuiManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyProtectionListener implements Listener {
    private static final int COMPASS_SLOT = 4;
    private final JavaPlugin plugin;
    private final ServerRole role;
    private final GuiManager guiManager;
    private final NamespacedKey lobbyItemKey;

    public LobbyProtectionListener(JavaPlugin plugin, ServerRole role, GuiManager guiManager) {
        this.plugin = plugin;
        this.role = role;
        this.guiManager = guiManager;
        this.lobbyItemKey = new NamespacedKey(plugin, "lobby_item");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (role == ServerRole.LOBBY_PRACTICE) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> normalizeCompass(event.getPlayer()), 5L);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isCosmeticsCompass(event.getItem())) return;
        event.setCancelled(true);
        guiManager.openCosmetics(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (role == ServerRole.LOBBY_PRACTICE && touchesCompass(event)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> normalizeCompass(player));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (role != ServerRole.LOBBY_PRACTICE) return;
        if (event.getRawSlots().contains(COMPASS_SLOT) || event.getRawSlots().contains(40)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> normalizeCompass((Player) event.getWhoClicked()));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isCosmeticsCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            normalizeCompass(event.getPlayer());
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isCosmeticsCompass(event.getMainHandItem()) || isCosmeticsCompass(event.getOffHandItem())) {
            event.setCancelled(true);
            normalizeCompass(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isCosmeticsCompass(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isCosmeticsCompass);
        plugin.getServer().getScheduler().runTask(plugin, () -> normalizeCompass(event.getEntity()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (role == ServerRole.LOBBY_PRACTICE && !event.getPlayer().hasPermission("macecup.admin.build")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (role == ServerRole.LOBBY_PRACTICE && !event.getPlayer().hasPermission("macecup.admin.build")) {
            event.setCancelled(true);
        }
    }

    public void removeCompass(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isCosmeticsCompass(inventory.getItem(i))) inventory.setItem(i, null);
        }
        if (isCosmeticsCompass(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
    }

    public void normalizeCompass(Player player) {
        if (role != ServerRole.LOBBY_PRACTICE || player == null || !player.isOnline()) return;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != COMPASS_SLOT && isCosmeticsCompass(inventory.getItem(i))) inventory.setItem(i, null);
        }
        if (isCosmeticsCompass(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
        inventory.setItem(COMPASS_SLOT, cosmeticsCompass());
    }

    private boolean touchesCompass(InventoryClickEvent event) {
        return isCosmeticsCompass(event.getCurrentItem()) || isCosmeticsCompass(event.getCursor()) || event.getSlot() == COMPASS_SLOT || event.getHotbarButton() == COMPASS_SLOT;
    }

    private boolean isCosmeticsCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(lobbyItemKey, PersistentDataType.STRING);
    }

    private ItemStack cosmeticsCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color("&d&lCosmetics"));
        meta.setCustomModelData(781009);
        meta.getPersistentDataContainer().set(lobbyItemKey, PersistentDataType.STRING, "cosmetics_compass");
        item.setItemMeta(meta);
        return item;
    }
}
