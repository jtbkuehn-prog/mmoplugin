package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.items.ItemStatUtils;
import de.deinname.statsplugin.items.ItemStats;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemRecalcListener implements Listener {

    private final JavaPlugin plugin;
    private final StatsManager stats;
    private final ManaManager mana;
    private final ItemStatKeys keys;

    public ItemRecalcListener(JavaPlugin plugin, StatsManager stats, ManaManager mana, ItemStatKeys keys) {
        this.plugin = plugin;
        this.stats = stats;
        this.mana = mana;
        this.keys = keys;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        // nach Join einmal sauber rechnen (kein Delay nötig, aber schadet nicht)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recalc(e.getPlayer()), 1L);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e){
        // WICHTIG: erst einen Tick später ist der Mainhand-Slot wirklich umgeschaltet
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recalc(p), 1L);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e){
        // Offhand/Mainhand-Tausch -> auch verzögert neu rechnen
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recalc(p), 1L);
    }

    @EventHandler
    public void onInv(InventoryClickEvent e){
        if (e.getWhoClicked() instanceof Player p){
            // Nach Inventar-Änderungen einen Tick später neu berechnen
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> recalc(p), 1L);
        }
    }

    private void recalc(Player p){
        // PlayerStats holen
        var ps = stats.getStats(p);
        if (ps == null) return;

        // Items summieren (Rüstung + Mainhand + Offhand)
        ItemStats total = ItemStats.zero();
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            total = total.add(ItemStatUtils.read(armor, keys));
        }
        total = total.add(ItemStatUtils.read(p.getInventory().getItemInMainHand(), keys));
        total = total.add(ItemStatUtils.read(p.getInventory().getItemInOffHand(), keys)); // Offhand optional

        // 1) ALLE alten Item-Boni löschen
        ps.clearItemBonuses();

        // 2) Item-Boni in PlayerStats eintragen (NUR itemX-Felder!)
        ps.addItemDamage(total.damage());
        ps.addItemCritChance(total.critChance());
        ps.addItemCritDamage(total.critDamage());
        ps.addItemHealth(total.health());
        ps.addItemArmor(total.armor());
        ps.addItemRange(total.range());
        ps.addItemAttackSpeed(total.attackspeed());
        ps.addItemMana(total.manaMax());          // falls du itemMana nutzen willst
        ps.addItemManaRegen(total.manaRegen());
        ps.addItemHealthRegen(total.healthRegen());
        ps.addItemSpeed(total.speed());

        // 3) Health anhand der neuen Total-Stats anwenden (Base + Items)
        stats.applyHealth(p);
        stats.applySpeed(p);

        // 4) Mana separat über ManaManager (wie vorher)
        int lvl = stats.getLevel(p).getLevel();
        double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0)
                + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0)
                + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);

        mana.setMax(p,   baseMax   + total.manaMax());
        mana.setRegen(p, baseRegen + total.manaRegen());
    }

}
