package com.bedwars.shop;

import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.TrapType;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class UpgradeShopManager {

    private final BedwarsGame game;

    // Slot assignments
    private static final int SLOT_SHARPNESS = 10;
    private static final int SLOT_PROTECTION = 11;
    private static final int SLOT_FORGE = 12;
    private static final int SLOT_HASTE = 13;
    private static final int SLOT_HEAL_POOL = 14;
    private static final int SLOT_REGEN_TRAP = 20;
    private static final int SLOT_COUNTER_OFFENSE = 21;
    private static final int SLOT_ALARM = 22;
    private static final int SLOT_MINER_FATIGUE = 23;

    // Costs in diamonds
    private static final int[] SHARPNESS_COSTS = {4, 8, 16, 32};
    private static final int[] PROTECTION_COSTS = {4, 8, 16};
    private static final int[] FORGE_COSTS = {4, 8, 16, 32};
    private static final int[] HASTE_COSTS = {2, 4};
    private static final int HEAL_POOL_COST = 1; // emerald

    public UpgradeShopManager(BedwarsGame game) {
        this.game = game;
    }

    public void openUpgradeShop(Player player) {
        BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());
        if (team == null) return;

        Inventory inv = game.getPlugin().getServer().createInventory(null, 54,
                MessageUtils.color("&6&lTeam Upgrades"));

        fillBackground(inv);

        // Sharpness
        inv.setItem(SLOT_SHARPNESS, buildUpgradeItem(
                "Sharpened Swords",
                Material.IRON_SWORD,
                team.getSharpenLevel(),
                4,
                SHARPNESS_COSTS,
                Material.DIAMOND,
                new String[]{"Gives your team's swords", "a Sharpness enchantment."}
        ));

        // Protection
        inv.setItem(SLOT_PROTECTION, buildUpgradeItem(
                "Reinforced Armor",
                Material.IRON_CHESTPLATE,
                team.getProtectionLevel(),
                3,
                PROTECTION_COSTS,
                Material.DIAMOND,
                new String[]{"Gives your team's armor", "a Protection enchantment."}
        ));

        // Forge
        inv.setItem(SLOT_FORGE, buildForgeItem(team));

        // Haste
        inv.setItem(SLOT_HASTE, buildUpgradeItem(
                "Maniac Miner",
                Material.GOLDEN_PICKAXE,
                team.getHasteLevel(),
                2,
                HASTE_COSTS,
                Material.DIAMOND,
                new String[]{"Gives your team Haste", "when near their island."}
        ));

        // Heal Pool
        inv.setItem(SLOT_HEAL_POOL, buildHealPoolItem(team));

        // Traps — show queue size
        int queueSize = team.getTrapQueueSize();
        String queueNote = queueSize > 0 ? " &8(&e" + queueSize + " queued&8)" : "";

        inv.setItem(SLOT_REGEN_TRAP, buildTrapItem("It's a Trap!" + queueNote, Material.TRIPWIRE_HOOK,
                "&7Gives your team Regen II", "&7for 10 seconds on trigger.", Material.GOLD_INGOT, 1));
        inv.setItem(SLOT_COUNTER_OFFENSE, buildTrapItem("Counter-Offensive Trap" + queueNote, Material.FEATHER,
                "&7Gives your team Speed II", "&7and Jump II for 10 seconds.", Material.GOLD_INGOT, 2));
        inv.setItem(SLOT_ALARM, buildTrapItem("Alarm Trap" + queueNote, Material.TRIPWIRE_HOOK,
                "&7Alerts your team when", "&7an enemy enters your island.", Material.GOLD_INGOT, 1));
        inv.setItem(SLOT_MINER_FATIGUE, buildTrapItem("Miner Fatigue Trap" + queueNote, Material.IRON_PICKAXE,
                "&7Gives Miner Fatigue III", "&7to enemies in your base.", Material.GOLD_INGOT, 2));

        player.openInventory(inv);
    }

    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
    }

    private ItemStack buildUpgradeItem(String name, Material material, int currentLevel, int maxLevel,
                                        int[] costs, Material currency, String[] description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageUtils.color("&e" + name));

        List<String> lore = new ArrayList<>();
        for (String desc : description) {
            lore.add(MessageUtils.color("&7" + desc));
        }
        lore.add("");

        for (int i = 0; i < maxLevel; i++) {
            String tier = "Tier " + (i + 1);
            if (i < currentLevel) {
                lore.add(MessageUtils.color("&a✔ " + tier + " &7- Purchased"));
            } else if (i == currentLevel) {
                lore.add(MessageUtils.color("&e➜ " + tier + " &7- " + costs[i] + " " + currency.name()));
            } else {
                lore.add(MessageUtils.color("&8✗ " + tier + " &7- " + costs[i] + " " + currency.name()));
            }
        }

        if (currentLevel >= maxLevel) {
            lore.add("");
            lore.add(MessageUtils.color("&aMAX LEVEL!"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildForgeItem(BedwarsTeam team) {
        ItemStack item = new ItemStack(Material.FURNACE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageUtils.color("&eForge"));
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color("&7Upgrades your team's"));
        lore.add(MessageUtils.color("&7resource forge."));
        lore.add("");

        String[] tiers = {"Iron Forge", "Golden Forge", "Emerald Forge", "Molten Forge"};
        for (int i = 0; i < tiers.length; i++) {
            if (i < team.getForgeLevel()) {
                lore.add(MessageUtils.color("&a✔ " + tiers[i]));
            } else if (i == team.getForgeLevel()) {
                int cost = FORGE_COSTS[Math.min(i, FORGE_COSTS.length - 1)];
                lore.add(MessageUtils.color("&e➜ " + tiers[i] + " &7- " + cost + " Diamonds"));
            } else {
                lore.add(MessageUtils.color("&8✗ " + tiers[i]));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildHealPoolItem(BedwarsTeam team) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageUtils.color("&eHeal Pool"));
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color("&7Creates a regeneration"));
        lore.add(MessageUtils.color("&7field around your island."));
        lore.add("");
        if (team.hasHealPool()) {
            lore.add(MessageUtils.color("&aAlready purchased!"));
        } else {
            lore.add(MessageUtils.color("&eCost: &f1 Emerald"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTrapItem(String name, Material material, String line1, String line2,
                                     Material currency, int cost) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageUtils.color("&e" + name));
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.color(line1));
        lore.add(MessageUtils.color(line2));
        lore.add("");
        lore.add(MessageUtils.color("&eCost: &f" + cost + " " + currency.name()));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void handleUpgradePurchase(Player player, int slot) {
        BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());
        if (team == null) return;

        switch (slot) {
            case SLOT_SHARPNESS -> purchaseSharpness(player, team);
            case SLOT_PROTECTION -> purchaseProtection(player, team);
            case SLOT_FORGE -> purchaseForge(player, team);
            case SLOT_HASTE -> purchaseHaste(player, team);
            case SLOT_HEAL_POOL -> purchaseHealPool(player, team);
            case SLOT_REGEN_TRAP -> purchaseTrap(player, team, TrapType.REGEN_BOOST, 1);
            case SLOT_COUNTER_OFFENSE -> purchaseTrap(player, team, TrapType.COUNTER_OFFENSE, 2);
            case SLOT_ALARM -> purchaseTrap(player, team, TrapType.ALARM, 1);
            case SLOT_MINER_FATIGUE -> purchaseTrap(player, team, TrapType.MINER_FATIGUE, 2);
        }
    }

    private void purchaseSharpness(Player player, BedwarsTeam team) {
        int level = team.getSharpenLevel();
        if (level >= 4) {
            player.sendMessage(MessageUtils.color("&cSharpened Swords is already at max level!"));
            return;
        }
        int cost = SHARPNESS_COSTS[level];
        if (!removeDiamonds(player, cost)) {
            player.sendMessage(MessageUtils.color("&cYou need " + cost + " diamonds!"));
            return;
        }
        team.setSharpenLevel(level + 1);
        game.broadcastToTeam(team, MessageUtils.color("&aYour team unlocked &eSharpened Swords Tier " + (level + 1) + "!"));
    }

    private void purchaseProtection(Player player, BedwarsTeam team) {
        int level = team.getProtectionLevel();
        if (level >= 3) {
            player.sendMessage(MessageUtils.color("&cReinforced Armor is already at max level!"));
            return;
        }
        int cost = PROTECTION_COSTS[level];
        if (!removeDiamonds(player, cost)) {
            player.sendMessage(MessageUtils.color("&cYou need " + cost + " diamonds!"));
            return;
        }
        team.setProtectionLevel(level + 1);
        game.broadcastToTeam(team, MessageUtils.color("&aYour team unlocked &eReinforced Armor Tier " + (level + 1) + "!"));
    }

    private void purchaseForge(Player player, BedwarsTeam team) {
        int level = team.getForgeLevel();
        if (level >= 4) {
            player.sendMessage(MessageUtils.color("&cForge is already at max level!"));
            return;
        }
        int cost = FORGE_COSTS[Math.min(level, FORGE_COSTS.length - 1)];
        if (!removeDiamonds(player, cost)) {
            player.sendMessage(MessageUtils.color("&cYou need " + cost + " diamonds!"));
            return;
        }
        team.setForgeLevel(level + 1);
        String[] forgeNames = {"Iron Forge", "Golden Forge", "Emerald Forge", "Molten Forge"};
        game.broadcastToTeam(team, MessageUtils.color("&aYour team unlocked &e" + forgeNames[level] + "!"));
    }

    private void purchaseHaste(Player player, BedwarsTeam team) {
        int level = team.getHasteLevel();
        if (level >= 2) {
            player.sendMessage(MessageUtils.color("&cManiac Miner is already at max level!"));
            return;
        }
        int cost = HASTE_COSTS[level];
        if (!removeDiamonds(player, cost)) {
            player.sendMessage(MessageUtils.color("&cYou need " + cost + " diamonds!"));
            return;
        }
        team.setHasteLevel(level + 1);
        game.broadcastToTeam(team, MessageUtils.color("&aYour team unlocked &eManiac Miner Tier " + (level + 1) + "!"));
    }

    private void purchaseTrap(Player player, BedwarsTeam team, TrapType trap, int goldCost) {
        if (team.getTrapQueueSize() >= 3) {
            player.sendMessage(MessageUtils.color("&cYour trap queue is full! (max 3)"));
            return;
        }
        if (!removeGold(player, goldCost)) {
            player.sendMessage(MessageUtils.color("&cYou need " + goldCost + " gold!"));
            return;
        }
        team.queueTrap(trap);
        game.broadcastToTeam(team, MessageUtils.color("&aYour team queued a &e" + trap.getDisplayName() +
                "&a! (&f" + team.getTrapQueueSize() + "&a in queue)"));
    }

    private boolean removeGold(Player player, int amount) {
        return removeMaterial(player, Material.GOLD_INGOT, amount);
    }

    private void purchaseHealPool(Player player, BedwarsTeam team) {
        if (team.hasHealPool()) {
            player.sendMessage(MessageUtils.color("&cHeal Pool is already purchased!"));
            return;
        }
        if (!removeEmeralds(player, HEAL_POOL_COST)) {
            player.sendMessage(MessageUtils.color("&cYou need " + HEAL_POOL_COST + " emerald(s)!"));
            return;
        }
        team.setHealPool(true);
        game.broadcastToTeam(team, MessageUtils.color("&aYour team unlocked &eHeal Pool!"));
    }

    private boolean removeDiamonds(Player player, int amount) {
        return removeMaterial(player, Material.DIAMOND, amount);
    }

    private boolean removeEmeralds(Player player, int amount) {
        return removeMaterial(player, Material.EMERALD, amount);
    }

    private boolean removeMaterial(Player player, Material material, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        if (count < amount) return false;

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
        return true;
    }

    public boolean isUpgradeShopInventory(String title) {
        return title.contains("Team Upgrades");
    }
}
