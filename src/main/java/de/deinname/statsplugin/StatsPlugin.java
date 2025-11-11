package de.deinname.statsplugin;

import de.deinname.statsplugin.commands.StatsCommand;
import de.deinname.statsplugin.listeners.AttackListener;
import de.deinname.statsplugin.listeners.DamageListener;
import de.deinname.statsplugin.listeners.PlayerListener;
import de.deinname.statsplugin.listeners.XPListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import de.deinname.statsplugin.util.DamageNumbers;
import de.deinname.statsplugin.abilities.AbilityListener;
import de.deinname.statsplugin.abilities.DamageBoostState;
//import de.deinname.statsplugin.abilities.DamageBoostApplier; // <— NEU (optional)
import de.deinname.statsplugin.commands.ItemAdminCommand;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.listeners.ItemRecalcListener;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class StatsPlugin extends JavaPlugin {
    private StatsManager statsManager;
    private StatsStorage storage;
    private DamageNumbers damageNumbers;

    @Override
    public void onEnable() {
        // Storage
        getLogger().info("Initialisiere StatsStorage...");
        storage = new StatsStorage(this);
        getLogger().info("StatsStorage initialisiert!");

        // Manager
        getLogger().info("Initialisiere StatsManager...");
        statsManager = new StatsManager();
        statsManager.setStorage(storage);
        getLogger().info("StatsManager initialisiert!");

        damageNumbers = new DamageNumbers(this);

        ItemStatKeys itemKeys = new ItemStatKeys(this);
        ManaManager manaManager = new ManaManager(this);

        // Listener
        getServer().getPluginManager().registerEvents(new PlayerListener(statsManager), this);

        // Combat/Schaden zuerst
        getServer().getPluginManager().registerEvents(new DamageListener(statsManager, damageNumbers), this);
        getServer().getPluginManager().registerEvents(new AttackListener(statsManager, damageNumbers), this);

        // XP (Mob-Kills etc.)
        getServer().getPluginManager().registerEvents(new XPListener(statsManager), this);

        // Items & Mana-Recalc
        getServer().getPluginManager().registerEvents(new ItemRecalcListener(this, statsManager, manaManager, itemKeys), this);

        // Abilities (Rightclick, Mana-Kosten)
        getServer().getPluginManager().registerEvents(new AbilityListener(this, manaManager, itemKeys), this);

        // Damage-Boost Zustand (setzt Multiplier) – nach Combat-Listenern
        getServer().getPluginManager().registerEvents(new DamageBoostState(), this);

        // OPTIONALER „Sicherheitsgurt“: ganz am Ende nochmal multipizieren
        // Falls ein später registrierter Listener den Schaden überschreibt
       // getServer().getPluginManager().registerEvents(new DamageBoostApplier(), this); // <— NEU (optional)

        // Commands
        var statsCmd = new StatsCommand(statsManager, manaManager);
        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(statsCmd);
            getCommand("stats").setTabCompleter(statsCmd);
        } else {
            getLogger().warning("Command 'stats' nicht in plugin.yml gefunden!");
        }

        var itemCmd = new ItemAdminCommand(this, statsManager, manaManager, itemKeys);
        getCommand("itemadmin").setExecutor(itemCmd);
        getCommand("itemadmin").setTabCompleter(itemCmd);


        // HUD & Mana-Regen (alle 10 Ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            getServer().getOnlinePlayers().forEach(p -> {
                // Tick-Regeneration
                manaManager.tick(p);

                int hp = (int) Math.ceil(p.getHealth());
                int hpMax = (int) Math.ceil(p.getMaxHealth());
                int m = (int) Math.ceil(manaManager.get(p));
                int mMax = (int) Math.ceil(manaManager.getMax(p));

                Component bar = Component.text()
                        .append(Component.text("❤HP ", NamedTextColor.RED))
                        .append(Component.text(hp + "/" + hpMax))
                        .append(Component.text("   |   "))
                        .append(Component.text("✪Mana ", NamedTextColor.BLUE))
                        .append(Component.text(m + "/" + mMax))
                        .build();

                p.sendActionBar(bar);
            });
        }, 0L, 10L);

        // Bereits online?
        getServer().getOnlinePlayers().forEach(p -> {
            statsManager.getStats(p);
            statsManager.applyHealth(p);
        });

        // Auto-Save alle 5 Minuten
        new BukkitRunnable() {
            @Override public void run() { statsManager.saveAll(); }
        }.runTaskTimerAsynchronously(this, 20L * 60 * 5, 20L * 60 * 5);

        getLogger().info("StatsPlugin aktiviert! Spieler geladen: " + statsManager.getPlayerCount());

        // XP-/Level-Kurve aus Config
        int max = getConfig().getInt("xp.max-level", 100);
        double base = getConfig().getDouble("xp.base", 100.0);
        double mult = getConfig().getDouble("xp.multiplier", 1.15);
        PlayerLevel.configure(max, base, mult);
    }

    @Override
    public void onDisable() {
        getLogger().info("Speichere alle Stats & Level...");
        statsManager.saveAll();
        statsManager.clearAll();
        getLogger().info("StatsPlugin deaktiviert!");
    }

    public StatsManager getStatsManager() { return statsManager; }
}
