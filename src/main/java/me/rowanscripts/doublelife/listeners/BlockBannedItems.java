package me.rowanscripts.doublelife.listeners;

import me.rowanscripts.doublelife.DoubleLife;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;

public class BlockBannedItems implements Listener {

    static World currentWorld;

    @EventHandler
    public void discoverRecipes(PlayerJoinEvent event){event.getPlayer().discoverRecipes(DoubleLife.recipeKeys); currentWorld = event.getPlayer().getWorld();}

    public static void startKillVillagersLoop() {
        if (!DoubleLife.plugin.getConfig().getBoolean("misc.kill-villagers"))
            return;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(DoubleLife.plugin, () -> {
            if (currentWorld != null) {
                for (Entity entity : currentWorld.getEntities()) {
                    if (entity.getType() == EntityType.VILLAGER) {
                        entity.remove();
                    }
                }
            }
        }, 100, 50);
    }

    @EventHandler
    public void blockEnchantedGoldenApples(InventoryClickEvent event) {
        ItemStack itemStack = event.getCurrentItem();
        if (itemStack == null)
            return;

        if (itemStack.getType() == Material.ENCHANTED_GOLDEN_APPLE && DoubleLife.plugin.getConfig().getBoolean("items.ban-god-apples")) {

            itemStack.setAmount(0);
            for (HumanEntity viewer : event.getClickedInventory().getViewers()) {
                Player cheater = (Player) viewer;
                cheater.sendMessage(ChatColor.DARK_RED + "Enchanted Golden Apples aren't allowed!");
            }
        }
    }

