package pl.minecodes.orm.connection;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.List;

public class MongoConnection implements Connection<MongoDatabase> {

  private final ConnectionCredentials connectionCredentials;

  private MongoClient mongoClient;
  private MongoDatabase mongoDatabase;

  public MongoConnection(ConnectionCredentials connectionCredentials) {
    this.connectionCredentials = connectionCredentials;
  }

  @Override
  public void connect() {
    if (this.mongoDatabase != null) {
      return;
    }

    MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
        .applyToClusterSettings(builder ->
            builder.hosts(List.of(new ServerAddress(
                this.connectionCredentials.hostname(),
                this.connectionCredentials.port()
            )))
        );

    if (this.connectionCredentials.username() != null
        && this.connectionCredentials.password() != null) {
      MongoCredential credential = MongoCredential.createCredential(
          this.connectionCredentials.username(),
          this.connectionCredentials.database(),
          this.connectionCredentials.password().toCharArray()
      );
      settingsBuilder.credential(credential);
    }

    this.mongoClient = MongoClients.create(settingsBuilder.build());
    this.mongoDatabase = this.mongoClient.getDatabase(this.connectionCredentials.database());
  }

  @Override
  public MongoDatabase getConnection() {
    if (this.mongoDatabase == null) {
      throw new IllegalStateException("Database is not connected!");
    }

    return this.mongoDatabase;
  }

  @Override
  public void close() {
    if (this.mongoClient != null) {
      this.mongoClient.close();
      this.mongoClient = null;
      this.mongoDatabase = null;
    }
  }
}
