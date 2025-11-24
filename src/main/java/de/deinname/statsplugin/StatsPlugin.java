package de.deinname.statsplugin;

import de.deinname.statsplugin.abilities.ExplosiveArrowAbility;
import de.deinname.statsplugin.commands.*;
import de.deinname.statsplugin.dungeons.DungeonChestListener;
import de.deinname.statsplugin.dungeons.DungeonManager;
import de.deinname.statsplugin.dungeons.DungeonStartCommand;
import de.deinname.statsplugin.listeners.*;
import de.deinname.statsplugin.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import de.deinname.statsplugin.util.DamageNumbers;
import de.deinname.statsplugin.abilities.AbilityListener;
import de.deinname.statsplugin.abilities.DamageBoostState;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.loot.LootTableManager;
import de.deinname.statsplugin.loot.LootListener;
import de.deinname.statsplugin.listeners.ItemTemplateGuiListener;

import java.util.Objects;


public class StatsPlugin extends JavaPlugin {
    private StatsManager statsManager;
    private StatsStorage storage;
    private DamageNumbers damageNumbers;
    public static DungeonManager dungeonManager;
    private PartyManager partyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDungeonWorld();

        storage = new StatsStorage(this);
        statsManager = new StatsManager();
        statsManager.setStorage(storage);
        damageNumbers = new DamageNumbers(this);
        CustomMobManager mobManager = new CustomMobManager(this);
        LootTableManager lootManager = new LootTableManager(this);
        ItemTemplateCommand itemTplCmd = new ItemTemplateCommand(this);
        dungeonManager = new DungeonManager(this, mobManager, lootManager);
        partyManager = new PartyManager(this);


