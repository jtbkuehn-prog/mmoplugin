// de/deinname/statsplugin/party/PartyManager.java
package de.deinname.statsplugin.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PartyManager {

    private final JavaPlugin plugin;
    private final int MAX_SIZE = 5;

    // Spieler -> Party
    private final Map<UUID, Party> partiesByMember = new HashMap<>();

    // Einladungen: target -> leader
    private final Map<UUID, UUID> invites = new HashMap<>();

    public PartyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Party getParty(Player p) {
        return partiesByMember.get(p.getUniqueId());
    }

    public boolean isInParty(Player p) {
        return getParty(p) != null;
    }

    public boolean isLeader(Player p) {
        Party party = getParty(p);
        return party != null && party.isLeader(p.getUniqueId());
    }

    public void invite(Player leader, Player target) {
        UUID lid = leader.getUniqueId();
        UUID tid = target.getUniqueId();

        Party party = getParty(leader);
        if (party == null) {
            // automatisch Party erstellen
            party = new Party(lid);
            partiesByMember.put(lid, party);
        }

        if (!party.isLeader(lid)) {
            leader.sendMessage("§cOnly the Party-Leader can invite.");
            return;
        }

        if (party.size() >= MAX_SIZE) {
            leader.sendMessage("§cYour party is full (" + MAX_SIZE + ").");
            return;
        }

        if (getParty(target) != null) {
            leader.sendMessage("§c" + target.getName() + " is already in a party.");
            return;
        }

        invites.put(tid, lid);
        leader.sendMessage("§aInvite sent to §e" + target.getName() + ".");
        target.sendMessage("§e" + leader.getName() + " §ainvited you to their party.");
        target.sendMessage("§7Use §a/party join " + leader.getName() + " §7to join.");
    }

    public void join(Player player, String leaderName) {
        UUID pid = player.getUniqueId();
        Player leader = Bukkit.getPlayerExact(leaderName);
        if (leader == null) {
            player.sendMessage("§cLeader §e" + leaderName + " §cis not online.");
            return;
        }

        UUID lid = leader.getUniqueId();
        UUID invitedBy = invites.get(pid);
        if (invitedBy == null || !invitedBy.equals(lid)) {
            player.sendMessage("§cYou don't have an invite from §e" + leader.getName() + "§c.");
            return;
        }

        Party party = getParty(leader);
        if (party == null) {
            player.sendMessage("§cThe party from §e" + leader.getName() + " §cdoesn't exist.");
            invites.remove(pid);
            return;
        }

        if (party.size() >= MAX_SIZE) {
            player.sendMessage("§cThe party is full.");
            return;
        }

        // Mitglied hinzufügen
        party.addMember(pid);
        partiesByMember.put(pid, party);
        invites.remove(pid);

        broadcast(party, "§a" + player.getName() + " §7joined the party.");
    }

    public void leave(Player p) {
        Party party = getParty(p);
        if (party == null) {
            p.sendMessage("§cYou're not in a Party.");
            return;
        }

        UUID pid = p.getUniqueId();
        boolean wasLeader = party.isLeader(pid);

        party.removeMember(pid);
        partiesByMember.remove(pid);

        if (party.size() == 0) {
            // komplett weg
            p.sendMessage("§7Your party has been disbanded.");
            return;
        }

        if (wasLeader) {
            // neuer Leader = irgendein Mitglied
            UUID newLeaderId = party.getMembers().iterator().next();
            Player newLeader = Bukkit.getPlayer(newLeaderId);
            if (newLeader != null) {
                broadcast(party, "§e" + newLeader.getName() + " §7is now Party-Leader.");
            }
        }

        broadcast(party, "§c" + p.getName() + " §7has left the party.");
    }

    public void disband(Player p) {
        Party party = getParty(p);
        if (party == null) {
            p.sendMessage("§cYou're not in a party.");
            return;
        }
        if (!party.isLeader(p.getUniqueId())) {
            p.sendMessage("§cOnly the leader can disband the party.");
            return;
        }

        for (UUID id : party.getMembers()) {
            partiesByMember.remove(id);
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                pl.sendMessage("§cThe party has been disbanded by §e" + p.getName() + ".");
            }
        }
    }

    public void showInfo(Player p) {
        Party party = getParty(p);
        if (party == null) {
            p.sendMessage("§7You're not in a party..");
            return;
        }

        p.sendMessage("§a--- Your Party ---");
        for (UUID id : party.getMembers()) {
            Player pl = Bukkit.getPlayer(id);
            String name = pl != null ? pl.getName() : "???";
            String mark = party.isLeader(id) ? "§6[L] " : "  ";
            p.sendMessage(mark + "§f" + name);
        }
    }

    public void broadcast(Party party, String msg) {
        for (UUID id : party.getMembers()) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                pl.sendMessage(msg);
            }
        }
    }
}
