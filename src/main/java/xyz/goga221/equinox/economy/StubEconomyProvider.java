package xyz.goga221.equinox.economy;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * No real economy plugin wired in yet. Every check succeeds and withdrawals/deposits
 * are only logged, so the purchase/sell flow can be exercised end to end. Replace with
 * a Vault-backed {@link EconomyProvider} once an economy plugin is chosen.
 */
@RequiredArgsConstructor
public final class StubEconomyProvider implements EconomyProvider {

    private final Logger logger;

    @Override
    public boolean has(Player player, double amount) {
        return true;
    }

    @Override
    public void withdraw(Player player, double amount) {
        logger.info(() -> "[stub-economy] Withdrew " + amount + " from " + player.getName());
    }

    @Override
    public void deposit(Player player, double amount) {
        logger.info(() -> "[stub-economy] Deposited " + amount + " to " + player.getName());
    }
}
