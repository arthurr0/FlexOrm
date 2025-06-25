package pl.minecodes.orm.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SQLiteConnectionTest {

  @TempDir
  Path tempDir;

  private SQLiteConnection connection;
  private ConnectionCredentials credentials;
  private File dbFile;

  @BeforeEach
  void setUp() {
    dbFile = tempDir.resolve("test.db").toFile();
    credentials = new ConnectionCredentials(null, 0, "test.db", null, null, dbFile);
    connection = new SQLiteConnection(credentials);
  }

  @AfterEach
  void tearDown() {
    try {
      HikariDataSource dataSource = connection.getConnection();
      if (dataSource != null && !dataSource.isClosed()) {
        dataSource.close();
      }
    } catch (IllegalStateException e) {
      //Ignore
    }
  }

  @Test
  void testConnect() {
    connection.connect();

    assertNotNull(connection.getConnection());
    assertFalse(connection.getConnection().isClosed());
  }

  @Test
  void testConnectTwiceReturnsSameDataSource() {
    connection.connect();
    HikariDataSource firstDataSource = connection.getConnection();

    connection.connect();
    HikariDataSource secondDataSource = connection.getConnection();

    assertSame(firstDataSource, secondDataSource);
  }

  @Test
  void testGetConnectionThrowsExceptionWhenNotConnected() {
    assertThrows(IllegalStateException.class, () -> connection.getConnection());
  }

  @Test
  void testConnectionCanExecuteQuery() throws SQLException {
    connection.connect();

    try (var conn = connection.getConnection().getConnection(); var stmt = conn.createStatement()) {
      var rs = stmt.executeQuery("SELECT 1");
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
    }
  }

  @Test
  void testDatabaseFileIsCreated() {
    assertFalse(dbFile.exists());

    connection.connect();

    try (var conn = connection.getConnection().getConnection()) {
      assertTrue(dbFile.exists());
    } catch (SQLException e) {
      fail("Nie udało się połączyć z bazą danych", e);
    }
  }

  @Test
  void testWithDatabasePathInsteadOfFile() {
    String dbPath = tempDir.resolve("test-path.db").toString();
    credentials = new ConnectionCredentials(null, 0, dbPath, null, null, null);
    connection = new SQLiteConnection(credentials);

    connection.connect();

    assertNotNull(connection.getConnection());
    assertFalse(connection.getConnection().isClosed());

    File createdDbFile = new File(dbPath);
    assertTrue(createdDbFile.exists());
  }
}
