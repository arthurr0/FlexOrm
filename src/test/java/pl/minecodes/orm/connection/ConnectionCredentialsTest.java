package pl.minecodes.orm.connection;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionCredentialsTest {

    @Test
    void testConnectionCredentialsCreation() {
        String hostname = "localhost";
        int port = 3306;
        String database = "testdb";
        String username = "user";
        String password = "pass";
        File databaseFile = new File("test.db");

        ConnectionCredentials credentials = new ConnectionCredentials(hostname, port, database, username, password, databaseFile);

        assertEquals(hostname, credentials.hostname());
        assertEquals(port, credentials.port());
        assertEquals(database, credentials.database());
        assertEquals(username, credentials.username());
        assertEquals(password, credentials.password());
        assertEquals(databaseFile, credentials.databaseFile());
    }

    @Test
    void testEquality() {
        ConnectionCredentials credentials1 = new ConnectionCredentials("localhost", 3306, "testdb", "user", "pass", null);
        ConnectionCredentials credentials2 = new ConnectionCredentials("localhost", 3306, "testdb", "user", "pass", null);
        ConnectionCredentials credentials3 = new ConnectionCredentials("otherhost", 3306, "testdb", "user", "pass", null);

        assertEquals(credentials1, credentials2);
        assertNotEquals(credentials1, credentials3);
    }
}
