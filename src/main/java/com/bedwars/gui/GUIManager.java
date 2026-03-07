package com.bedwars.gui;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.game.TeamColor;
import com.bedwars.shop.ShopCategory;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * Central GUI router — handles hotbar clicks and all custom inventory interactions.
 */
public class GUIManager implements Listener {

    private final BedwarsPlugin plugin;
    private final ArenaSelectionGUI arenaGUI;
    private final TeamSelectionGUI teamGUI;
    private final GameMenuGUI gameMenuGUI;

    public GUIManager(BedwarsPlugin plugin) {
        this.plugin = plugin;
        this.arenaGUI = new ArenaSelectionGUI(plugin);
        this.teamGUI = new TeamSelectionGUI();
        this.gameMenuGUI = new GameMenuGUI();
    }

    // =====================================================================
    // PLAYER JOIN / GAME STATE TRANSITIONS
    // =====================================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        HotbarManager.giveLobbyItems(player);
        MessageUtils.sendMessage(player, "&6Welcome to &lBedWars&r&6! &7Right-click the &eCompass &7to join a game.");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) {
            HotbarManager.giveLobbyItems(player);
        }
    }

    // =====================================================================
    // HOTBAR RIGHT-CLICK (physical item interaction)
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // Only handle right-clicks
        org.bukkit.event.block.Action action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);

        if (HotbarManager.isCompass(item)) {
            event.setCancelled(true);
            handleCompassClick(player, game);
            return;
        }

        if (game != null && game.getGameState() == GameState.PLAYING) {
            if (HotbarManager.isShopItem(item)) {
                event.setCancelled(true);
                game.getShopManager().openShop(player, ShopCategory.BLOCKS);
                playClick(player);
                return;
            }
            if (HotbarManager.isUpgradeItem(item)) {
                event.setCancelled(true);
                game.getUpgradeShopManager().openUpgradeShop(player);
                playClick(player);
            }
        }
    }

    private void handleCompassClick(Player player, BedwarsGame game) {
        playClick(player);
        if (game == null) {
            // Not in a game → open arena selector
            arenaGUI.open(player);
        } else if (game.getGameState() == GameState.WAITING || game.getGameState() == GameState.STARTING) {
            // In lobby → open team selector
            teamGUI.open(player, game);
        } else if (game.getGameState() == GameState.PLAYING) {
            // In game → open game menu
            gameMenuGUI.open(player, game);
        }
    }

    // =====================================================================
    // INVENTORY CLICK ROUTING
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        int slot = event.getRawSlot();
        int invSize = event.getInventory().getSize();

        // Only handle clicks inside the top inventory
        if (slot < 0 || slot >= invSize) return;

        // ---- Arena Selection GUI ----
        if (title.equals(ArenaSelectionGUI.TITLE)) {
            event.setCancelled(true);
            handleArenaGUI(player, slot);
            return;
        }

        // ---- Team Selection GUI ----
        if (title.equals(TeamSelectionGUI.TITLE)) {
            event.setCancelled(true);
            handleTeamGUI(player, slot);
            return;
        }

        // ---- Game Menu GUI ----
        if (title.equals(GameMenuGUI.TITLE)) {
            event.setCancelled(true);
            handleGameMenuGUI(player, slot);
            return;
        }

        // ---- Item Shop & Upgrade Shop (delegated to existing managers) ----
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        if (game.getShopManager().isShopInventory(title)) {
            event.setCancelled(true);
            handleShopClick(player, game, title, slot);
            return;
        }

        if (game.getUpgradeShopManager().isUpgradeShopInventory(title)) {
            event.setCancelled(true);
            handleUpgradeShopClick(player, game, slot);
        }
    }

    // =====================================================================
    // ARENA SELECTION HANDLER
    // =====================================================================

    private void handleArenaGUI(Player player, int slot) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || item.getItemMeta() == null) return;

        String displayName = item.getItemMeta().getDisplayName();
        if (displayName == null) return;
        String plain = displayName.replaceAll("§[0-9a-fklmnorA-F]", "").trim();

        // Close button
        if (item.getType() == org.bukkit.Material.DARK_OAK_DOOR) {
            player.closeInventory();
            return;
        }

        // Try to join arena by name
        BedwarsGame game = plugin.getGameManager().getGame(plain);
        if (game == null) return;

        if (plugin.getGameManager().isInGame(player)) {
            MessageUtils.sendMessage(player, "&cYou are already in a game! Use the compass to leave first.");
            return;
        }

        GameState state = game.getGameState();
        if (state != GameState.WAITING && state != GameState.STARTING) {
            MessageUtils.sendMessage(player, "&cThis game is already in progress!");
            return;
        }

        if (game.getPlayerCount() >= game.getMaxPlayers()) {
            MessageUtils.sendMessage(player, "&cThis game is full!");
            return;
        }

        player.closeInventory();
        boolean joined = plugin.getGameManager().joinGame(player, game);
        if (joined) {
            HotbarManager.giveWaitingItems(player);
            playClick(player);
            MessageUtils.sendMessage(player, "&aYou joined &e" + game.getArenaName() + "&a!");
        } else {
            MessageUtils.sendMessage(player, "&cFailed to join. Try again.");
        }
    }

    // =====================================================================
    // TEAM SELECTION HANDLER
    // =====================================================================

    private void handleTeamGUI(Player player, int slot) {
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) { player.closeInventory(); return; }

        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || item.getItemMeta() == null) return;

        String displayName = item.getItemMeta().getDisplayName();
        if (displayName == null) return;

        // Back button
        if (item.getType() == org.bukkit.Material.ARROW) {
            player.closeInventory();
            if (plugin.getGameManager().getPlayerGame(player) == null) {
                arenaGUI.open(player);
            }
            return;
        }

        // Leave button
        if (item.getType() == org.bukkit.Material.DARK_OAK_DOOR) {
            player.closeInventory();
            plugin.getGameManager().leaveGame(player);
            HotbarManager.giveLobbyItems(player);
            return;
        }

        // Spectate button
        if (item.getType() == org.bukkit.Material.ENDER_EYE) {
            player.closeInventory();
            MessageUtils.sendMessage(player, "&bYou are now spectating!");
            return;
        }

        // Team selection
        TeamColor color = teamGUI.getTeamColorFromSlot(player, slot, game);
        if (color == null) return;

        BedwarsTeam currentTeam = game.getPlayerTeam(player.getUniqueId());
        if (currentTeam != null && currentTeam.getColor() == color) {
            MessageUtils.sendMessage(player, "&eYou are already on this team!");
            return;
        }

        // Switch team
        if (currentTeam != null) {
            currentTeam.removePlayer(player.getUniqueId());
        }

        BedwarsTeam newTeam = game.getOrCreateTeam(color);
        int maxPerTeam = game.getMaxPlayers() / Math.max(1, game.getTeams().size());
        if (newTeam.getSize() >= maxPerTeam) {
            MessageUtils.sendMessage(player, "&cThat team is full!");
            teamGUI.open(player, game); // refresh
            return;
        }

        newTeam.addPlayer(player.getUniqueId());
        // Update player-team map via internal method
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Refresh the GUI to show updated state
            teamGUI.open(player, game);
        });

        playClick(player);
        MessageUtils.sendMessage(player, "&aYou joined " + color.getDisplayName() + " &ateam!");
    }

    // =====================================================================
    // GAME MENU HANDLER
    // =====================================================================

    private void handleGameMenuGUI(Player player, int slot) {
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) { player.closeInventory(); return; }

        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || item.getItemMeta() == null) return;

        switch (slot) {
            case 10 -> { // Item Shop
                player.closeInventory();
                game.getShopManager().openShop(player, ShopCategory.BLOCKS);
                playClick(player);
            }
            case 12 -> { // Team Upgrades
                player.closeInventory();
                game.getUpgradeShopManager().openUpgradeShop(player);
                playClick(player);
            }
            case 22 -> { // Leave game
                player.closeInventory();
                plugin.getGameManager().leaveGame(player);
                HotbarManager.giveLobbyItems(player);
            }
        }
    }

    // =====================================================================
    // SHOP HANDLER (delegates to ShopManager)
    // =====================================================================

    private void handleShopClick(Player player, BedwarsGame game, String title, int slot) {
        if (slot < 9) {
            // Category tab click
            for (ShopCategory cat : ShopCategory.values()) {
                if (cat.getSlot() == slot) {
                    game.getShopManager().openShop(player, cat);
                    playClick(player);
                    return;
                }
            }
            return;
        }

        boolean purchased = game.getShopManager().handlePurchase(player, slot);
        if (purchased) {
            playClick(player);
        }

        // Refresh to show updated counts/state
        ShopCategory currentCat = getCurrentShopCategory(title);
        game.getShopManager().openShop(player, currentCat);
    }

    private void handleUpgradeShopClick(Player player, BedwarsGame game, int slot) {
        game.getUpgradeShopManager().handleUpgradePurchase(player, slot);
        playClick(player);
        game.getUpgradeShopManager().openUpgradeShop(player);
    }

    private ShopCategory getCurrentShopCategory(String title) {
        for (ShopCategory cat : ShopCategory.values()) {
            if (title.contains(cat.getDisplayName())) return cat;
        }
        return ShopCategory.BLOCKS;
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    public ArenaSelectionGUI getArenaGUI() { return arenaGUI; }
    public TeamSelectionGUI getTeamGUI() { return teamGUI; }
    public GameMenuGUI getGameMenuGUI() { return gameMenuGUI; }
}
