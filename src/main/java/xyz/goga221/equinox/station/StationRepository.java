package xyz.goga221.equinox.station;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Persists stations - hand-editable definitional data, so the implementation is YAML, not a database. */
public interface StationRepository {

    CompletableFuture<Void> save(Station station);

    /** Deletes the station, then reports whether it existed and was removed. */
    CompletableFuture<Boolean> delete(String name);

    CompletableFuture<List<Station>> loadAll();
}
