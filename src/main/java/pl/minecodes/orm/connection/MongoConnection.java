package pl.minecodes.orm.connection;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.List;

public class MongoDatabaseConnection implements DatabaseConnection<MongoDatabase> {

  private MongoDatabase mongoDatabase;

  private final ConnectionCredentials connectionCredentials;

  public MongoDatabaseConnection(ConnectionCredentials connectionCredentials) {
    this.connectionCredentials = connectionCredentials;
  }

  @Override
  public void connect() {
    if (this.mongoDatabase != null) {
      return;
    }

    MongoCredential credential = MongoCredential.createCredential(
        this.connectionCredentials.username(),
        this.connectionCredentials.database(),
        this.connectionCredentials.password().toCharArray()
    );

    MongoClientSettings settings = MongoClientSettings.builder()
        .credential(credential)
        .applyToClusterSettings(builder ->
            builder.hosts(List.of(new ServerAddress(this.connectionCredentials.hostname(), this.connectionCredentials.port()))))
        .build();


    MongoClient mongoClient = MongoClients.create(settings);

    this.mongoDatabase = mongoClient.getDatabase(this.connectionCredentials.database());
  }

  @Override
  public MongoDatabase getConnection() {
    if (this.mongoDatabase == null) {
      throw new IllegalStateException("Database is not connected!");
    }

    return this.mongoDatabase;
  }
}
