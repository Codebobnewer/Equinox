package dev.goga221.equinox.horse;

import java.util.UUID;

/**
 * world/x/y/z record where the horse was purchased (station location at spawn time) - purely
 * informational; the sell flow always checks the horse's live current position, not this.
 */
public record OwnedHorse(
        UUID horseUuid,
        UUID ownerUuid,
        HorseTier tier,
        double costPaid,
        String world,
        double x,
        double y,
        double z,
        long purchasedAt
) {
}
