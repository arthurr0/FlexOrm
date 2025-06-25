package pl.minecodes.orm.connection;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteConnectionTest {

    @TempDir
    Path tempDir;
    
    private SQLiteConnection connection;
    private ConnectionCredentials credentials;
    private File dbFile;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("test.db").toFile();
        credentials = new ConnectionCredentials(
                null, 0, "test.db", null, null, dbFile
        );
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
            // Ignoruj, jeśli połączenie nie zostało jeszcze utworzone
        }
    }

    @Test
    void testConnect() {
        // when
        connection.connect();
        
        // then
        assertNotNull(connection.getConnection());
        assertFalse(connection.getConnection().isClosed());
    }

    @Test
    void testConnectTwiceReturnsSameDataSource() {
        // when
        connection.connect();
        HikariDataSource firstDataSource = connection.getConnection();
        connection.connect();
        HikariDataSource secondDataSource = connection.getConnection();
        
        // then
        assertSame(firstDataSource, secondDataSource);
    }

    @Test
    void testGetConnectionThrowsExceptionWhenNotConnected() {
        // then
        assertThrows(IllegalStateException.class, () -> connection.getConnection());
    }

    @Test
    void testConnectionCanExecuteQuery() throws SQLException {
        // given
        connection.connect();
        
        // when - then (no exception thrown means success)
        try (var conn = connection.getConnection().getConnection();
             var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT 1");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void testDatabaseFileIsCreated() {
        // given
        assertFalse(dbFile.exists());
        
        // when
        connection.connect();
        
        // then
        try (var conn = connection.getConnection().getConnection()) {
            assertTrue(dbFile.exists());
        } catch (SQLException e) {
            fail("Nie udało się połączyć z bazą danych", e);
        }
    }

    @Test
    void testWithDatabasePathInsteadOfFile() {
        // given
        String dbPath = tempDir.resolve("test-path.db").toString();
        credentials = new ConnectionCredentials(
                null, 0, dbPath, null, null, null
        );
        connection = new SQLiteConnection(credentials);
        
        // when
        connection.connect();
        
        // then
        assertNotNull(connection.getConnection());
        assertFalse(connection.getConnection().isClosed());
        
        File createdDbFile = new File(dbPath);
        assertTrue(createdDbFile.exists());
    }
}
