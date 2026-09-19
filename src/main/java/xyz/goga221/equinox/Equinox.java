package xyz.goga221.equinox;

import xyz.goga221.equinox.command.StableCommand;
import xyz.goga221.equinox.config.ConfigManager;
import xyz.goga221.equinox.economy.EconomyProvider;
import xyz.goga221.equinox.economy.StubEconomyProvider;
import xyz.goga221.equinox.gui.StableMenu;
import xyz.goga221.equinox.horse.HorseListener;
import xyz.goga221.equinox.horse.HorseService;
import xyz.goga221.equinox.horse.SqliteHorseRepository;
import xyz.goga221.equinox.station.StationRepository;
import xyz.goga221.equinox.station.StationService;
import xyz.goga221.equinox.station.WorldGuardHook;
import xyz.goga221.equinox.station.YamlStationRepository;
import xyz.goga221.equinox.util.Messages;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import dev.jorel.commandapi.CommandAPI;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Wires up every Equinox component on enable and tears them down on disable. Holds no business
 * logic itself - that all lives in {@link HorseService}/{@link StationService} - this class is
 * purely construction/lifecycle plumbing, plus the static service locator other code (and, in
 * principle, other plugins) can reach Equinox's services through.
 */
public final class Equinox extends JavaPlugin {

    private static Equinox instance;
    private static HorseService horseService;
    private static StationService stationService;
    private static Messages messages;
    private static TaskScheduler scheduler;

    private SqliteHorseRepository horseRepository;

    // No onLoad() override: CommandAPI v12 ships as a genuine Paper plugin with its own
    // Bootstrapper, which calls CommandAPI.onLoad() itself before any classic Bukkit-style
    // plugin's onLoad() runs. Calling it again here throws "onLoad() called more than once".

    @Override
    public void onEnable() {
        CommandAPI.onEnable();

        ConfigManager configManager = new ConfigManager(this);
        Messages messagesInstance = new Messages(configManager);
        TaskScheduler schedulerInstance = UniversalScheduler.getScheduler(this);

        try {
            horseRepository = new SqliteHorseRepository(getDataFolder(), configManager.getDatabaseFile(), schedulerInstance);
        } catch (RuntimeException e) {
            // SqliteHorseRepository already bounds connection attempts to a few seconds instead of
            // hanging (see its own comment), but if it still fails outright - a corrupted file,
            // a permissions problem, whatever - fail this plugin cleanly instead of letting the
            // exception surface as a half-initialized, harder-to-diagnose state.
            getLogger().severe("Could not initialize the database, disabling Equinox: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        StationRepository stationRepository = new YamlStationRepository(getDataFolder(), schedulerInstance);
        WorldGuardHook worldGuardHook = new WorldGuardHook();
        StationService stationServiceInstance = new StationService(stationRepository, worldGuardHook);

        EconomyProvider economyProvider = new StubEconomyProvider(getLogger());
        HorseService horseServiceInstance = new HorseService(this, configManager, horseRepository, stationServiceInstance,
                worldGuardHook, economyProvider, schedulerInstance, messagesInstance);

        instance = this;
        scheduler = schedulerInstance;
        messages = messagesInstance;
        stationService = stationServiceInstance;
        horseService = horseServiceInstance;

        // Loaded off the main thread so a stalled database can't block server startup at all -
        // both loads are independent (no station-depends-on-horse ordering needed), so they just
        // run side by side and each logs once it's actually done.
        stationServiceInstance.loadStationsIntoCache().whenComplete((count, throwable) -> {
            if (throwable != null) {
                getLogger().log(Level.SEVERE, "Failed to load stations", throwable);
            } else {
                getLogger().info("Loaded " + count + " station(s)");
            }
        });
        horseServiceInstance.loadOwnedHorsesIntoCache().whenComplete((count, throwable) -> {
            if (throwable != null) {
                getLogger().log(Level.SEVERE, "Failed to load owned horses", throwable);
            } else {
                getLogger().info("Loaded " + count + " owned horse(s)");
            }
        });

        getServer().getPluginManager().registerEvents(new HorseListener(horseServiceInstance, messagesInstance), this);

        StableMenu stableMenu = new StableMenu(this, configManager, horseServiceInstance, schedulerInstance);
        new StableCommand(this, stableMenu, horseServiceInstance, stationServiceInstance, messagesInstance).register();
    }

    @Override
    public void onDisable() {
        if (horseRepository != null) {
            horseRepository.close();
        }
        if (scheduler != null) {
            // Cancels this plugin's own pending scheduled tasks (e.g. the horses' follow loop)
            // so none of them fire after the database above is already closed.
            scheduler.cancelTasks();
        }
        instance = null;
        horseService = null;
        stationService = null;
        messages = null;
        scheduler = null;
        // Deliberately not calling CommandAPI.onDisable() here: CommandAPI runs as its own
        // separate plugin on this server (see plugin.yml's depend: [CommandAPI]), so its
        // CommandAPIHandler is a single shared instance used by every dependent plugin.
        // Calling onDisable() from a dependent plugin tears that shared instance down for
        // everyone, not just Equinox - only the actual CommandAPI plugin should do that.
    }

    public static Equinox getInstance() {
        return instance;
    }

    public static HorseService getHorseService() {
        return horseService;
    }

    public static StationService getStationService() {
        return stationService;
    }

    public static Messages getMessages() {
        return messages;
    }

    public static TaskScheduler getScheduler() {
        return scheduler;
    }
}
