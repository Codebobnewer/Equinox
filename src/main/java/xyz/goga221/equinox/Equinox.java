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
import xyz.goga221.equinox.station.StationManager;
import xyz.goga221.equinox.station.WorldGuardHook;
import xyz.goga221.equinox.util.Messages;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import dev.jorel.commandapi.CommandAPI;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Wires up every Equinox component on enable and tears them down on disable. Holds no business
 * logic itself - that all lives in {@link HorseManager}/{@link StationManager} - this class is
 * purely construction/lifecycle plumbing.
 */
public final class Equinox extends JavaPlugin {

    private DatabaseManager databaseManager;
    private TaskScheduler scheduler;

    // No onLoad() override: CommandAPI v12 ships as a genuine Paper plugin with its own
    // Bootstrapper, which calls CommandAPI.onLoad() itself before any classic Bukkit-style
    // plugin's onLoad() runs. Calling it again here throws "onLoad() called more than once".

    @Override
    public void onEnable() {
        CommandAPI.onEnable();

        ConfigManager configManager = new ConfigManager(this);
        Messages messages = new Messages(configManager);

        try {
            databaseManager = new DatabaseManager(this, configManager.getDatabaseFile());
        } catch (RuntimeException e) {
            // DatabaseManager already bounds connection attempts to a few seconds instead of
            // hanging (see its own comment), but if it still fails outright - a corrupted file,
            // a permissions problem, whatever - fail this plugin cleanly instead of letting the
            // exception surface as a half-initialized, harder-to-diagnose state.
            getLogger().severe("Could not initialize the database, disabling Equinox: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        HorseDao horseDao = new HorseDao(databaseManager, getLogger());
        StationDao stationDao = new StationDao(databaseManager, getLogger());

        WorldGuardHook worldGuardHook = new WorldGuardHook();
        StationManager stationManager = new StationManager(stationDao, worldGuardHook);

        scheduler = UniversalScheduler.getScheduler(this);
        EconomyProvider economyProvider = new StubEconomyProvider(getLogger());

        HorseManager horseManager = new HorseManager(this, configManager, horseDao, stationManager,
                economyProvider, scheduler, messages);

        // Loaded off the main thread so a stalled database can't block server startup at all -
        // both loads are independent (no station-depends-on-horse ordering needed), so they just
        // run side by side and each logs once it's actually done.
        stationManager.loadStationsIntoCache(scheduler).whenComplete((count, throwable) -> {
            if (throwable != null) {
                getLogger().log(Level.SEVERE, "Failed to load stations", throwable);
            } else {
                getLogger().info("Loaded " + count + " station(s)");
            }
        });
        horseManager.loadOwnedHorsesIntoCache().whenComplete((count, throwable) -> {
            if (throwable != null) {
                getLogger().log(Level.SEVERE, "Failed to load owned horses", throwable);
            } else {
                getLogger().info("Loaded " + count + " owned horse(s)");
            }
        });

        getServer().getPluginManager().registerEvents(new HorseListener(horseManager, messages), this);

        StableMenu stableMenu = new StableMenu(configManager, horseManager);
        new StableCommand(this, stableMenu, horseManager, stationManager).register();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (scheduler != null) {
            // Cancels this plugin's own pending scheduled tasks (e.g. a horse's panic-AI timeout)
            // so none of them fire after the database above is already closed.
            scheduler.cancelTasks();
        }
        // Deliberately not calling CommandAPI.onDisable() here: CommandAPI runs as its own
        // separate plugin on this server (see plugin.yml's depend: [CommandAPI]), so its
        // CommandAPIHandler is a single shared instance used by every dependent plugin.
        // Calling onDisable() from a dependent plugin tears that shared instance down for
        // everyone, not just Equinox - only the actual CommandAPI plugin should do that.
    }
}
