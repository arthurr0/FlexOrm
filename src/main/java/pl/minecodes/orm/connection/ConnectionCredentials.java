package pl.minecodes.orm;

public class ConnectionCredentials {

  private final String hostname;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  public ConnectionCredentials(
      String hostname,
      int port,
      String database,
      String username,
      String password
  ) {
    this.hostname = hostname;
    this.port = port;
    this.database = database;
    this.username = username;
    this.password = password;
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
}
