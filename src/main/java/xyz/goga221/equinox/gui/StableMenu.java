package xyz.goga221.equinox.gui;

import xyz.goga221.equinox.config.ConfigManager;
import xyz.goga221.equinox.config.TierDefinition;
import xyz.goga221.equinox.horse.HorseService;
import xyz.goga221.equinox.horse.HorseTier;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;

/** Builds and opens the InvUI window shown for {@code /stable} - one item per horse tier. */
@RequiredArgsConstructor
public final class StableMenu {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final HorseService horseService;
    private final TaskScheduler scheduler;

    public void open(Player player) {
        boolean alreadyOwnsHorse = horseService.hasHorse(player.getUniqueId());

        Structure structure = new Structure(
                "#########",
                "##A#B#C##",
                "#########"
        );
        structure.addIngredient('#', new SimpleItem(tagAsMenuItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName(new AdventureComponentWrapper(Component.empty())))));
        structure.addIngredient('A', tierItem(player, HorseTier.BASIC, Material.LEATHER_HORSE_ARMOR, alreadyOwnsHorse));
        structure.addIngredient('B', tierItem(player, HorseTier.ADVANCED, Material.IRON_HORSE_ARMOR, alreadyOwnsHorse));
        structure.addIngredient('C', tierItem(player, HorseTier.ELITE, Material.GOLDEN_HORSE_ARMOR, alreadyOwnsHorse));

        Gui gui = Gui.normal().setStructure(structure).build();

        Window.single()
                .setViewer(player)
                .setGui(gui)
                .setTitle(new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<dark_green>Stable")))
                // A held hotbar-slot key combined with Esc can race the close packet, letting the
                // client's hotbar-swap land against the GUI's slot after InvUI has already stopped
                // guarding it - the real display ItemStack ends up sitting in the player's hotbar.
                // Every item this menu renders carries the marker from tagAsMenuItem(), so on close
                // we strip any marked item that leaked into the player's real inventory and resync.
                .addCloseHandler(() -> scheduler.runTask(player, () -> {
                    purgeLeakedMenuItems(player);
                    player.updateInventory();
                }))
                .build()
                .open();
    }

    private SimpleItem tierItem(Player player, HorseTier tier, Material material, boolean alreadyOwnsHorse) {
        TierDefinition definition = config.getTier(tier);
        if (definition == null) {
            return new SimpleItem(tagAsMenuItem(new ItemBuilder(Material.BARRIER)
                    .setDisplayName(new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<red>Not configured")))));
        }

        ItemBuilder builder = tagAsMenuItem(new ItemBuilder(material)
                .setDisplayName(new AdventureComponentWrapper(MINI_MESSAGE.deserialize(definition.getDisplayName())))
                .addLoreLines(
                        new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<gray>Price: <white>" + definition.getPrice())),
                        new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<gray>Health: <white>" + definition.getHealth())),
                        new AdventureComponentWrapper(MINI_MESSAGE.deserialize("<gray>Speed: <white>" + definition.getSpeed())),
                        new AdventureComponentWrapper(alreadyOwnsHorse
                                ? MINI_MESSAGE.deserialize("<red>You already own a horse")
                                : Component.empty())
                ));

        return new SimpleItem(builder, click -> {
            // Vulcan's menus always navigate away on click, so a GUI window never sits open and
            // idle waiting for further input - that's what closes off the hold-hotbar-key-then-Esc
            // race, not anything in the click cancellation itself. Purchasing has nowhere to
            // navigate to, so mirror that by closing the window outright as soon as it's clicked.
            player.closeInventory();
            horseService.purchase(player, tier);
        });
    }

    private ItemBuilder tagAsMenuItem(ItemBuilder builder) {
        return builder.addModifier(itemStack -> {
            ItemMeta meta = itemStack.getItemMeta();
            meta.getPersistentDataContainer().set(menuItemKey(), PersistentDataType.BYTE, (byte) 1);
            itemStack.setItemMeta(meta);
            return itemStack;
        });
    }

    private NamespacedKey menuItemKey() {
        return new NamespacedKey(plugin, "stable-menu-item");
    }

    private void purgeLeakedMenuItems(Player player) {
        NamespacedKey key = menuItemKey();
        ItemStack[] contents = player.getInventory().getContents();
        boolean leaked = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                player.getInventory().setItem(slot, null);
                leaked = true;
            }
        }
        if (leaked) {
            plugin.getLogger().warning("Stripped a leaked Stable menu item from " + player.getName() + "'s inventory");
        }
    }
}
