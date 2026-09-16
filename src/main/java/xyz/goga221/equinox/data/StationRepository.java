package xyz.goga221.equinox.data;

import xyz.goga221.equinox.station.Station;
import xyz.goga221.equinox.station.StationType;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Plain JDBC CRUD against the {@code stations} table - no ORM, just prepared statements. */
@RequiredArgsConstructor
public final class StationRepository {

    private final DatabaseManager database;
    private final Logger logger;

    /** Only called once, at startup, to seed {@link xyz.goga221.equinox.station.StationService}'s cache off the main thread. */
    public CompletableFuture<List<Station>> findAllAsync(TaskScheduler scheduler) {
        CompletableFuture<List<Station>> future = new CompletableFuture<>();
        scheduler.runTaskAsynchronously(() -> {
            try {
                future.complete(loadAll());
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private List<Station> loadAll() throws SQLException {
        List<Station> stations = new ArrayList<>();
        String sql = "SELECT * FROM stations";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                stations.add(map(resultSet));
            }
        }
        return stations;
    }

    public void insert(Station station) {
        String sql = """
                INSERT OR REPLACE INTO stations (name, type, world, region_id, x, y, z)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, station.name());
            statement.setString(2, station.type().name());
            statement.setString(3, station.world());
            statement.setString(4, station.regionId());
            statement.setDouble(5, station.x());
            statement.setDouble(6, station.y());
            statement.setDouble(7, station.z());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to insert station", e);
        }
    }

    public void delete(String name) {
        String sql = "DELETE FROM stations WHERE name = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete station", e);
        }
    }

    private Station map(ResultSet resultSet) throws SQLException {
        return new Station(
                resultSet.getString("name"),
                StationType.valueOf(resultSet.getString("type")),
                resultSet.getString("world"),
                resultSet.getString("region_id"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z")
        );
    }
}
