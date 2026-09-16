package dev.goga221.equinox.data;

import dev.goga221.equinox.station.Station;
import dev.goga221.equinox.station.StationType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StationDao {

    private final DatabaseManager database;
    private final Logger logger;

    public StationDao(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public List<Station> findAll() {
        List<Station> stations = new ArrayList<>();
        String sql = "SELECT * FROM stations";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                stations.add(map(resultSet));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load stations", e);
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
