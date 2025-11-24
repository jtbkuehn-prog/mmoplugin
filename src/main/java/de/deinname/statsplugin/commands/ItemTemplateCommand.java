package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.listeners.ItemTemplateGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class ItemTemplateCommand implements CommandExecutor, TabCompleter {

    // 🔹 Gemeinsamer Titel-Prefix für das GUI
    public static final String TEMPLATE_INV_TITLE = "Item-Vorlagen";

    private final JavaPlugin plugin;

    public ItemTemplateCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "save" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cNur ingame nutzbar.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("§cNutzung: /itemtpl save <name>");
                    return true;
                }
                String name = args[1];

                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    p.sendMessage("§cDu musst ein Item in der Hand halten.");
                    return true;
                }

                plugin.getConfig().set("saved-items." + name, hand.clone());
                plugin.saveConfig();

                p.sendMessage("§aItem-Vorlage §e" + name + " §agespeichert.");
                return true;
            }

            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cNutzung: /itemtpl give <name> [spieler]");
                    return true;
                }
                String name = args[1];

                ItemStack stored = plugin.getConfig().getItemStack("saved-items." + name);
                if (stored == null) {
                    sender.sendMessage("§cKeine Item-Vorlage mit Namen §e" + name + "§c gefunden.");
                    return true;
                }

                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        sender.sendMessage("§cSpieler §e" + args[2] + " §cnicht gefunden.");
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("§cBitte Spieler angeben: /itemtpl give <name> <spieler>");
                        return true;
                    }
                    target = p;
                }

                ItemStack toGive = stored.clone();
                HashMap<Integer, ItemStack> rest = target.getInventory().addItem(toGive);
                if (!rest.isEmpty()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), toGive);
                    target.sendMessage("§eDein Inventar war voll – Item wurde gedroppt.");
                }

                if (sender == target) {
                    sender.sendMessage("§aDu hast die Vorlage §e" + name + " §aerhalten.");
                } else {
                    sender.sendMessage("§aItem-Vorlage §e" + name + " §aan §e" + target.getName() + " §agegeben.");
                    target.sendMessage("§aDu hast die Item-Vorlage §e" + name + " §aerhalten.");
                }
                return true;
            }

            case "list" -> {
                // 👉 Für Spieler: GUI öffnen
                if (sender instanceof Player p) {
                    int page = 0;
                    if (args.length >= 2) {
                        try {
                            page = Integer.parseInt(args[1]) - 1; // /itemtpl list 2 -> Seite 1 (Index 1)
                        } catch (NumberFormatException ignored) {}
                    }
                    ItemTemplateGuiListener.openTemplateInventory(p, page);
                    return true;
                }

                // Konsole: Textliste
                ConfigurationSection sec = plugin.getConfig().getConfigurationSection("saved-items");
                if (sec == null || sec.getKeys(false).isEmpty()) {
                    sender.sendMessage("§7Es sind noch keine Item-Vorlagen gespeichert.");
                    return true;
                }
                sender.sendMessage("§aGespeicherte Item-Vorlagen:");
                for (String key : sec.getKeys(false)) {
                    sender.sendMessage(" §e- " + key);
                }
                return true;
            }

            case "delete", "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cNutzung: /itemtpl delete <name>");
                    return true;
                }
                String name = args[1];

                String path = "saved-items." + name;
                if (plugin.getConfig().get(path) == null) {
                    sender.sendMessage("§cKeine Item-Vorlage mit Namen §e" + name + "§c gefunden.");
                    return true;
                }

                plugin.getConfig().set(path, null);
                plugin.saveConfig();
                sender.sendMessage("§aItem-Vorlage §e" + name + " §agelöscht.");
                return true;
            }

            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§e/itemtpl save <name> §7- aktuelles Item als Vorlage speichern");
        s.sendMessage("§e/itemtpl give <name> [spieler] §7- Vorlage geben");
        s.sendMessage("§e/itemtpl list [page] §7- alle Vorlagen im GUI anzeigen");
        s.sendMessage("§e/itemtpl delete <name> §7- Vorlage löschen");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("save", "give", "list", "delete");
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("delete") || sub.equals("remove")) {
                ConfigurationSection sec = plugin.getConfig().getConfigurationSection("saved-items");
                if (sec == null) return List.of();
                return sec.getKeys(false).stream()
                        .filter(k -> k.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
