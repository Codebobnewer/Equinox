package xyz.goga221.equinox.command;

import xyz.goga221.equinox.gui.StableMenu;
import xyz.goga221.equinox.horse.HorseService;
import xyz.goga221.equinox.station.Station;
import xyz.goga221.equinox.station.StationService;
import xyz.goga221.equinox.station.StationType;
import xyz.goga221.equinox.util.Messages;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Registers the whole {@code /stable} command tree: the player-facing menu/sell, and the admin station subcommands. */
@RequiredArgsConstructor
public final class StableCommand {

    private final JavaPlugin plugin;
    private final StableMenu stableMenu;
    private final HorseService horseService;
    private final StationService stationService;
    private final Messages messages;

    public void register() {
        new CommandAPICommand("stable")
                .withPermission("equinox.use")
                .executesPlayer((player, args) -> {
                    stableMenu.open(player);
                })
                .withSubcommand(new CommandAPICommand("sell")
                        .withPermission("equinox.use")
                        .executesPlayer((player, args) -> {
                            horseService.sell(player);
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
                            registerStation(player, name, region, StationType.BUY);
                        }))
                .withSubcommand(new CommandAPICommand("setsell")
                        .withArguments(new StringArgument("name"), new StringArgument("region"))
                        .executesPlayer((player, args) -> {
                            String name = (String) args.get("name");
                            String region = (String) args.get("region");
                            registerStation(player, name, region, StationType.SELL);
                        }))
                .withSubcommand(new CommandAPICommand("remove")
                        .withArguments(new StringArgument("name"))
                        .executesPlayer((player, args) -> {
                            String name = (String) args.get("name");
                            stationService.removeStation(name)
                                    .thenAccept(removed -> messages.send(player, removed ? "station-removed" : "station-not-found",
                                            Placeholder.unparsed("name", name)))
                                    .exceptionally(throwable -> {
                                        messages.send(player, "station-save-failed");
                                        return null;
                                    });
                        }))
                .withSubcommand(new CommandAPICommand("list")
                        .executesPlayer((player, args) -> {
                            List<Station> stations = stationService.list();
                            if (stations.isEmpty()) {
                                messages.send(player, "station-list-empty");
                                return;
                            }
                            stations.forEach(station -> messages.send(player, "station-list-entry",
                                    Placeholder.unparsed("name", station.getName()),
                                    Placeholder.unparsed("type", station.getType().name()),
                                    Placeholder.unparsed("world", station.getWorld()),
                                    Placeholder.unparsed("region", station.getRegionId())));
                        }));
    }

    private void registerStation(Player player, String name, String region, StationType type) {
        stationService.createStation(name, type, player.getWorld(), region)
                .thenAccept(result -> {
                    switch (result) {
                        case CREATED -> messages.send(player, "station-set-success",
                                Placeholder.unparsed("type", type == StationType.BUY ? "Buy" : "Sell"),
                                Placeholder.unparsed("name", name),
                                Placeholder.unparsed("region", region));
                        case REGION_NOT_FOUND -> messages.send(player, "station-set-failed",
                                Placeholder.unparsed("region", region),
                                Placeholder.unparsed("world", player.getWorld().getName()));
                        case NAME_TAKEN_BY_OTHER_TYPE -> messages.send(player, "station-name-taken",
                                Placeholder.unparsed("name", name));
                    }
                })
                .exceptionally(throwable -> {
                    messages.send(player, "station-save-failed");
                    return null;
                });
    }
}
