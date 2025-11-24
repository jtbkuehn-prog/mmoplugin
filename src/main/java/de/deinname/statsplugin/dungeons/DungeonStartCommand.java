package de.deinname.statsplugin.dungeons;

import de.deinname.statsplugin.party.Party;
import de.deinname.statsplugin.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DungeonStartCommand implements CommandExecutor {

    private final DungeonManager dungeonManager;
    private final PartyManager partyManager;

    public DungeonStartCommand(DungeonManager dungeonManager, PartyManager partyManager) {
        this.dungeonManager = dungeonManager;
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Ingame nutzbar.");
            return true;
        }

        // 1) Check: ist Spieler in einer Party?
        Party party = partyManager.getParty(p);
        if (party == null) {
            int id = dungeonManager.createInstance();
            dungeonManager.saveReturnLocation(p);
            dungeonManager.teleportToInstance(p, id);

            p.sendMessage("§aJoining Solo-Dungeon #" + id + "!");
            return true;
        }

        // 2) Check: ist er Leader?
        UUID leaderId = party.getLeader();
        if (!leaderId.equals(p.getUniqueId())) {
            p.sendMessage("§cOnly the Party-Leader can start a dungeon.");
            return true;
        }

        // 3) Instanz erstellen
        int id = dungeonManager.createInstance();

        // 4) Alle Party-Mitglieder in die Instanz teleportieren
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                dungeonManager.saveReturnLocation(p);
                dungeonManager.teleportToInstance(member, id);
                member.sendMessage("§aYour party is joining Dungeon §e#" + id + "§a!");
            }
        }

        return true;
    }
}