    @EventHandler
    public void blockVillagerSpawns(EntitySpawnEvent event) {
        if (!DoubleLife.plugin.getConfig().getBoolean("misc.kill-villagers"))
            return;
        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.VILLAGER || entity.getType() == EntityType.ZOMBIE_VILLAGER)
            event.setCancelled(true);
    }

    @EventHandler
    public void blockEnchantmentTableRecipe(CraftItemEvent event) {
        Material recipeType = event.getRecipe().getResult().getType();
        if (recipeType == Material.ENCHANTING_TABLE && !DoubleLife.plugin.getConfig().getBoolean("enchantments.enchantment-table-craftable"))
            event.setCancelled(true);
        else if (recipeType == Material.BOOKSHELF && !DoubleLife.plugin.getConfig().getBoolean("enchantments.bookshelves-craftable"))
            event.setCancelled(true);
    }

    @SuppressWarnings("unchecked")
    @EventHandler
    public void blockBannedPotions(InventoryClickEvent event) {
        if (!DoubleLife.plugin.getConfig().getBoolean("items.potions.whitelist-enabled"))
            return;

        ItemStack itemStack = event.getCurrentItem();
        if (itemStack == null)
            return;

        if (itemStack.getType() == Material.POTION || itemStack.getType() == Material.SPLASH_POTION) {
            PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
            PotionType potionType = potionMeta.getBasePotionData().getType();
            List<String> potionWhitelist = (List<String>) DoubleLife.plugin.getConfig().getList("items.potions.whitelist");
            if (potionWhitelist == null) return;
            System.out.println(potionType);
            if (!potionWhitelist.contains(potionType.toString())) {
                event.setCancelled(true);
                itemStack.setType(Material.GLASS_BOTTLE);
                for (HumanEntity viewer : event.getClickedInventory().getViewers()) {
                    Player cheater = (Player) viewer;
                    cheater.sendMessage(ChatColor.DARK_RED + "That potion is banned on this server!");
                }
            }
        }
    }

    HashMap<UUID, Map<Enchantment, Integer>> justGotNerfed = new HashMap<>();
    public void nerfEnchantmentLevel(Map<Enchantment, Integer> enchantments, List<HumanEntity> viewers, ItemStack resultItem, PrepareAnvilEvent event) {
        StringBuilder nerfedEnchantmentsMessage = new StringBuilder(ChatColor.DARK_RED + "The following enchantments have been nerfed:\n" + ChatColor.GRAY);
        Map<Enchantment, Integer> nerfedEnchantments = new HashMap<>();
        ItemStack nerfedItem = new ItemStack(resultItem.getType());

        ItemMeta resultItemMeta = resultItem.getItemMeta();
        if (resultItemMeta.hasDisplayName()){
            ItemMeta nerfedItemMeta = nerfedItem.getItemMeta();
            nerfedItemMeta.setDisplayName(resultItemMeta.getDisplayName());
            nerfedItem.setItemMeta(nerfedItemMeta);
        }

        if (resultItem.getType() != Material.ENCHANTED_BOOK) {
            Damageable resultItemDamageAble = (Damageable) resultItem.getItemMeta();
            Damageable nerfedItemDamageAble = (Damageable) nerfedItem.getItemMeta();
            nerfedItemDamageAble.setDamage(resultItemDamageAble.getDamage());
            nerfedItem.setItemMeta(nerfedItemDamageAble);
        }

        for (Map.Entry<Enchantment, Integer> enchantment : enchantments.entrySet()) {
            if (enchantment.getValue() > 2) {
                nerfedEnchantments.put(enchantment.getKey(), 2);
                nerfedEnchantmentsMessage.append(enchantment.getKey().getKey().toString().replace("minecraft:", "")).append(", ");
            } else {
                nerfedEnchantments.put(enchantment.getKey(), enchantment.getValue());
            }
        }
        nerfedItem.addUnsafeEnchantments(nerfedEnchantments);
        event.setResult(nerfedItem);

        for (HumanEntity viewer : viewers) {
            UUID viewerUUID = viewer.getUniqueId();
            if (justGotNerfed.containsKey(viewerUUID))
                break;

            justGotNerfed.put(viewerUUID, nerfedEnchantments);
            Bukkit.getScheduler().runTaskLater(DoubleLife.plugin, () -> justGotNerfed.remove(viewerUUID), 60);
            if (!nerfedEnchantmentsMessage.toString().equals(ChatColor.DARK_RED + "The following enchantments have been nerfed:\n" + ChatColor.GRAY))
                viewer.sendMessage(String.valueOf(nerfedEnchantmentsMessage));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void modifyIllegalEnchantments(PrepareAnvilEvent event) {
        if (!DoubleLife.plugin.getConfig().getBoolean("enchantments.nerf-enchantment-stacking"))
            return;

        ItemStack resultItem = event.getResult();
        if (resultItem == null)
            return;

        if (resultItem.getType() == Material.ENCHANTED_BOOK){
            EnchantmentStorageMeta enchantmentStorageMeta = (EnchantmentStorageMeta) resultItem.getItemMeta();
            if (enchantmentStorageMeta != null) {
                Map<Enchantment, Integer> enchantments = enchantmentStorageMeta.getStoredEnchants();
                nerfEnchantmentLevel(enchantments, event.getInventory().getViewers(), resultItem, event);
            }
        } else {
            ItemMeta itemMeta = resultItem.getItemMeta();
            if (itemMeta != null){
                if (!itemMeta.getEnchants().isEmpty()){
                    Map<Enchantment, Integer> enchantments = itemMeta.getEnchants();
                    nerfEnchantmentLevel(enchantments, event.getInventory().getViewers(), resultItem, event);
                }
            }
        }
    }

    @EventHandler
    public void removeHelmets(InventoryClickEvent event) {
        if (!DoubleLife.plugin.getConfig().getBoolean("items.ban-helmets"))
            return;

        ItemStack item = event.getCurrentItem();
        if (item != null) {
            if (item.getType() == Material.CHAINMAIL_HELMET || item.getType() == Material.IRON_HELMET || item.getType() == Material.LEATHER_HELMET || item.getType() == Material.DIAMOND_HELMET || item.getType() == Material.GOLDEN_HELMET || item.getType() == Material.TURTLE_HELMET || item.getType() == Material.NETHERITE_HELMET){
                event.setCancelled(true);
                item.setAmount(0);
                for (HumanEntity viewer : event.getInventory().getViewers()) {
                    Player cheater = (Player) viewer;
                    cheater.sendMessage(ChatColor.DARK_RED + "Helmets are banned!");
                }
            }
        }
    }

    @EventHandler
    public void makeEnchantmentTableIndestructibleOnDrop(PlayerDropItemEvent event) {
        if (!DoubleLife.plugin.getConfig().getBoolean("enchantments.enchantment-table-indestructible-on-drop"))
            return;
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (droppedItem.getType() == Material.ENCHANTING_TABLE){
            Item item = event.getItemDrop();
            item.setCustomName("Invulnerable");
            item.setUnlimitedLifetime(true);
            item.setInvulnerable(true);
        }
    }

    @EventHandler
    public void preventExplodingOfEnchantmentTable(EntityDamageByEntityEvent event){
        if (!DoubleLife.plugin.getConfig().getBoolean("enchantments.enchantment-table-indestructible-on-drop"))
            return;
        for (Entity entity : event.getDamager().getNearbyEntities(10,10,10)) {
            if (entity.getType() == EntityType.DROPPED_ITEM){
                if (entity.getCustomName() != null) {
                    if (entity.getCustomName().equalsIgnoreCase("Invulnerable")) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void blockDestroyingEnchantmentTable(BlockBreakEvent event) {
        if (DoubleLife.plugin.getConfig().getBoolean("enchantments.enchantment-table-breakable"))
            return;

        if (event.getBlock().getType() == Material.ENCHANTING_TABLE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.DARK_RED +"You're not allowed to break the enchantment table!");
        }
    }

}