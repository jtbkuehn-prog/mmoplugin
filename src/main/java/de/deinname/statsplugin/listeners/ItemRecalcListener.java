package de.deinname.statsplugin.listeners;


import de.deinname.statsplugin.items.*;
import de.deinname.statsplugin.mana.ManaManager;
import de.deinname.statsplugin.StatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;


public class ItemRecalcListener implements Listener {
    private final StatsManager stats; private final ManaManager mana; private final ItemStatKeys keys; private final Plugin plugin;


    public ItemRecalcListener(Plugin plugin, StatsManager stats, ManaManager mana, ItemStatKeys keys){
        this.plugin = plugin; this.stats = stats; this.mana = mana; this.keys = keys;
    }


    @EventHandler public void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();
        int lvl = stats.getLevel(p.getUniqueId()).getLevel();
        double baseMax = plugin.getConfig().getDouble("mana.base-max", 50.0) + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0) + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);
        mana.init(p, baseMax, baseRegen); // setzt CUR auf base
        recalc(p, baseMax, baseRegen);
    }


    @EventHandler public void onHeld(PlayerItemHeldEvent e){
        Player p = e.getPlayer();
        int lvl = stats.getLevel(p.getUniqueId()).getLevel();
        double baseMax = plugin.getConfig().getDouble("mana.base-max", 50.0) + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0) + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);
        recalc(p, baseMax, baseRegen);
    }


    @EventHandler public void onInv(InventoryClickEvent e){
        if (e.getWhoClicked() instanceof Player p){
            int lvl = stats.getLevel(p.getUniqueId()).getLevel();
            double baseMax = plugin.getConfig().getDouble("mana.base-max", 50.0) + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
            double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0) + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);
            plugin.getServer().getScheduler().runTask(plugin, ()-> recalc(p, baseMax, baseRegen));
        }
    }


    private void recalc(Player p, double baseMax, double baseRegen){
        ItemStats total = ItemStats.zero();
        for (ItemStack is : p.getInventory().getArmorContents()) total = total.add(ItemStatUtils.read(is, keys));
        total = total.add(ItemStatUtils.read(p.getInventory().getItemInMainHand(), keys));


// Item-Boni in Stats einspeisen (separat, additiv per Delta in StatsManager)
        stats.applyItemBonuses(p.getUniqueId(), total.damage(), total.critChance(), total.critDamage(), total.health(), total.armor(), total.range());


// Mana NICHT additiv stapeln → immer neu: base + itemBonus
        mana.setMax(p, baseMax + total.manaMax());
        mana.setRegen(p, baseRegen + total.manaRegen());


// Kleine HUD-Bestätigung (optional)
        p.sendActionBar(Component.text("Stats aktualisiert", NamedTextColor.GREEN));
    }
}