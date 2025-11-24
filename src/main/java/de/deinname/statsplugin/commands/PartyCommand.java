// de/deinname/statsplugin/commands/PartyCommand.java
package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final PartyManager partyManager;

    public PartyCommand(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur ingame nutzbar.");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage("§e/party invite <player>");
            p.sendMessage("§e/party join <leader>");
            p.sendMessage("§e/party leave");
            p.sendMessage("§e/party disband");
            p.sendMessage("§e/party info");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "invite" -> {
                if (args.length < 2) {
                    p.sendMessage("§c/party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    p.sendMessage("§cCouldn't find §e" + args[1] + ".");
                    return true;
                }
                if (target.equals(p)) {
                    p.sendMessage("§cYou cant invite yourself.");
                    return true;
                }
                partyManager.invite(p, target);
            }
            case "join" -> {
                if (args.length < 2) {
                    p.sendMessage("§c/party join <leader>");
                    return true;
                }
                partyManager.join(p, args[1]);
            }
            case "leave" -> {
                partyManager.leave(p);
            }
            case "disband" -> {
                partyManager.disband(p);
            }
            case "info" -> {
                partyManager.showInfo(p);
            }
            default -> p.sendMessage("§c/party for help.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            return Arrays.asList("invite", "join", "leave", "disband", "info");
        }

        if (args.length == 2 && ("invite".equalsIgnoreCase(args[0]) || "join".equalsIgnoreCase(args[0]))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        return List.of();
    }
}
