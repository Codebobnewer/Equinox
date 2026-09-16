package dev.goga221.equinox.command;

import dev.goga221.equinox.gui.StableMenu;
import dev.goga221.equinox.horse.HorseManager;
import dev.goga221.equinox.station.StationManager;
import dev.goga221.equinox.station.StationType;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.plugin.java.JavaPlugin;

public final class StableCommand {

    private final JavaPlugin plugin;
    private final StableMenu stableMenu;
    private final HorseManager horseManager;
    private final StationManager stationManager;

    public StableCommand(JavaPlugin plugin, StableMenu stableMenu, HorseManager horseManager, StationManager stationManager) {
        this.plugin = plugin;
        this.stableMenu = stableMenu;
        this.horseManager = horseManager;
        this.stationManager = stationManager;
    }

    public void register() {
        new CommandAPICommand("stable")
                .withPermission("equinox.use")
                .executesPlayer((player, args) -> {
                    stableMenu.open(player);
                })
                .withSubcommand(new CommandAPICommand("sell")
                        .withPermission("equinox.use")
                        .executesPlayer((player, args) -> {
                            horseManager.sell(player);
                        }))
                .withSubcommand(stationSubcommand())
                .register(plugin);
    }

    private CommandAPICommand stationSubcommand() {
        return new CommandAPICommand("station")
                .withPermission("equinox.admin.station")
                .withSubcommand(new CommandAPICommand("setbuy")
                        .withArguments(new StringArgument("name"), new StringArgument("region"))
                        .executesPlayer((player, args) -> {
                            String name = (String) args.get("name");
                            String region = (String) args.get("region");
                            boolean created = stationManager.createStation(name, StationType.BUY, player.getWorld(), region);
                            player.sendMessage(created
                                    ? "Buy station '" + name + "' set to region '" + region + "'."
                                    : "No region named '" + region + "' found in " + player.getWorld().getName() + ".");
                        }))
                .withSubcommand(new CommandAPICommand("setsell")
                        .withArguments(new StringArgument("name"), new StringArgument("region"))
                        .executesPlayer((player, args) -> {
                            String name = (String) args.get("name");
                            String region = (String) args.get("region");
                            boolean created = stationManager.createStation(name, StationType.SELL, player.getWorld(), region);
                            player.sendMessage(created
                                    ? "Sell station '" + name + "' set to region '" + region + "'."
                                    : "No region named '" + region + "' found in " + player.getWorld().getName() + ".");
                        }))
                .withSubcommand(new CommandAPICommand("remove")
                        .withArguments(new StringArgument("name"))
                        .executesPlayer((player, args) -> {
                            String name = (String) args.get("name");
                            boolean removed = stationManager.removeStation(name);
                            player.sendMessage(removed ? "Removed station '" + name + "'." : "No station named '" + name + "'.");
                        }))
                .withSubcommand(new CommandAPICommand("list")
                        .executesPlayer((player, args) -> {
                            var stations = stationManager.list();
                            if (stations.isEmpty()) {
                                player.sendMessage("No stations configured.");
                                return;
                            }
                            stations.forEach(station -> player.sendMessage(
                                    "- " + station.name() + " [" + station.type() + "] world=" + station.world()
                                            + " region=" + station.regionId()));
                        }));
    }
}
