package xyz.goga221.equinox.config;

import xyz.goga221.equinox.horse.HorseTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Loads config.yml into typed values, falling back to the shipped defaults for any key an admin hasn't set. */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private Map<HorseTier, TierDefinition> tiers;
    private Map<String, String> messages;
    private double refundPercent;
    private String databaseFile;
    private long horsePanicDurationTicks;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        load();
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.databaseFile = config.getString("database.file", "equinox.db");
        this.refundPercent = config.getDouble("economy.refund-percent", 0.8);
        this.horsePanicDurationTicks = config.getLong("horse.panic-duration-ticks", 100);

        Map<HorseTier, TierDefinition> loadedTiers = new EnumMap<>(HorseTier.class);
        ConfigurationSection tiersSection = config.getConfigurationSection("tiers");
        if (tiersSection != null) {
            for (HorseTier tier : HorseTier.values()) {
                ConfigurationSection section = tiersSection.getConfigurationSection(tier.name());
                if (section == null) {
                    continue;
                }
                loadedTiers.put(tier, new TierDefinition(
                        section.getString("display-name", tier.name()),
                        section.getDouble("price", 100.0),
                        section.getDouble("health", 20.0),
                        section.getDouble("speed", 0.2),
                        section.getDouble("jump-strength", 0.5),
                        Horse.Color.valueOf(section.getString("color", "BROWN")),
                        Horse.Style.valueOf(section.getString("style", "NONE"))
                ));
            }
        }
        this.tiers = Collections.unmodifiableMap(loadedTiers);

        Map<String, String> loadedMessages = new HashMap<>();
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                loadedMessages.put(key, messagesSection.getString(key, ""));
            }
        }
        this.messages = Collections.unmodifiableMap(loadedMessages);
    }

    public TierDefinition getTier(HorseTier tier) {
        return tiers.get(tier);
    }

    public Map<HorseTier, TierDefinition> getTiers() {
        return tiers;
    }

    public double getRefundPercent() {
        return refundPercent;
    }

    public String getDatabaseFile() {
        return databaseFile;
    }

    public long getHorsePanicDurationTicks() {
        return horsePanicDurationTicks;
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, key);
    }
}
