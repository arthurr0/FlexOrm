package pl.minecodes.orm.connection;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MongoConnectionTest {

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.4");

    private MongoConnection connection;
    private ConnectionCredentials credentials;

    @BeforeEach
    void setUp() {
        credentials = new ConnectionCredentials(
                mongoDBContainer.getHost(),
                mongoDBContainer.getFirstMappedPort(),
                "testdb",
                "testuser",
                "testpass",
                null
        );
        connection = new MongoConnection(credentials);
    }

    @AfterEach
    void tearDown() {
        // Nie ma potrzeby zamykania MongoDatabase, ale możemy spróbować zamknąć klienta
        try {
            MongoDatabase database = connection.getConnection();
            if (database != null) {
                // Możemy oczyścić kolekcje testowe
                for (String collectionName : database.listCollectionNames()) {
                    database.getCollection(collectionName).drop();
                }
            }
        } catch (IllegalStateException e) {
            // Ignoruj, jeśli połączenie nie zostało utworzone
        }
    }

    @Test
    void testConnect() {
        // when
        connection.connect();
        
        // then
        assertNotNull(connection.getConnection());
    }

    @Test
    void testConnectTwiceReturnsSameDatabase() {
        // when
        connection.connect();
        MongoDatabase firstDatabase = connection.getConnection();
        connection.connect();
        MongoDatabase secondDatabase = connection.getConnection();
        
        // then
        assertSame(firstDatabase, secondDatabase);
    }

    @Test
    void testGetConnectionThrowsExceptionWhenNotConnected() {
        // then
        assertThrows(IllegalStateException.class, () -> connection.getConnection());
    }

    @Test
    void testConnectionCanExecuteQuery() {
        // given
        connection.connect();
        
        // when - then (no exception thrown means success)
        MongoDatabase database = connection.getConnection();
        database.createCollection("testcollection");
        database.getCollection("testcollection").insertOne(new Document("test", "value"));
        
        Document result = database.getCollection("testcollection").find().first();
        assertNotNull(result);
        assertEquals("value", result.getString("test"));
    }

    @Test
    void testDatabaseHasCorrectName() {
        // given
        connection.connect();
        
        // when
        MongoDatabase database = connection.getConnection();
        
        // then
        assertEquals(credentials.database(), database.getName());
    }
}
