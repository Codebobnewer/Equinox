package xyz.goga221.equinox.horse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists owned-horse records. Ownership is core gameplay state, not analytics/logging, so
 * unlike a pure log sink this data is authoritative - the implementation is a database (SQLite),
 * not a hand-editable file.
 */
public interface HorseRepository {

    CompletableFuture<List<OwnedHorse>> loadAll();

    CompletableFuture<Void> save(OwnedHorse horse);

    CompletableFuture<Void> delete(UUID horseUuid);
}
