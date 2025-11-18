package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.mobs.CustomMobType;
import org.bukkit.EntityEffect;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MobSpawnCommand implements CommandExecutor, TabCompleter {

    private final CustomMobManager mobManager;

    public MobSpawnCommand(CustomMobManager mobManager) {
        this.mobManager = mobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur ingame.");
            return true;
        }

        if (args.length < 2) {
            p.sendMessage("§cNutzung: /mobspawn <type> <level>");
            return true;
        }

        String typeId = args[0];
        CustomMobType type = CustomMobType.byId(typeId);
        if (type == null) {
            p.sendMessage("§cUnbekannter Mob-Typ. Verfügbar: " +
                    Arrays.stream(CustomMobType.values())
                            .map(CustomMobType::getId)
                            .collect(Collectors.joining(", ")));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            p.sendMessage("§cLevel muss eine Zahl sein.");
            return true;
        }
        if (level <= 0) level = 1;

        var loc = p.getLocation();
        var mob = mobManager.spawnMob(type, level, loc);
        if (mob == null) {
            p.sendMessage("§cSpawn fehlgeschlagen.");
            return true;
        }

        p.sendMessage("§aCustomMob gespawnt: §e" + type.getId() + " §7(Lv. " + level + ")");
        mob.playEffect(EntityEffect.ENTITY_POOF);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            return Arrays.stream(CustomMobType.values())
                    .map(CustomMobType::getId)
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return List.of("1", "5", "10", "20","50","100");
        }
        return List.of();
    }
}
