// de/deinname/statsplugin/party/Party.java
package de.deinname.statsplugin.party;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {

    private final UUID leader;
    private final Set<UUID> members = new HashSet<>();

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean isLeader(UUID id) {
        return leader.equals(id);
    }

    public boolean contains(UUID id) {
        return members.contains(id);
    }

    public void addMember(UUID id) {
        members.add(id);
    }

    public void removeMember(UUID id) {
        members.remove(id);
    }

    public int size() {
        return members.size();
    }
}
