package pl.minecodes.orm.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
        "test",
        null,
        null,
        null
    );

    connection = new MongoConnection(credentials);
  }

  @Test
  void testConnect() {
    connection.connect();

    assertNotNull(connection.getConnection());
  }

  @Test
  void testConnectTwiceReturnsSameDatabase() {
    connection.connect();
    MongoDatabase firstDatabase = connection.getConnection();

    connection.connect();
    MongoDatabase secondDatabase = connection.getConnection();

    assertSame(firstDatabase, secondDatabase);
  }

  @Test
  void testGetConnectionThrowsExceptionWhenNotConnected() {
    assertThrows(IllegalStateException.class, () -> connection.getConnection());
  }

  @Test
  void testConnectionCanExecuteQuery() {
    connection.connect();

    MongoDatabase database = connection.getConnection();
    database.createCollection("testcollection");
    database.getCollection("testcollection").insertOne(new Document("test", "value"));

    Document result = database.getCollection("testcollection").find().first();
    assertNotNull(result);
    assertEquals("value", result.getString("test"));
  }

  @Test
  void testDatabaseHasCorrectName() {
    connection.connect();

    MongoDatabase database = connection.getConnection();

    assertEquals(credentials.database(), database.getName());
  }
}
