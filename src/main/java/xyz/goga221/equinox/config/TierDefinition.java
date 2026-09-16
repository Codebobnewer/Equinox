package xyz.goga221.equinox.config;

import org.bukkit.entity.Horse;

/** One `/stable` menu tier's stats, as configured under {@code tiers.<TIER>} in config.yml. */
public record TierDefinition(
        String displayName,
        double price,
        double health,
        double speed,
        double jumpStrength,
        Horse.Color color,
        Horse.Style style
) {
}