        var tplCmd = new ItemTemplateCommand(this);
        getCommand("itemtpl").setExecutor(itemTplCmd);
        getCommand("itemtpl").setTabCompleter(itemTplCmd);


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
        getServer().getPluginManager().registerEvents(new DamageListener(statsManager, damageNumbers, mobManager, this), this);
        AttackListener attackListener = new AttackListener(statsManager, damageNumbers, mobManager);getServer().getPluginManager().registerEvents(attackListener, this);
        getServer().getPluginManager().registerEvents(new XPListener(this, statsManager, mobManager), this);
        getServer().getPluginManager().registerEvents(new ItemRecalcListener(this, statsManager, manaManager, itemKeys), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this, manaManager, itemKeys), this);
        getServer().getPluginManager().registerEvents(new DamageBoostState(), this);
        getServer().getPluginManager().registerEvents(new FoodAndRegenListener(), this);
        getServer().getPluginManager().registerEvents(new LootListener(this, lootManager, mobManager), this);
        getServer().getPluginManager().registerEvents(new ItemTemplateGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ExplosiveArrowAbility(this, manaManager, itemKeys, statsManager, damageNumbers, mobManager), this);
        getServer().getPluginManager().registerEvents(new BowForceListener(this), this);
        getServer().getPluginManager().registerEvents(new DungeonChestListener(this, lootManager,
                        dungeonManager.getDungeonChestKey(), dungeonManager.getDungeonChestRolledKey()), this);



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



        MobSpawnCommand mobCmd = new MobSpawnCommand(mobManager);
        getCommand("mobspawn").setExecutor(mobCmd);
        getCommand("mobspawn").setTabCompleter(mobCmd);

        Objects.requireNonNull(getCommand("party"))
                .setExecutor(new PartyCommand(partyManager));

        // NEU in onEnable:
        Objects.requireNonNull(getCommand("dungeonstart"))
                .setExecutor(new DungeonStartCommand(dungeonManager, partyManager));


        getCommand("leavedungeon").setExecutor((sender, cmd, lbl, args) -> {
            if (!(sender instanceof Player p)) return true;

            dungeonManager.returnPlayer(p);
            p.sendMessage("§aDu hast den Dungeon verlassen.");

            return true;
        });





        // HUD & Ticks (alle 10 Ticks = 0,5s)
        // HUD & Ticks (alle 10 Ticks = 0,5s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (var p : getServer().getOnlinePlayers()) {

                if (!p.isOnline() || p.isDead()) {
                    continue;
                }
                statsManager.applyNoVanillaCooldown(p);

                // Stats & Mana holen
                PlayerStats stats = statsManager.getStats(p);
                if (stats == null) continue;

                // --- Mana-Regeneration (deine vorhandene Logik) ---
                manaManager.tick(p);

                // --- Eigene Health-Regeneration ---
                double hpr = stats.getHealthRegen(); // HP-Reg pro Sekunde
                if (hpr > 0) {
                    double dt = 0.5;          // 10 Ticks = 0,5s
                    double add = hpr * dt;
                    stats.heal(add);          // arbeitet auf currentHealth
                }

                // --- Werte für Anzeige berechnen ---
                double curHpD  = stats.getCurrentHealth();
                double maxHpD  = stats.getHealth();          // Max-HP (Base + Items)
                if (maxHpD <= 0) maxHpD = 1.0;
                if (curHpD < 0) curHpD = 0;

                double curManaD = manaManager.get(p);
                double maxManaD = manaManager.getMax(p);
                if (maxManaD <= 0) maxManaD = 1.0;
                if (curManaD < 0) curManaD = 0;

                double hpFrac   = curHpD / maxHpD;   // 0..1
                double manaFrac = curManaD / maxManaD;

                // --- 1) Vanilla-Herzen als HP-Prozent-Balken ---
                var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) {
                    double maxHearts = 20.0;
                    attr.setBaseValue(maxHearts);

                    double hearts = hpFrac * maxHearts;

                    // Wenn noch lebend, nie komplett 0 Herzen anzeigen,
                    // sonst denkt Bukkit evtl. der Spieler ist tot.
                    if (curHpD > 0 && hearts < 1.0) {
                        hearts = 1.0;
                    }

                    // clamp
                    hearts = Math.max(0.0, Math.min(maxHearts, hearts));
                    p.setHealth(hearts);
                }

                // --- 2) Hungerleisten als Mana-Prozent-Balken ---
                int food = (int) Math.round(manaFrac * 20.0);
                food = Math.max(0, Math.min(20, food));

                p.setFoodLevel(food);
                p.setSaturation(0f);   // kein Vanilla-Autoplay
                p.setExhaustion(0f);

                // --- 3) ActionBar-HUD mit echten Werten + Prozent ---
                int hp    = (int) Math.ceil(curHpD);
                int hpMax = (int) Math.ceil(maxHpD);
                int m     = (int) Math.ceil(curManaD);
                int mMax  = (int) Math.ceil(maxManaD);

                int hpPct   = (int) Math.round(hpFrac   * 100.0);
                int manaPct = (int) Math.round(manaFrac * 100.0);

                Component bar = Component.text()
                        .append(Component.text("❤HP ", NamedTextColor.RED))
                        .append(Component.text(hp + "/" + hpMax))
                        .append(Component.text("   |   "))
                        .append(Component.text("✪Mana ", NamedTextColor.BLUE))
                        .append(Component.text(m + "/" + mMax))
                        .build();

                p.sendActionBar(bar);


            }
        }, 0L, 10L);



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

    private void createDungeonWorld() {
        String worldName = "dungeons_world";

        if (getServer().getWorld(worldName) != null) {
            getLogger().info("[Dungeons] Welt '" + worldName + "' existiert bereits.");
            return;
        }

        getLogger().info("[Dungeons] Erstelle Void-Welt '" + worldName + "'...");

        org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(worldName);
        creator.environment(org.bukkit.World.Environment.NORMAL);
        creator.generator(new de.deinname.statsplugin.world.VoidChunkGenerator());
        creator.generateStructures(false);

        org.bukkit.World world = creator.createWorld();
        if (world != null) {
            world.setSpawnLocation(0, 64, 0);
            getLogger().info("[Dungeons] Welt '" + worldName + "' erstellt.");
        } else {
            getLogger().warning("[Dungeons] Konnte Welt '" + worldName + "' nicht erstellen!");
        }
    }
}
