package pl.minecodes.orm.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MySQLConnectionTest {

  @Container
  private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
      .withDatabaseName("testdb")
      .withUsername("testuser")
      .withPassword("testpass");

  private MySQLConnection connection;
  private ConnectionCredentials credentials;

  @BeforeEach
  void setUp() {
    credentials = new ConnectionCredentials(
        mysqlContainer.getHost(),
        mysqlContainer.getFirstMappedPort(),
        mysqlContainer.getDatabaseName(),
        mysqlContainer.getUsername(),
        mysqlContainer.getPassword(),
        null
    );
    connection = new MySQLConnection(credentials);
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
  void testHikariConfigHasCorrectProperties() {
    connection.connect();

    HikariDataSource dataSource = connection.getConnection();

    String jdbcUrl = dataSource.getJdbcUrl();
    assertTrue(jdbcUrl.contains(credentials.hostname()));
    assertTrue(jdbcUrl.contains(String.valueOf(credentials.port())));
    assertTrue(jdbcUrl.contains(credentials.database()));
    assertEquals(credentials.username(), dataSource.getUsername());
    assertEquals(credentials.password(), dataSource.getPassword());
  }
}
