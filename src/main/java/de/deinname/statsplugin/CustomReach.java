package de.deinname.statsplugin.combat;

import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kleiner Helfer: Merkt sich für sehr kurze Zeit (ein paar Ticks),
 * dass ein Spieler einen "Extended-Reach"-Treffer auf ein bestimmtes Ziel ausführen darf.
 * Der DamageListener sieht das und überspringt NUR für diesen Hit den Range-Check.
 */
public final class CustomReach {

    private static final class Entry {
        final int targetId;
        long expireAt; // System.nanoTime() + ttlNanos
        Entry(int targetId, long expireAt) { this.targetId = targetId; this.expireAt = expireAt; }
    }

    private static final Map<UUID, Entry> ALLOW = new HashMap<>();
    // ~2 Ticks TTL in ns (2 * 50ms)
    private static final long TTL_NANOS = 2L * 50_000_000L;

    private CustomReach() {}

    /** Merkt: Angreifer darf genau JETZT dieses Ziel außerhalb von Vanilla-Reach treffen. */
    public static void allowOnce(UUID attacker, Entity target) {
        long now = System.nanoTime();
        ALLOW.put(attacker, new Entry(target.getEntityId(), now + TTL_NANOS));
    }

    /** Prüft & verbraucht das Override für diesen Treffer. */
    public static boolean consumeIfMatches(UUID attacker, Entity target) {
        long now = System.nanoTime();
        Entry e = ALLOW.get(attacker);
        if (e == null) return false;
        if (now > e.expireAt) { ALLOW.remove(attacker); return false; }
        if (e.targetId != target.getEntityId()) return false;
        // verbrauchen
        ALLOW.remove(attacker);
        return true;
    }
}
