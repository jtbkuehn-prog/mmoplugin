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
import org.bukkit.plugin.Plugin;

public class ItemRecalcListener implements Listener {

    private final StatsManager stats;
    private final ManaManager mana;
    private final ItemStatKeys keys;
    private final Plugin plugin;

    public ItemRecalcListener(Plugin plugin, StatsManager stats, ManaManager mana, ItemStatKeys keys) {
        this.plugin = plugin;
        this.stats = stats;
        this.mana = mana;
        this.keys = keys;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        int lvl = stats.getLevel(p.getUniqueId()).getLevel();

        double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0)
                + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0)
                + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);

        mana.init(p, baseMax, baseRegen); // setzt current = base
        // 1 Tick später recalc (Inventar ist dann konsistent)
        plugin.getServer().getScheduler().runTask(plugin, () -> recalc(p, baseMax, baseRegen));
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        // HeldEvent kommt VOR dem echten Slotwechsel -> 1 Tick später recalc
        Player p = e.getPlayer();
        int lvl = stats.getLevel(p.getUniqueId()).getLevel();
        double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0)
                + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0)
                + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);

        plugin.getServer().getScheduler().runTask(plugin, () -> recalc(p, baseMax, baseRegen));
    }

    @EventHandler
    public void onInv(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int lvl = stats.getLevel(p.getUniqueId()).getLevel();
        double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0)
                + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0)
                + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);
        // Nach dem Click ist das Item erst NACH dem Tick korrekt -> verzögern
        plugin.getServer().getScheduler().runTask(plugin, () -> recalc(p, baseMax, baseRegen));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        int lvl = stats.getLevel(p.getUniqueId()).getLevel();
        double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0)
                + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
        double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0)
                + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);
        plugin.getServer().getScheduler().runTask(plugin, () -> recalc(p, baseMax, baseRegen));
    }

    private void recalc(Player p, double baseMax, double baseRegen) {
        ItemStats total = ItemStats.zero();

        // Armor
        for (ItemStack is : p.getInventory().getArmorContents()) {
            total = total.add(ItemStatUtils.read(is, keys));
        }
        // Mainhand (jetzt korrekt, weil 1 Tick später)
        total = total.add(ItemStatUtils.read(p.getInventory().getItemInMainHand(), keys));

        // Item-Boni in Stats einspeisen (Delta-Logik im StatsManager)
        stats.applyItemBonuses(p.getUniqueId(),
                total.damage(), total.critChance(), total.critDamage(),
                total.health(), total.armor(), total.range());

        // >>> NEU/WICHTIG: Spieler-MaxHealth sofort aktualisieren
        stats.applyHealth(p);  // <— diese Zeile sorgt dafür, dass Health sofort wirkt

        // Mana: niemals stapeln – immer base + items
        mana.setMax(p,   baseMax   + total.manaMax());
        mana.setRegen(p, baseRegen + total.manaRegen());

        // optionales Feedback
        p.sendActionBar(Component.text("Stats aktualisiert", NamedTextColor.GREEN));
    }
}
