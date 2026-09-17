package xyz.goga221.equinox.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;
import org.bukkit.entity.Horse;

/** One `/stable` menu tier's stats, as configured under {@code tiers.<TIER>} in config.yml. */
@Getter
@With
@AllArgsConstructor
public class TierDefinition {

    private final String displayName;
    private final double price;
    private final double health;
    private final double speed;
    private final double jumpStrength;
    private final Horse.Color color;
    private final Horse.Style style;
}
