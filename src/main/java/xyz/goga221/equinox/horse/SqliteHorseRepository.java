package xyz.goga221.equinox.horse;

import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQLite-backed (via HikariCP) implementation, storing to {@code equinox.db} in the plugin's
 * data folder. Self-contained (owns its own connection pool) rather than sharing a generic
 * "database manager" abstraction, since this is the only thing in the plugin that needs one.
 */
public final class SqliteHorseRepository implements HorseRepository, AutoCloseable {

    private final HikariDataSource dataSource;
    private final TaskScheduler scheduler;

    public SqliteHorseRepository(File dataFolder, String fileName, TaskScheduler scheduler) {
        this.scheduler = scheduler;

        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder: " + dataFolder);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, fileName).getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        // SQLite only supports one writer at a time; a single pooled connection avoids
        // "database is locked" errors instead of trying to parallelize writes.
        config.setMaximumPoolSize(1);
        config.setPoolName("equinox-sqlite");
        // This constructor runs synchronously during the server's plugin-enable sequence, on the
        // thread Paper's watchdog monitors. Without bounded timeouts, a stalled connection (a
        // locked file left over from an unclean shutdown, a slow/contended disk, etc.) could sit
        // on HikariCP's 30s default - or SQLite's own indefinite lock wait - and could trip the
        // watchdog and take the whole server down, not just fail to load this plugin. A local
        // SQLite file should connect in milliseconds under normal conditions, so these bounds are
        // generous while still failing fast instead of hanging.
        config.setConnectionTimeout(5_000);
        config.setInitializationFailTimeout(5_000);
        config.setValidationTimeout(3_000);
        config.addDataSourceProperty("busy_timeout", "5000");

        this.dataSource = new HikariDataSource(config);
        createSchema();
    }

    private void createSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS owned_horses (
                        horse_uuid TEXT PRIMARY KEY,
                        owner_uuid TEXT NOT NULL,
                        tier TEXT NOT NULL,
                        cost_paid REAL NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        purchased_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_owned_horses_owner ON owned_horses(owner_uuid)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize the owned-horses database", e);
        }
    }

    @Override
    public CompletableFuture<List<OwnedHorse>> loadAll() {
        CompletableFuture<List<OwnedHorse>> future = new CompletableFuture<>();
        scheduler.runTaskAsynchronously(() -> {
            try {
                List<OwnedHorse> horses = new ArrayList<>();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT * FROM owned_horses");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        horses.add(map(resultSet));
                    }
                }
                future.complete(horses);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> save(OwnedHorse horse) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.runTaskAsynchronously(() -> {
            String sql = """
                    INSERT INTO owned_horses (horse_uuid, owner_uuid, tier, cost_paid, world, x, y, z, purchased_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, horse.getHorseUuid().toString());
                statement.setString(2, horse.getOwnerUuid().toString());
                statement.setString(3, horse.getTier().name());
                statement.setDouble(4, horse.getCostPaid());
                statement.setString(5, horse.getWorld());
                statement.setDouble(6, horse.getX());
                statement.setDouble(7, horse.getY());
                statement.setDouble(8, horse.getZ());
                statement.setLong(9, horse.getPurchasedAt());
                statement.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> delete(UUID horseUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.runTaskAsynchronously(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM owned_horses WHERE horse_uuid = ?")) {
                statement.setString(1, horseUuid.toString());
                statement.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private OwnedHorse map(ResultSet resultSet) throws SQLException {
        return new OwnedHorse(
                UUID.fromString(resultSet.getString("horse_uuid")),
                UUID.fromString(resultSet.getString("owner_uuid")),
                HorseTier.valueOf(resultSet.getString("tier")),
                resultSet.getDouble("cost_paid"),
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getLong("purchased_at")
        );
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
