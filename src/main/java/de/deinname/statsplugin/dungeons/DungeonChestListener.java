package de.deinname.statsplugin.dungeons;

import de.deinname.statsplugin.loot.LootTableManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class DungeonChestListener implements Listener {

    private final JavaPlugin plugin;
    private final LootTableManager lootManager;
    private final NamespacedKey KEY_DUNGEON_CHEST;
    private final NamespacedKey KEY_ALREADY_ROLLED;

    public DungeonChestListener(JavaPlugin plugin,
                                LootTableManager lootManager,
                                NamespacedKey keyDungeonChest,
                                NamespacedKey keyAlreadyRolled) {
        this.plugin = plugin;
        this.lootManager = lootManager;
        this.KEY_DUNGEON_CHEST = keyDungeonChest;
        this.KEY_ALREADY_ROLLED = keyAlreadyRolled;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChestOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;

        Block b = e.getClickedBlock();
        if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST) return;
        if (!(b.getState() instanceof Chest chest)) return;

        Player p = e.getPlayer();

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        String tableName = pdc.get(KEY_DUNGEON_CHEST, PersistentDataType.STRING);
        if (tableName == null || tableName.isEmpty()) {
            // keine Dungeon-Chest → Vanilla machen lassen
            return;
        }

        plugin.getLogger().info("[DungeonChest] Dungeon-Kiste geklickt bei "
                + b.getX() + "," + b.getY() + "," + b.getZ() + " | table=" + tableName);

        // Schon gerollt?
        Byte rolled = pdc.get(KEY_ALREADY_ROLLED, PersistentDataType.BYTE);
        if (rolled != null && rolled == (byte) 1) {
            p.sendMessage("§7Diese Truhe wurde bereits geplündert.");
            e.setCancelled(true);
            return;
        }

        // Vanilla-Opening ABBRECHEN
        e.setCancelled(true);

        // 1) Neues Inventar nur für den Spieler
        Inventory inv = Bukkit.createInventory(
                null,
                27,
                Component.text("Dungeon Chest")
        );

        // 2) Loot generieren
        List<ItemStack> items = lootManager.generate(tableName);
        plugin.getLogger().info("[DungeonChest] GUI-Loot size=" + items.size());

        for (ItemStack it : items) {
            inv.addItem(it);
        }

        // 3) Debug: wenn leer, trotzdem Test-Item rein
        if (inv.isEmpty()) {
            plugin.getLogger().warning("[DungeonChest] GUI war leer, lege Debug-Diamanten rein.");
            inv.setItem(13, new ItemStack(Material.DIAMOND, 3));
        }

        // 4) GUI öffnen
        p.openInventory(inv);

        // 5) Flag setzen, damit nicht mehrfach geplündert wird
        pdc.set(KEY_ALREADY_ROLLED, PersistentDataType.BYTE, (byte) 1);
        chest.update(true, false);
    }
}
