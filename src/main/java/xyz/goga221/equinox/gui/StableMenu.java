package xyz.goga221.equinox.gui;

import xyz.goga221.equinox.config.ConfigManager;
import xyz.goga221.equinox.config.TierDefinition;
import xyz.goga221.equinox.horse.HorseManager;
import xyz.goga221.equinox.horse.HorseTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;

public final class StableMenu {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ConfigManager config;
    private final HorseManager horseManager;

    public StableMenu(ConfigManager config, HorseManager horseManager) {
        this.config = config;
        this.horseManager = horseManager;
    }

    public void open(Player player) {
        boolean alreadyOwnsHorse = horseManager.hasHorse(player.getUniqueId());

        Structure structure = new Structure(
                "#########",
                "##A#B#C##",
                "#########"
        );
        structure.addIngredient('#', new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName(new AdventureComponentWrapper(Component.empty()))));
        structure.addIngredient('A', tierItem(player, HorseTier.BASIC, Material.LEATHER_HORSE_ARMOR, alreadyOwnsHorse));
        structure.addIngredient('B', tierItem(player, HorseTier.ADVANCED, Material.IRON_HORSE_ARMOR, alreadyOwnsHorse));
        structure.addIngredient('C', tierItem(player, HorseTier.ELITE, Material.GOLDEN_HORSE_ARMOR, alreadyOwnsHorse));

        Gui gui = Gui.normal().setStructure(structure).build();

        Window.single()
                .setViewer(player)
                .setGui(gui)
                .setTitle(new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<dark_green>Stable")))
                .build()
                .open();
    }

    private SimpleItem tierItem(Player player, HorseTier tier, Material material, boolean alreadyOwnsHorse) {
        TierDefinition definition = config.getTier(tier);
        if (definition == null) {
            return new SimpleItem(new ItemBuilder(Material.BARRIER)
                    .setDisplayName(new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<red>Not configured"))));
        }

        ItemBuilder builder = new ItemBuilder(material)
                .setDisplayName(new AdventureComponentWrapper(MINI_MESSAGE.deserialize(definition.displayName())))
                .addLoreLines(
                        new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<gray>Price: <white>" + definition.price())),
                        new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<gray>Health: <white>" + definition.health())),
                        new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<gray>Speed: <white>" + definition.speed())),
                        new AdventureComponentWrapper(alreadyOwnsHorse
                                ? MINI_MESSAGE.deserialize("<red>You already own a horse")
                                : Component.empty())
                );

        return new SimpleItem(builder, click -> horseManager.purchase(player, tier));
    }
}
