package de.deinname.statsplugin;

import de.deinname.statsplugin.commands.StatsCommand;
import de.deinname.statsplugin.listeners.*;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import de.deinname.statsplugin.util.DamageNumbers;
import de.deinname.statsplugin.abilities.AbilityListener;
import de.deinname.statsplugin.abilities.DamageBoostState;
import de.deinname.statsplugin.commands.ItemAdminCommand;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class StatsPlugin extends JavaPlugin {
    private StatsManager statsManager;
    private StatsStorage storage;
    private DamageNumbers damageNumbers;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        storage = new StatsStorage(this);
        statsManager = new StatsManager();
        statsManager.setStorage(storage);
        damageNumbers = new DamageNumbers(this);

        ItemStatKeys itemKeys = new ItemStatKeys(this);
        ManaManager manaManager = new ManaManager(this);

        // Gamerule – je nach API: typisiert ODER String-Variante
        for (World w : getServer().getWorlds()) {
            try {
                w.setGameRule(GameRule.NATURAL_REGENERATION, Boolean.FALSE);
            } catch (Throwable t) {
                w.setGameRuleValue("naturalRegeneration", "false");
            }
        }

        // Listener
        getServer().getPluginManager().registerEvents(new PlayerListener(statsManager), this);
        getServer().getPluginManager().registerEvents(new DamageListener(statsManager, damageNumbers), this);
        getServer().getPluginManager().registerEvents(new AttackListener(statsManager, damageNumbers), this);
        getServer().getPluginManager().registerEvents(new XPListener(this, statsManager), this);
        getServer().getPluginManager().registerEvents(new ItemRecalcListener(this, statsManager, manaManager, itemKeys), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this, manaManager, itemKeys), this);
        getServer().getPluginManager().registerEvents(new DamageBoostState(), this);
        getServer().getPluginManager().registerEvents(new FoodAndRegenListener(), this);


        // Commands
        // RICHTIG (2 Argumente)
        var statsCmd = new StatsCommand(statsManager, manaManager);
        getCommand("stats").setExecutor(statsCmd);
        getCommand("stats").setTabCompleter(statsCmd);


        var itemCmd = new ItemAdminCommand(this, statsManager, manaManager, itemKeys);
        if (getCommand("itemadmin") != null) {
            getCommand("itemadmin").setExecutor(itemCmd);
            getCommand("itemadmin").setTabCompleter(itemCmd);
        }

        // HUD & Ticks (alle 10 Ticks = 0,5s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (var p : getServer().getOnlinePlayers()) {
                // Mana-Regeneration
                manaManager.tick(p);

                // Eigene Health-Regeneration
                double hpr = statsManager.getTotalHealthRegen(p); // /s
                if (hpr > 0) {
                    double dt = 0.5; // 10 Ticks
                    double add = hpr * dt;
                    double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    double cur = p.getHealth();
                    double next = Math.min(max, cur + add);
                    if (next > cur) p.setHealth(next);
                }

                // Hunger „freezen“
                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.setExhaustion(0f);

                // HUD (ActionBar)
                int hp = (int) Math.ceil(p.getHealth());
                int hpMax = (int) Math.ceil(p.getMaxHealth());
                int m = (int) Math.ceil(manaManager.get(p));
                int mMax = (int) Math.ceil(manaManager.getMax(p));

                Component bar = Component.text()
                        .append(Component.text("❤HP", NamedTextColor.RED))
                        .append(Component.text(hp + "/" + hpMax))
                        .append(Component.text("   |   "))
                        .append(Component.text("✪Mana", NamedTextColor.BLUE))
                        .append(Component.text(m + "/" + mMax))
                        .build();

                p.sendActionBar(bar);
            }
        }, 0L, 10L);

        // Online-Spieler initialisieren
        getServer().getOnlinePlayers().forEach(p -> {
            statsManager.getStats(p);
            statsManager.applyHealth(p);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setExhaustion(0f);
            // KEIN setHealthScaled/setHealthScale mehr -> kein „halbes Herz“
        });

        // Auto-Save
        new BukkitRunnable() { @Override public void run() { statsManager.saveAll(); } }
                .runTaskTimerAsynchronously(this, 20L * 60 * 5, 20L * 60 * 5);

        // XP-Formel
        int max = getConfig().getInt("xp.max-level", 100);
        double base = getConfig().getDouble("xp.base", 100.0);
        double mult = getConfig().getDouble("xp.multiplier", 1.15);
        PlayerLevel.configure(max, base, mult);

        getLogger().info("StatsPlugin aktiviert! Spieler: " + statsManager.getPlayerCount());
    }

    @Override
    public void onDisable() {
        statsManager.saveAll();
        statsManager.clearAll();
    }

    public StatsManager getStatsManager() { return statsManager; }
}
