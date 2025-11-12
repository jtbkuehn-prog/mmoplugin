package de.deinname.statsplugin.abilities;

import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.items.ItemStatUtils;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.Particle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AbilityListener implements Listener {

    private final Plugin plugin;
    private final ManaManager mana;
    private final ItemStatKeys keys;
    private final Map<UUID, Long> cd = new HashMap<>();

    public AbilityListener(Plugin plugin, ManaManager mana, ItemStatKeys keys) {
        this.plugin = plugin;
        this.mana = mana;
        this.keys = keys;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // nur Mainhand-Klicks (verhindert doppelte Trigger mit Offhand)
        if (e.getHand() != EquipmentSlot.HAND) return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        String ability = ItemStatUtils.readAbility(hand, keys);
        if (!"damage_boost".equalsIgnoreCase(ability)) return;

        // Cooldown
        long now = System.currentTimeMillis();
        long readyAt = cd.getOrDefault(p.getUniqueId(), 0L);
        long cdMs = plugin.getConfig().getLong("ability.damage_boost.cooldown-ticks", 100) * 50L;
        if (now < readyAt) {
            long rest = (readyAt - now) / 1000;
            p.sendActionBar(Component.text("Cooldown: " + rest + "s"));
            return;
        }

        // Kosten
        double cost = plugin.getConfig().getDouble("ability.damage_boost.cost", 25.0);
        if (!mana.consume(p, cost)) {
            p.sendActionBar(Component.text("Nicht genug Mana (" + (int)cost + ")"));
            return;
        }

        // Aktivieren
        int duration = (int) plugin.getConfig().getLong("ability.damage_boost.duration-ticks", 60);
        double mult = plugin.getConfig().getDouble("ability.damage_boost.multiplier", 1.5);
        DamageBoostState.activate(plugin, p.getUniqueId(), duration, mult);
        cd.put(p.getUniqueId(), now + cdMs);

        // optisches Feedback
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0,1,0), 12, 0,0,0, 0);
    }
}
