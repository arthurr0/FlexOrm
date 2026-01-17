package pl.minecodes.orm.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.minecodes.orm.FlexOrm;

class MigrationManagerTest {

  @TempDir
  Path tempDir;

  private FlexOrm flexOrm;
  private MigrationManager migrationManager;

  @BeforeEach
  void setUp() {
    File dbFile = tempDir.resolve("migration-test.db").toFile();
    flexOrm = FlexOrm.sqllite(dbFile);
    flexOrm.connect();
    migrationManager = new MigrationManager(flexOrm);
  }

  @Test
  void testMigrateCreatesTable() throws SQLException {
    migrationManager.addMigration(new TestMigration(1, "Create users table",
        "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)",
        "DROP TABLE users"));

    migrationManager.migrate();

    assertTrue(tableExists("users"));
  }

  @Test
  void testMigrateMultipleMigrations() throws SQLException {
    migrationManager
        .addMigration(new TestMigration(1, "Create users",
            "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)",
            "DROP TABLE users"))
        .addMigration(new TestMigration(2, "Create posts",
            "CREATE TABLE posts (id INTEGER PRIMARY KEY, title TEXT, user_id INTEGER)",
            "DROP TABLE posts"));

    migrationManager.migrate();

    assertTrue(tableExists("users"));
    assertTrue(tableExists("posts"));
  }

  @Test
  void testMigrateSkipsAlreadyApplied() throws SQLException {
    migrationManager.addMigration(new TestMigration(1, "Create users",
        "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)",
        "DROP TABLE users"));

    migrationManager.migrate();
    migrationManager.migrate();

    assertTrue(tableExists("users"));
  }

  @Test
  void testMigrateInOrder() throws SQLException {
    migrationManager
        .addMigration(new TestMigration(3, "Add email column",
            "ALTER TABLE users ADD COLUMN email TEXT",
            "SELECT 1"))
        .addMigration(new TestMigration(1, "Create users",
            "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)",
            "DROP TABLE users"))
        .addMigration(new TestMigration(2, "Add age column",
            "ALTER TABLE users ADD COLUMN age INTEGER",
            "SELECT 1"));

    migrationManager.migrate();

    assertTrue(tableExists("users"));
    assertTrue(columnExists("users", "age"));
    assertTrue(columnExists("users", "email"));
  }

  @Test
  void testRollbackSingleMigration() throws SQLException {
    migrationManager
        .addMigration(new TestMigration(1, "Create users",
            "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)",
            "DROP TABLE users"))
        .addMigration(new TestMigration(2, "Create posts",
            "CREATE TABLE posts (id INTEGER PRIMARY KEY, title TEXT)",
            "DROP TABLE posts"));

    migrationManager.migrate();
    assertTrue(tableExists("users"));
    assertTrue(tableExists("posts"));

    migrationManager.rollback();
    assertTrue(tableExists("users"));
    assertFalse(tableExists("posts"));
  }

  @Test
  void testRollbackMultipleSteps() throws SQLException {
    migrationManager
        .addMigration(new TestMigration(1, "Create users",
            "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)",
            "DROP TABLE users"))
        .addMigration(new TestMigration(2, "Create posts",
            "CREATE TABLE posts (id INTEGER PRIMARY KEY, title TEXT)",
            "DROP TABLE posts"))
        .addMigration(new TestMigration(3, "Create comments",
            "CREATE TABLE comments (id INTEGER PRIMARY KEY, text TEXT)",
            "DROP TABLE comments"));

    migrationManager.migrate();
    assertTrue(tableExists("users"));
    assertTrue(tableExists("posts"));
    assertTrue(tableExists("comments"));

    migrationManager.rollback(2);
    assertTrue(tableExists("users"));
    assertFalse(tableExists("posts"));
    assertFalse(tableExists("comments"));
  }

  @Test
  void testGetCurrentVersion() throws SQLException {
    migrationManager
        .addMigration(new TestMigration(1, "v1", "CREATE TABLE t1 (id INTEGER)", "DROP TABLE t1"))
        .addMigration(new TestMigration(2, "v2", "CREATE TABLE t2 (id INTEGER)", "DROP TABLE t2"));

    migrationManager.migrate();

    HikariDataSource dataSource = (HikariDataSource) flexOrm.getConnection().getConnection();
    try (Connection conn = dataSource.getConnection()) {
      int version = migrationManager.getCurrentVersion(conn);
      assertEquals(2, version);
    }
  }

  @Test
  void testMigrationsTableCreated() throws SQLException {
    migrationManager.addMigration(new TestMigration(1, "Test",
        "CREATE TABLE test (id INTEGER)",
        "DROP TABLE test"));

    migrationManager.migrate();

    assertTrue(tableExists("_orm_migrations"));
  }

  @Test
  void testMigrationTransactionRollbackOnError() throws SQLException {
    migrationManager
        .addMigration(new TestMigration(1, "Create users",
            "CREATE TABLE users (id INTEGER PRIMARY KEY)",
            "DROP TABLE users"))
        .addMigration(new TestMigration(2, "Invalid SQL",
            "THIS IS INVALID SQL",
            "SELECT 1"));

    assertThrows(RuntimeException.class, () -> migrationManager.migrate());

    assertTrue(tableExists("users"));
  }

  @Test
  void testEmptyMigrations() {
    assertDoesNotThrow(() -> migrationManager.migrate());
  }

  @Test
  void testRollbackWithNoAppliedMigrations() throws SQLException {
    migrationManager.addMigration(new TestMigration(1, "Test",
        "CREATE TABLE test (id INTEGER)",
        "DROP TABLE test"));

    migrationManager.migrate();
    migrationManager.rollback();

    assertFalse(tableExists("test"));
  }

  private boolean tableExists(String tableName) throws SQLException {
    HikariDataSource dataSource = (HikariDataSource) flexOrm.getConnection().getConnection();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
      return rs.next();
    }
  }

  private boolean columnExists(String tableName, String columnName) throws SQLException {
    HikariDataSource dataSource = (HikariDataSource) flexOrm.getConnection().getConnection();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
      while (rs.next()) {
        if (columnName.equals(rs.getString("name"))) {
          return true;
        }
      }
      return false;
    }
  }

    record TestMigration(int version, String description, String upSql, String downSql) implements
        Migration {

    }
}
