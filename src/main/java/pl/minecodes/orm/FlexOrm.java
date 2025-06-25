package pl.minecodes.orm;

import com.google.gson.Gson;
import java.io.File;

public class FlexOrmInitializer {

  private final DatabaseType databaseType;

  private final String hostname;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  private final File databaseDirectory;

  private final Gson gson;

  private FlexOrmInitializer(
      DatabaseType databaseType,
      String hostname,
      int port,
      String database,
      String username,
      String password,
      File databaseDirectory,
      Gson gson
  ) {
    this.databaseType = databaseType;
    this.hostname = hostname;
    this.port = port;
    this.database = database;
    this.username = username;
    this.password = password;
    this.databaseDirectory = databaseDirectory;
    this.gson = gson;
  }

  public static FlexOrmInitializer mysql(
      String hostname,
      int port,
      String database,
      String username,
      String password
  ) {
    return new FlexOrmInitializer(
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

  public static FlexOrmInitializer mongodb(
      String hostname,
      int port,
      String database,
      String username,
      String password
  ) {
    return new FlexOrmInitializer(
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

  public static FlexOrmInitializer json(
      String hostname,
      int port,
      String database,
      String username,
      String password,
      File databaseDirectory,
      Gson gson
  ) {
    return new FlexOrmInitializer(
        DatabaseType.MYSQL,
        hostname,
        port,
        database,
        username,
        password,
        databaseDirectory,
        gson
    );
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
}
