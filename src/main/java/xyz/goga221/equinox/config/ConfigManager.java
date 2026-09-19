package xyz.goga221.equinox.config;

import xyz.goga221.equinox.horse.HorseTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        mergeMissingDefaults();
        load();
    }

    /**
     * saveDefaultConfig() only writes config.yml if it's missing entirely - an existing file from
     * before this plugin added a new key (a message, a tier field, etc.) is left untouched, so that
     * key would silently be absent from {@code messages}/{@code tiers} and getMessage() would fall
     * back to showing the raw key itself in chat instead of real text. Loading the bundled resource
     * as "defaults" and copying anything the live file doesn't already have into it - then saving -
     * keeps existing admin overrides intact while still picking up new keys automatically.
     */
    private void mergeMissingDefaults() {
        try (InputStream defaultStream = plugin.getResource("config.yml")) {
            if (defaultStream == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            FileConfiguration config = plugin.getConfig();
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            plugin.saveConfig();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not merge new config.yml defaults: " + e.getMessage());
        }
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.databaseFile = config.getString("database.file", "equinox.db");
        this.refundPercent = config.getDouble("economy.refund-percent", 0.8);

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

    public String getMessage(String key) {
        return messages.getOrDefault(key, key);
    }
}
