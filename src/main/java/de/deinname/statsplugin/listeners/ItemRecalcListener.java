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
        // Items summieren (Rüstung + Mainhand + Offhand, falls du Offhand-Stats willst)
        ItemStats total = ItemStats.zero();
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            total = total.add(ItemStatUtils.read(armor, keys));
        }
        total = total.add(ItemStatUtils.read(p.getInventory().getItemInMainHand(), keys));
        total = total.add(ItemStatUtils.read(p.getInventory().getItemInOffHand(), keys)); // optional — rausnehmen, wenn Offhand nichts geben soll

        // Item-Werte in Manager übernehmen
        stats.applyItemBonuses(
                p.getUniqueId(),
                total.damage(), total.critChance(), total.critDamage(),
                total.health(), total.armor(), total.range(), total.attackspeed()
        );
        stats.applyHealth(p);

        // Item-HealthRegen in die Item-Map (du hattest 1–4 schon umgesetzt)
        stats.setItemHealthRegen(p.getUniqueId(), total.healthRegen());

        // Mana (base aus Level + items)
        int lvl = stats.getLevel(p).getLevel();
        double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0) + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0) + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);
        mana.setMax(p,   baseMax   + total.manaMax());
        mana.setRegen(p, baseRegen + total.manaRegen());

        p.sendActionBar(Component.text("Stats aktualisiert", NamedTextColor.GREEN));
    }
}
