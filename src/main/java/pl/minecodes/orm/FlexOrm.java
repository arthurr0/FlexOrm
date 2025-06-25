package pl.minecodes.orm;

import com.google.gson.Gson;
import java.io.File;
import pl.minecodes.orm.connection.Connection;
import pl.minecodes.orm.connection.ConnectionCredentials;
import pl.minecodes.orm.connection.MongoConnection;
import pl.minecodes.orm.connection.MySQLConnection;
import pl.minecodes.orm.connection.SQLiteConnection;
import pl.minecodes.orm.entity.EntityAgent;
import pl.minecodes.orm.entity.EntityAgentFactory;

public class FlexOrm {

  private final DatabaseType databaseType;

  private final String hostname;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  private final Gson gson;
  private final File databaseDirectory;

  private Connection<?> connection;

  protected FlexOrm(
      DatabaseType databaseType,
      String hostname,
      int port,
      String database,
      String username,
      String password,
      Gson gson,
      File databaseDirectory
  ) {
    this.databaseType = databaseType;
    this.hostname = hostname;
    this.port = port;
    this.database = database;
    this.username = username;
    this.password = password;
    this.gson = gson;
    this.databaseDirectory = databaseDirectory;
  }

  public static FlexOrm mysql(
      String hostname,
      int port,
      String database,
      String username,
      String password
  ) {
    return new FlexOrm(
        DatabaseType.MYSQL,
        hostname,
        port,
        database,
        username,
        password,
        null,
        null
    );
  }

  public static FlexOrm mongodb(
      String hostname,
      int port,
      String database,
      String username,
      String password,
      Gson gson
  ) {
    return new FlexOrm(
        DatabaseType.MONGODB,
        hostname,
        port,
        database,
        username,
        password,
        gson,
        null
    );
  }

  public static FlexOrm json(
      String hostname,
      int port,
      String database,
      String username,
      String password,
      File databaseDirectory,
      Gson gson
  ) {
    return new FlexOrm(
        DatabaseType.JSON,
        hostname,
        port,
        database,
        username,
        password,
        gson,
        databaseDirectory
    );
  }

  public static FlexOrm sqllite(File databaseFile) {
    return new FlexOrm(
        DatabaseType.SQLLITE,
        null,
        0,
        databaseFile.getName(),
        null,
        null,
        null,
        databaseFile.getParentFile()
    );
  }

  public static FlexOrm sqllite(String databasePath) {
    File databaseFile = new File(databasePath);
    return sqllite(databaseFile);
  }

  public FlexOrm connect() {
    ConnectionCredentials connectionCredentials = new ConnectionCredentials(
        this.hostname,
        this.port,
        this.database,
        this.username,
        this.password,
        this.databaseDirectory
    );

    this.connection = switch (this.databaseType) {
      case MYSQL -> new MySQLConnection(connectionCredentials);
      case MONGODB -> new MongoConnection(connectionCredentials);
      case SQLLITE -> new SQLiteConnection(connectionCredentials);
      case JSON -> throw new IllegalStateException("JSON connection is not supported");
    };

    this.connection.connect();

    return this;
  }

  public <T, ID> EntityAgent<T, ID> getEntityAgent(Class<T> entityClass) {
    if (this.connection == null) {
      connect();
    }

    return EntityAgentFactory.createEntityAgent(this, entityClass);
  }

  public DatabaseType getDatabaseType() {
    return databaseType;
  }

  public String getHostname() {
    return hostname;
  }

  public int getPort() {
    return port;
  }

  public String getDatabase() {
    return database;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public File getDatabaseDirectory() {
    return databaseDirectory;
  }

  public Gson getGson() {
    return gson;
  }

  public Connection<?> getConnection() {
    return connection;
  }
}