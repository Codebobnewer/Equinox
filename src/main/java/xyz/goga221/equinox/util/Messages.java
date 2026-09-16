package xyz.goga221.equinox.util;

import xyz.goga221.equinox.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

public final class Messages {

    private final ConfigManager config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(ConfigManager config) {
        this.config = config;
    }

    public Component render(String key, TagResolver... resolvers) {
        return miniMessage.deserialize(config.getMessage(key), resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(render(key, resolvers));
    }
}
