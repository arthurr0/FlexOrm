package pl.minecodes.orm.migration;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pl.minecodes.orm.DatabaseType;
import pl.minecodes.orm.FlexOrm;

public class MigrationManager {

  private static final String MIGRATIONS_TABLE = "_orm_migrations";
  private final FlexOrm orm;
  private final List<Migration> migrations = new ArrayList<>();

  public MigrationManager(FlexOrm orm) {
    this.orm = orm;
  }

  public MigrationManager addMigration(Migration migration) {
    migrations.add(migration);
    return this;
  }

  public void migrate() {
    if (orm.getDatabaseType() == DatabaseType.MONGODB) {
      throw new UnsupportedOperationException("Migrations are not supported for MongoDB");
    }

    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      createMigrationsTableIfNotExists(connection);

      int currentVersion = getCurrentVersion(connection);

      List<Migration> pendingMigrations = migrations.stream()
          .filter(m -> m.version() > currentVersion)
          .sorted(Comparator.comparingInt(Migration::version))
          .toList();

      for (Migration migration : pendingMigrations) {
        applyMigration(connection, migration);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error during migration: " + e.getMessage(), e);
    }
  }

  public void rollback() {
    rollback(1);
  }

  public void rollback(int steps) {
    if (orm.getDatabaseType() == DatabaseType.MONGODB) {
      throw new UnsupportedOperationException("Migrations are not supported for MongoDB");
    }

    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      List<Integer> appliedVersions = getAppliedVersions(connection);

      int count = 0;
      for (int i = appliedVersions.size() - 1; i >= 0 && count < steps; i--, count++) {
        int version = appliedVersions.get(i);
        Migration migration = findMigrationByVersion(version);
        if (migration != null) {
          rollbackMigration(connection, migration);
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error during rollback: " + e.getMessage(), e);
    }
  }

  public int getCurrentVersion(Connection connection) throws SQLException {
    String sql = "SELECT MAX(version) FROM " + MIGRATIONS_TABLE;
    try (PreparedStatement stmt = connection.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      if (rs.next()) {
        return rs.getInt(1);
      }
      return 0;
    }
  }

  private void createMigrationsTableIfNotExists(Connection connection) throws SQLException {
    String sql;
    if (orm.getDatabaseType() == DatabaseType.MYSQL) {
      sql = "CREATE TABLE IF NOT EXISTS " + MIGRATIONS_TABLE + " (" +
          "version INT PRIMARY KEY, " +
          "description VARCHAR(255), " +
          "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
    } else {
      sql = "CREATE TABLE IF NOT EXISTS " + MIGRATIONS_TABLE + " (" +
          "version INTEGER PRIMARY KEY, " +
          "description TEXT, " +
          "applied_at TEXT DEFAULT CURRENT_TIMESTAMP)";
    }

    try (Statement stmt = connection.createStatement()) {
      stmt.execute(sql);
    }
  }

  private void applyMigration(Connection connection, Migration migration) throws SQLException {
    connection.setAutoCommit(false);
    try {
      try (Statement stmt = connection.createStatement()) {
        stmt.execute(migration.upSql());
      }

      String insertSql =
          "INSERT INTO " + MIGRATIONS_TABLE + " (version, description) VALUES (?, ?)";
      try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
        stmt.setInt(1, migration.version());
        stmt.setString(2, migration.description());
        stmt.executeUpdate();
      }

      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  private void rollbackMigration(Connection connection, Migration migration) throws SQLException {
    connection.setAutoCommit(false);
    try {
      try (Statement stmt = connection.createStatement()) {
        stmt.execute(migration.downSql());
      }

      String deleteSql = "DELETE FROM " + MIGRATIONS_TABLE + " WHERE version = ?";
      try (PreparedStatement stmt = connection.prepareStatement(deleteSql)) {
        stmt.setInt(1, migration.version());
        stmt.executeUpdate();
      }

      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  private List<Integer> getAppliedVersions(Connection connection) throws SQLException {
    List<Integer> versions = new ArrayList<>();
    String sql = "SELECT version FROM " + MIGRATIONS_TABLE + " ORDER BY version ASC";
    try (PreparedStatement stmt = connection.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        versions.add(rs.getInt(1));
      }
    }
    return versions;
  }

  private Migration findMigrationByVersion(int version) {
    return migrations.stream()
        .filter(m -> m.version() == version)
        .findFirst()
        .orElse(null);
  }
}
