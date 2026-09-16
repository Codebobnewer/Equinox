package xyz.goga221.equinox.economy;

import org.bukkit.entity.Player;

/**
 * Abstraction over whatever plugin/store actually holds player balances.
 * Swap {@link StubEconomyProvider} for a Vault-backed implementation later
 * without touching any calling code.
 */
public interface EconomyProvider {

    boolean has(Player player, double amount);

    void withdraw(Player player, double amount);

    void deposit(Player player, double amount);
}
