package pl.minecodes.orm.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class MySQLConnection implements Connection<HikariDataSource> {

  private HikariDataSource hikariDataSource;

  private final ConnectionCredentials connectionCredentials;

  public MySQLConnection(ConnectionCredentials connectionCredentials) {
    this.connectionCredentials = connectionCredentials;
  }

  @Override
  public void connect() {
    if (this.hikariDataSource != null) {
      return;
    }

    this.hikariDataSource = new HikariDataSource(this.getHikariConfig());
  }

  @Override
  public HikariDataSource getConnection() {
    if (hikariDataSource == null) {
      throw new IllegalStateException("Database is not connected!");
    }

    return this.hikariDataSource;
  }

  private HikariConfig getHikariConfig() {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

    hikariConfig.setJdbcUrl("jdbc:mysql://" + this.connectionCredentials.hostname() + ":"
        + this.connectionCredentials.port() + "/" + this.connectionCredentials.database());
    hikariConfig.setUsername(this.connectionCredentials.username());
    hikariConfig.setPassword(this.connectionCredentials.password());

    hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
    hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
    hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
    hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
    hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
    hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
    hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
    hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
    return hikariConfig;
  }
}
