package com.bedwars.shop;

import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.*;

public class ShopManager {

    private final BedwarsGame game;
    private final List<ShopItem> shopItems = new ArrayList<>();

    // Track one-per-game purchases
    private final Map<UUID, Set<String>> playerPurchases = new HashMap<>();

    public ShopManager(BedwarsGame game) {
        this.game = game;
        registerItems();
    }

    private void registerItems() {
        // BLOCKS
        shopItems.add(new ShopItem("Wool", Material.WHITE_WOOL, 16, Material.IRON_INGOT, 4, ShopCategory.BLOCKS));
        shopItems.add(new ShopItem("Hardened Clay", Material.TERRACOTTA, 16, Material.IRON_INGOT, 12, ShopCategory.BLOCKS));
        shopItems.add(new ShopItem("Blast-Proof Glass", Material.WHITE_STAINED_GLASS, 4, Material.IRON_INGOT, 12, ShopCategory.BLOCKS));
        shopItems.add(new ShopItem("Wooden Planks", Material.OAK_PLANKS, 16, Material.IRON_INGOT, 4, ShopCategory.BLOCKS));
        shopItems.add(new ShopItem("End Stone", Material.END_STONE, 12, Material.IRON_INGOT, 24, ShopCategory.BLOCKS));
        shopItems.add(new ShopItem("Obsidian", Material.OBSIDIAN, 4, Material.EMERALD, 4, ShopCategory.BLOCKS));
        shopItems.add(new ShopItem("Ladder", Material.LADDER, 16, Material.IRON_INGOT, 4, ShopCategory.BLOCKS));

        // WEAPONS
        shopItems.add(new ShopItem("Stone Sword", Material.STONE_SWORD, 1, Material.IRON_INGOT, 10, ShopCategory.WEAPONS));
        shopItems.add(new ShopItem("Iron Sword", Material.IRON_SWORD, 1, Material.GOLD_INGOT, 7, ShopCategory.WEAPONS));
        shopItems.add(new ShopItem("Diamond Sword", Material.DIAMOND_SWORD, 1, Material.EMERALD, 3, ShopCategory.WEAPONS));
        shopItems.add(new ShopItem("Knockback Stick", Material.STICK, 1, Material.GOLD_INGOT, 5, ShopCategory.WEAPONS));
        shopItems.add(new ShopItem("Bow", Material.BOW, 1, Material.GOLD_INGOT, 12, ShopCategory.WEAPONS));
        shopItems.add(new ShopItem("Punch Bow", Material.BOW, 1, Material.GOLD_INGOT, 24, ShopCategory.WEAPONS));
        shopItems.add(new ShopItem("Power Bow", Material.BOW, 1, Material.EMERALD, 3, ShopCategory.WEAPONS));
        shopItems.add(new ShopItem("Arrow", Material.ARROW, 6, Material.GOLD_INGOT, 2, ShopCategory.WEAPONS));

        // ARMOR
        shopItems.add(new ShopItem("Chainmail Armor", Material.CHAINMAIL_CHESTPLATE, 1, Material.IRON_INGOT, 40, ShopCategory.ARMOR));
        shopItems.add(new ShopItem("Iron Armor", Material.IRON_CHESTPLATE, 1, Material.GOLD_INGOT, 12, ShopCategory.ARMOR));
        shopItems.add(new ShopItem("Diamond Armor", Material.DIAMOND_CHESTPLATE, 1, Material.EMERALD, 6, ShopCategory.ARMOR));

        // TOOLS
        shopItems.add(new ShopItem("Shears", Material.SHEARS, 1, Material.IRON_INGOT, 20, ShopCategory.TOOLS));
        shopItems.add(new ShopItem("Wooden Pickaxe", Material.WOODEN_PICKAXE, 1, Material.IRON_INGOT, 10, ShopCategory.TOOLS));
        shopItems.add(new ShopItem("Stone Pickaxe", Material.STONE_PICKAXE, 1, Material.IRON_INGOT, 20, ShopCategory.TOOLS));
        shopItems.add(new ShopItem("Iron Pickaxe", Material.IRON_PICKAXE, 1, Material.GOLD_INGOT, 3, ShopCategory.TOOLS));
        shopItems.add(new ShopItem("Wooden Axe", Material.WOODEN_AXE, 1, Material.IRON_INGOT, 10, ShopCategory.TOOLS));
        shopItems.add(new ShopItem("Stone Axe", Material.STONE_AXE, 1, Material.IRON_INGOT, 20, ShopCategory.TOOLS));
        shopItems.add(new ShopItem("Iron Axe", Material.IRON_AXE, 1, Material.GOLD_INGOT, 3, ShopCategory.TOOLS));

        // POTIONS
        shopItems.add(new ShopItem("Speed Potion", Material.POTION, 1, Material.GOLD_INGOT, 2, ShopCategory.POTIONS));
        shopItems.add(new ShopItem("Jump Potion", Material.POTION, 1, Material.GOLD_INGOT, 2, ShopCategory.POTIONS));
        shopItems.add(new ShopItem("Invisibility Potion", Material.POTION, 1, Material.EMERALD, 4, ShopCategory.POTIONS));

        // UTILITY
        shopItems.add(new ShopItem("Golden Apple", Material.GOLDEN_APPLE, 1, Material.GOLD_INGOT, 3, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Enchanted Golden Apple", Material.ENCHANTED_GOLDEN_APPLE, 1, Material.GOLD_INGOT, 6, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("TNT", Material.TNT, 1, Material.GOLD_INGOT, 4, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Ender Pearl", Material.ENDER_PEARL, 1, Material.EMERALD, 4, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Fireball", Material.FIRE_CHARGE, 1, Material.GOLD_INGOT, 40, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Bridge Egg", Material.EGG, 1, Material.GOLD_INGOT, 30, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Water Bucket", Material.WATER_BUCKET, 1, Material.GOLD_INGOT, 6, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Sponge", Material.SPONGE, 4, Material.GOLD_INGOT, 3, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Magic Milk", Material.MILK_BUCKET, 1, Material.GOLD_INGOT, 4, ShopCategory.UTILITY));
        shopItems.add(new ShopItem("Dream Defender", Material.IRON_GOLEM_SPAWN_EGG, 1, Material.IRON_INGOT, 120, ShopCategory.UTILITY, true));
    }

    public void openShop(Player player, ShopCategory category) {
        Inventory inv = game.getPlugin().getServer().createInventory(null, 54,
                MessageUtils.color("&6&lItem Shop - &e" + category.getDisplayName()));

        // Category tabs in top row
        for (ShopCategory cat : ShopCategory.values()) {
            ItemStack tab = new ItemStack(Material.BOOK);
            ItemMeta meta = tab.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtils.color((cat == category ? "&e&l" : "&7") + cat.getDisplayName()));
                tab.setItemMeta(meta);
            }
            inv.setItem(cat.getSlot(), tab);
        }

        // Spacer
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i) == null) {
                ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = glass.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(" ");
                    glass.setItemMeta(meta);
                }
                inv.setItem(i, glass);
            }
        }

        // Items for category
        int slot = 9;
        for (ShopItem item : shopItems) {
            if (item.getCategory() != category) continue;
            if (slot >= 54) break;

            ItemStack displayItem = buildDisplayItem(player, item);
            inv.setItem(slot++, displayItem);
        }

        player.openInventory(inv);
    }

    private ItemStack buildDisplayItem(Player player, ShopItem shopItem) {
        ItemStack item = new ItemStack(shopItem.getMaterial(), shopItem.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Apply enchantments for specific items
        applyEnchantments(shopItem, item, meta, getPlayerTeam(player));

        // Handle special items
        if (shopItem.getMaterial() == Material.POTION) {
            if (meta instanceof PotionMeta potionMeta) {
                if (shopItem.getName().contains("Speed")) {
                    potionMeta.setBasePotionData(new PotionData(PotionType.SPEED));
                } else if (shopItem.getName().contains("Jump")) {
                    potionMeta.setBasePotionData(new PotionData(PotionType.JUMP));
                } else if (shopItem.getName().contains("Invisibility")) {
                    potionMeta.setBasePotionData(new PotionData(PotionType.INVISIBILITY));
                }
            }
        }

        meta.setDisplayName(MessageUtils.color("&f" + shopItem.getName()));

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color("&7Cost: &f" + shopItem.getCost() + " &6" + shopItem.getCurrencyName()));

        boolean canAfford = hasEnoughCurrency(player, shopItem.getCurrency(), shopItem.getCost());
        if (!canAfford) {
            lore.add(MessageUtils.color("&cYou can't afford this!"));
        }

        if (shopItem.isOnePerGame()) {
            boolean purchased = playerPurchases.getOrDefault(player.getUniqueId(), Collections.emptySet())
                    .contains(shopItem.getName());
            if (purchased) {
                lore.add(MessageUtils.color("&cAlready purchased!"));
            } else {
                lore.add(MessageUtils.color("&eOne per game"));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private void applyEnchantments(ShopItem shopItem, ItemStack item, ItemMeta meta, BedwarsTeam team) {
        if (team == null) return;

        // Apply team sharpness to swords
        if (item.getType().name().endsWith("SWORD") && team.getSharpenLevel() > 0) {
            meta.addEnchant(Enchantment.DAMAGE_ALL, team.getSharpenLevel(), true);
        }
        // Apply team protection to armor
        if ((item.getType().name().endsWith("CHESTPLATE") ||
                item.getType().name().endsWith("LEGGINGS") ||
                item.getType().name().endsWith("BOOTS") ||
                item.getType().name().endsWith("HELMET")) && team.getProtectionLevel() > 0) {
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, team.getProtectionLevel(), true);
        }

        // Special weapon enchantments
        if (shopItem.getName().equals("Knockback Stick")) {
            meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
        } else if (shopItem.getName().equals("Punch Bow")) {
            meta.addEnchant(Enchantment.ARROW_KNOCKBACK, 1, true);
        } else if (shopItem.getName().equals("Power Bow")) {
            meta.addEnchant(Enchantment.ARROW_DAMAGE, 2, true);
        }
    }

    public boolean handlePurchase(Player player, int slot) {
        // Skip category tab slots
        if (slot < 9) return false;

        ShopCategory currentCategory = getPlayerCategory(player);
        if (currentCategory == null) return false;

        List<ShopItem> categoryItems = shopItems.stream()
                .filter(i -> i.getCategory() == currentCategory)
                .toList();

        int itemIndex = slot - 9;
        if (itemIndex >= categoryItems.size()) return false;

        ShopItem shopItem = categoryItems.get(itemIndex);

        // Check one-per-game
        if (shopItem.isOnePerGame()) {
            Set<String> purchases = playerPurchases.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
            if (purchases.contains(shopItem.getName())) {
                player.sendMessage(MessageUtils.color("&cYou already purchased this item!"));
                return false;
            }
        }

        // Check currency
        if (!hasEnoughCurrency(player, shopItem.getCurrency(), shopItem.getCost())) {
            player.sendMessage(MessageUtils.color("&cYou don't have enough " + shopItem.getCurrencyName() + "!"));
            return false;
        }

        // Deduct currency
        removeCurrency(player, shopItem.getCurrency(), shopItem.getCost());

        // Build item to give
        ItemStack itemToGive = buildPurchasedItem(player, shopItem);
        player.getInventory().addItem(itemToGive);

        if (shopItem.isOnePerGame()) {
            playerPurchases.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(shopItem.getName());
        }

        player.sendMessage(MessageUtils.color("&aYou purchased &f" + shopItem.getName() + "&a!"));
        return true;
    }

    private ItemStack buildPurchasedItem(Player player, ShopItem shopItem) {
        ItemStack item = new ItemStack(shopItem.getMaterial(), shopItem.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        BedwarsTeam team = getPlayerTeam(player);

        // Apply enchantments
        applyEnchantments(shopItem, item, meta, team);

        // Replace wool with team colored wool
        if (shopItem.getMaterial() == Material.WHITE_WOOL && team != null) {
            item = new ItemStack(team.getColor().getWoolMaterial(), shopItem.getAmount());
            meta = item.getItemMeta();
        }

        // Handle potions
        if (shopItem.getMaterial() == Material.POTION) {
            if (meta instanceof PotionMeta potionMeta) {
                if (shopItem.getName().contains("Speed")) {
                    potionMeta.setBasePotionData(new PotionData(PotionType.SPEED));
                } else if (shopItem.getName().contains("Jump")) {
                    potionMeta.setBasePotionData(new PotionData(PotionType.JUMP));
                } else if (shopItem.getName().contains("Invisibility")) {
                    potionMeta.setBasePotionData(new PotionData(PotionType.INVISIBILITY));
                }
            }
        }

        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasEnoughCurrency(Player player, Material currency, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == currency) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeCurrency(Player player, Material currency, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == currency) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    private BedwarsTeam getPlayerTeam(Player player) {
        return game.getPlayerTeam(player.getUniqueId());
    }

    // Track which category the player has open (stored in inventory title parsing)
    private ShopCategory getPlayerCategory(Player player) {
        if (player.getOpenInventory() == null) return null;
        String title = player.getOpenInventory().getTitle();
        for (ShopCategory cat : ShopCategory.values()) {
            if (title.contains(cat.getDisplayName())) {
                return cat;
            }
        }
        return null;
    }

    public boolean isShopInventory(String title) {
        return title.contains("Item Shop");
    }

    public void clearPlayerData(UUID uuid) {
        playerPurchases.remove(uuid);
    }

    public List<ShopItem> getShopItems() {
        return Collections.unmodifiableList(shopItems);
    }

    public ShopItem getItemAtSlot(int slot, ShopCategory category) {
        List<ShopItem> categoryItems = shopItems.stream()
                .filter(i -> i.getCategory() == category)
                .toList();
        int itemIndex = slot - 9;
        if (itemIndex < 0 || itemIndex >= categoryItems.size()) return null;
        return categoryItems.get(itemIndex);
    }
}
