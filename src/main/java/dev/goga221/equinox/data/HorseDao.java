package dev.goga221.equinox.data;

import dev.goga221.equinox.horse.HorseTier;
import dev.goga221.equinox.horse.OwnedHorse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HorseDao {

    private final DatabaseManager database;
    private final Logger logger;

    public HorseDao(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /**
     * Only called once, at startup, to seed {@link dev.goga221.equinox.horse.HorseManager}'s
     * in-memory ownership cache - everything else reads that cache instead of hitting the
     * database, so purchase/sell/menu-open never block the calling thread on disk I/O.
     */
    public List<OwnedHorse> findAll() {
        List<OwnedHorse> horses = new ArrayList<>();
        String sql = "SELECT * FROM owned_horses";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                horses.add(map(resultSet));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load owned horses", e);
        }
        return horses;
    }

    public void insert(OwnedHorse horse) {
        String sql = """
                INSERT INTO owned_horses (horse_uuid, owner_uuid, tier, cost_paid, world, x, y, z, purchased_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, horse.horseUuid().toString());
            statement.setString(2, horse.ownerUuid().toString());
            statement.setString(3, horse.tier().name());
            statement.setDouble(4, horse.costPaid());
            statement.setString(5, horse.world());
            statement.setDouble(6, horse.x());
            statement.setDouble(7, horse.y());
            statement.setDouble(8, horse.z());
            statement.setLong(9, horse.purchasedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to insert owned horse", e);
        }
    }

    public void delete(UUID horseUuid) {
        String sql = "DELETE FROM owned_horses WHERE horse_uuid = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, horseUuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete owned horse", e);
        }
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
}
