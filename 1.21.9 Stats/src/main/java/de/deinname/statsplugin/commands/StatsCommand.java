package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.PlayerLevel;
import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final StatsManager statsManager;   // Beibehaltener Name für Kompatibilität
    private final ManaManager manaManager;     // optional, darf null sein

    // Alter Konstruktor (kompatibel zu bestehendem Code)
    public StatsCommand(StatsManager statsManager) {
        this(statsManager, null);
    }

    // Neuer Konstruktor (für Mana-Anzeige)
    public StatsCommand(StatsManager statsManager, ManaManager manaManager) {
        this.statsManager = statsManager;
        this.manaManager = manaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler können diesen Befehl nutzen!");
            return true;
        }

        // /stats  -> Eigene Übersicht (mit HP/Mana-Header)
        if (args.length == 0) {
            printHpManaHeader(player);
            PlayerStats stats = statsManager.getStats(player);
            PlayerLevel level = statsManager.getLevel(player);
            player.sendMessage(stats.toString());
            player.sendMessage("");
            player.sendMessage(level.toString());
            return true;
        }

        // ab hier gibt es mindestens 1 Argument
        String sub = args[0].toLowerCase();

        // /stats <spieler>
        if (args.length == 1
                && !sub.equals("set")
                && !sub.equals("reset")
                && !sub.equals("save")
                && !sub.equals("allocate")
                && !sub.equals("resetskills")) {

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§cSpieler nicht gefunden!");
                return true;
            }

            // Header für Zielspieler
            printHpManaHeader(target);
            PlayerStats stats = statsManager.getStats(target);
            PlayerLevel level = statsManager.getLevel(target);
            player.sendMessage("§6Stats von " + target.getName() + ":");
            player.sendMessage(stats.toString());
            player.sendMessage("");
            player.sendMessage(level.toString());
            return true;
        }

        switch (sub) {
            case "set": {
                if (!player.hasPermission("stats.admin")) {
                    player.sendMessage("§cKeine Berechtigung!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cNutzung: /stats set <stat> <wert> [spieler]");
                    return true;
                }

                Player target = player;
                if (args.length >= 4) {
                    target = Bukkit.getPlayer(args[3]);
                    if (target == null) {
                        player.sendMessage("§cSpieler nicht gefunden!");
                        return true;
                    }
                }

                String stat = args[1].toLowerCase();
                double value;
                try {
                    value = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cUngültige Zahl!");
                    return true;
                }

                PlayerStats s = statsManager.getStats(target);
                switch (stat) {
                    case "damage" -> s.setDamage(value);
                    case "critchance", "crit" -> s.setCritChance(value);
                    case "critdamage", "critdmg" -> s.setCritDamage(value);
                    case "range" -> s.setRange(value);
                    case "health", "hp" -> {
                        s.setHealth(value);
                        statsManager.applyHealth(target);
                    }
                    case "armor" -> s.setArmor(value);
                    case "attackspeed", "aps" -> s.setAttackSpeed(value);
                    case "mana", "ma" -> s.setAttackSpeed(value);
                    case "manaregen", "manareg" -> s.setAttackSpeed(value);
                    case "healthregen", "healthreg" -> s.setAttackSpeed(value);
                    default -> {
                        player.sendMessage("§cUnbekannter Stat! Verfügbar: damage, critchance, "
                                + "critdamage, range, health, armor, attackspeed");
                        return true;
                    }
                }
                player.sendMessage("§a" + stat + " von " + target.getName() + " auf " + value + " gesetzt!");
                return true;
            }

            case "reset": {
                if (!player.hasPermission("stats.admin")) {
                    player.sendMessage("§cKeine Berechtigung!");
                    return true;
                }
                Player target = player;
                if (args.length >= 2) {
                    Player t = Bukkit.getPlayer(args[1]);
                    if (t != null) target = t;
                    else {
                        player.sendMessage("§cSpieler nicht gefunden!");
                        return true;
                    }
                }
                statsManager.resetStats(target);
                player.sendMessage("§aStats von " + target.getName() + " zurückgesetzt!");
                return true;
            }

            case "save": {
                if (!player.hasPermission("stats.admin")) {
                    player.sendMessage("§cKeine Berechtigung!");
                    return true;
                }
                if (args.length == 1) {
                    statsManager.saveAll();
                    player.sendMessage("§aAlle Stats gespeichert!");
                } else {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        player.sendMessage("§cSpieler nicht gefunden!");
                        return true;
                    }
                    statsManager.saveStats(target.getUniqueId());
                    player.sendMessage("§aStats von " + target.getName() + " gespeichert!");
                }
                return true;
            }

            case "allocate": {
                PlayerLevel level = statsManager.getLevel(player);
                if (level == null) {
                    player.sendMessage("§cLevel-Daten fehlen.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cNutzung: /stats allocate <stat> [menge]");
                    return true;
                }
                String stat = args[1];
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cUngültige Zahl!");
                        return true;
                    }
                }
                if (level.allocateSkillPoint(stat, amount)) {
                    statsManager.applyHealth(player);
                }
                return true;
            }

            case "resetskills": {
                PlayerLevel level = statsManager.getLevel(player);
                if (level == null) {
                    player.sendMessage("§cLevel-Daten fehlen.");
                    return true;
                }
                level.resetSkillPoints();
                statsManager.applyHealth(player);
                return true;
            }

            default:
                player.sendMessage("§cNutzung: /stats [spieler] | /stats set <stat> <wert> [spieler] | "
                        + "/stats reset [spieler] | /stats save [spieler] | "
                        + "/stats allocate <stat> [menge] | /stats resetskills");
                return true;
        }
    }

    private void printHpManaHeader(Player p) {
        // HP (rot)
        int hp = (int) Math.round(p.getHealth());
        int hpMax = (int) Math.round(p.getMaxHealth());
        Component lineHp = Component.text()
                .append(Component.text("HP ", NamedTextColor.RED))
                .append(Component.text(hp + "/" + hpMax))
                .build();
        p.sendMessage(lineHp);

        // Mana (blau), nur wenn ManaManager vorhanden
        if (manaManager != null) {
            int m = (int) Math.round(manaManager.get(p));
            int mMax = (int) Math.round(manaManager.getMax(p));
            double regen = manaManager.getRegen(p);
            Component lineMana = Component.text()
                    .append(Component.text("Mana ", NamedTextColor.BLUE))
                    .append(Component.text(m + "/" + mMax + " (" + trim1(regen) + "/s)"))
                    .build();
            p.sendMessage(lineMana);
        }
    }

    private String trim1(double v) {
        boolean isInt = Math.abs(v - Math.rint(v)) < 1e-9;
        return isInt ? String.valueOf((int) Math.rint(v))
                : String.valueOf(((double) Math.round(v * 10)) / 10.0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("set", "reset", "save", "allocate", "resetskills"));
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            completions.addAll(Arrays.asList("damage", "critchance", "critdamage", "range", "health", "armor", "attackspeed","mana","manaregen","healthregen"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            completions.add("<wert>");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("save")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("allocate")) {
            completions.addAll(Arrays.asList("health","damage","armor","critchance","critdamage","range","attackspeed","mana","manaregen","healthregen"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("allocate")) {
            completions.add("<menge>");
        }
        return completions;
    }
}
