package de.deinname.statsplugin.abilities;

import java.util.HashMap;
import java.util.Map;

public enum AbilityDefinition {

    DAMAGE_BOOST(
            "damage_boost",
            "Damage Boost",
            "Increases your damage by 50% for a short duration."
    ),
    // 🔥 NEU: Explosive Arrow
    EXPLOSIVE_ARROW(
                "explosive_arrow",
                    "Explosive Arrow",
                    "Fires an explosive arrow that causes 200% area damage on impact."
    );


    private final String id;
    private final String displayName;
    private final String description;

    AbilityDefinition(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    private static final Map<String, AbilityDefinition> BY_ID = new HashMap<>();

    static {
        for (AbilityDefinition def : values()) {
            BY_ID.put(def.id, def);
        }
    }

    public static AbilityDefinition byId(String id) {
        return BY_ID.get(id);
    }
}
