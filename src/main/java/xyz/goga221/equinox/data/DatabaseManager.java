package xyz.goga221.equinox.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private final HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, String fileName) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), fileName).getAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        // SQLite only supports one writer at a time; a single pooled connection avoids
        // "database is locked" errors instead of trying to parallelize writes.
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setPoolName("equinox-sqlite");
        // This constructor runs synchronously during the server's plugin-enable sequence, on the
        // thread Paper's watchdog monitors. Without bounded timeouts, a stalled connection (a
        // locked file left over from an unclean shutdown, a slow/contended disk, etc.) would sit
        // on HikariCP's 30s default - or SQLite's own indefinite lock wait - and could trip the
        // watchdog and take the whole server down, not just fail to load this plugin. A local
        // SQLite file should connect in milliseconds under normal conditions, so these bounds are
        // generous while still failing fast instead of hanging.
        hikariConfig.setConnectionTimeout(5_000);
        hikariConfig.setInitializationFailTimeout(5_000);
        hikariConfig.setValidationTimeout(3_000);
        hikariConfig.addDataSourceProperty("busy_timeout", "5000");

        this.dataSource = new HikariDataSource(hikariConfig);
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
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_owned_horses_owner ON owned_horses(owner_uuid)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS stations (
                        name TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        world TEXT NOT NULL,
                        region_id TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize Equinox database schema", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        dataSource.close();
    }
}
