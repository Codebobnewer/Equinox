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
