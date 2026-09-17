package xyz.goga221.equinox.horse;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

import java.util.UUID;

/**
 * world/x/y/z is where the horse was purchased (station location at spawn time) - purely
 * informational; the sell flow always checks the horse's live current position, not this.
 */
@Getter
@With
@AllArgsConstructor
public class OwnedHorse {

    private final UUID horseUuid;
    private final UUID ownerUuid;
    private final HorseTier tier;
    private final double costPaid;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final long purchasedAt;
}
