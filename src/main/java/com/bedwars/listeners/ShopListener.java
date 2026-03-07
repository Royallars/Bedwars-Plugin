package com.bedwars.listeners;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.GameState;
import com.bedwars.shop.ShopCategory;
import com.bedwars.shop.ShopManager;
import com.bedwars.shop.UpgradeShopManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class ShopListener implements Listener {

    private final BedwarsPlugin plugin;

    public ShopListener(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        ShopManager shopManager = game.getShopManager();
        UpgradeShopManager upgradeShopManager = game.getUpgradeShopManager();

        if (shopManager.isShopInventory(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            // Check if clicking a category tab (slots 0-5)
            if (slot < 9) {
                for (ShopCategory cat : ShopCategory.values()) {
                    if (cat.getSlot() == slot) {
                        shopManager.openShop(player, cat);
                        return;
                    }
                }
                return;
            }

            // Handle item purchase
            shopManager.handlePurchase(player, slot);
            // Refresh shop
            shopManager.openShop(player, getCurrentCategory(title));

        } else if (upgradeShopManager.isUpgradeShopInventory(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            upgradeShopManager.handleUpgradePurchase(player, slot);
            // Refresh upgrade shop
            upgradeShopManager.openUpgradeShop(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Nothing special needed here
    }

    private ShopCategory getCurrentCategory(String title) {
        for (ShopCategory cat : ShopCategory.values()) {
            if (title.contains(cat.getDisplayName())) {
                return cat;
            }
        }
        return ShopCategory.BLOCKS;
    }
}
