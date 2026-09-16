package xyz.goga221.equinox;

import xyz.goga221.equinox.command.StableCommand;
import xyz.goga221.equinox.config.ConfigManager;
import xyz.goga221.equinox.data.DatabaseManager;
import xyz.goga221.equinox.data.HorseDao;
import xyz.goga221.equinox.data.StationDao;
import xyz.goga221.equinox.economy.EconomyProvider;
import xyz.goga221.equinox.economy.StubEconomyProvider;
import xyz.goga221.equinox.gui.StableMenu;
import xyz.goga221.equinox.horse.HorseListener;
import xyz.goga221.equinox.horse.HorseManager;
import xyz.goga221.equinox.scheduler.SchedulerService;
import xyz.goga221.equinox.station.StationManager;
import xyz.goga221.equinox.station.WorldGuardHook;
import xyz.goga221.equinox.util.Messages;
import dev.jorel.commandapi.CommandAPI;
import org.bukkit.plugin.java.JavaPlugin;

public final class Equinox extends JavaPlugin {

    private DatabaseManager databaseManager;

    // No onLoad() override: CommandAPI v12 ships as a genuine Paper plugin with its own
    // Bootstrapper, which calls CommandAPI.onLoad() itself before any classic Bukkit-style
    // plugin's onLoad() runs. Calling it again here throws "onLoad() called more than once".

    @Override
    public void onEnable() {
        CommandAPI.onEnable();

        ConfigManager configManager = new ConfigManager(this);
        Messages messages = new Messages(configManager);

        databaseManager = new DatabaseManager(this, configManager.getDatabaseFile());
        HorseDao horseDao = new HorseDao(databaseManager, getLogger());
        StationDao stationDao = new StationDao(databaseManager, getLogger());

        WorldGuardHook worldGuardHook = new WorldGuardHook();
        StationManager stationManager = new StationManager(stationDao, worldGuardHook);

        SchedulerService schedulerService = new SchedulerService(this);
        EconomyProvider economyProvider = new StubEconomyProvider(getLogger());

        HorseManager horseManager = new HorseManager(this, configManager, horseDao, stationManager,
                economyProvider, schedulerService, messages);

        getServer().getPluginManager().registerEvents(new HorseListener(horseManager, messages), this);

        StableMenu stableMenu = new StableMenu(configManager, horseManager);
        new StableCommand(this, stableMenu, horseManager, stationManager).register();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        // Deliberately not calling CommandAPI.onDisable() here: CommandAPI runs as its own
        // separate plugin on this server (see plugin.yml's depend: [CommandAPI]), so its
        // CommandAPIHandler is a single shared instance used by every dependent plugin.
        // Calling onDisable() from a dependent plugin tears that shared instance down for
        // everyone, not just Equinox - only the actual CommandAPI plugin should do that.
    }
}
