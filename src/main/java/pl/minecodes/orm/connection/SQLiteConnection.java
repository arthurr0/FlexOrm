package pl.minecodes.orm.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class SQLiteConnection implements Connection<HikariDataSource> {

  private HikariDataSource hikariDataSource;
  private final ConnectionCredentials connectionCredentials;

  public SQLiteConnection(ConnectionCredentials connectionCredentials) {
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
    hikariConfig.setDriverClassName("org.sqlite.JDBC");

    String jdbcUrl;
    if (this.connectionCredentials.databaseFile() != null
        && this.connectionCredentials.database() != null) {
      java.io.File dbFile = this.connectionCredentials.databaseFile();
      if (dbFile.isDirectory() || !dbFile.getName().endsWith(".db")) {
        java.io.File fullPath = new java.io.File(dbFile, this.connectionCredentials.database());
        jdbcUrl = "jdbc:sqlite:" + fullPath.getAbsolutePath();
      } else {
        jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
      }
    } else if (this.connectionCredentials.databaseFile() != null) {
      jdbcUrl = "jdbc:sqlite:" + this.connectionCredentials.databaseFile().getAbsolutePath();
    } else {
      jdbcUrl = "jdbc:sqlite:" + this.connectionCredentials.database();
    }
    hikariConfig.setJdbcUrl(jdbcUrl);

    if (this.connectionCredentials.username() != null) {
      hikariConfig.setUsername(this.connectionCredentials.username());
    }

    if (this.connectionCredentials.password() != null) {
      hikariConfig.setPassword(this.connectionCredentials.password());
    }

    hikariConfig.addDataSourceProperty("foreign_keys", "true");
    hikariConfig.addDataSourceProperty("synchronous", "normal");
    hikariConfig.addDataSourceProperty("journal_mode", "WAL");
    hikariConfig.addDataSourceProperty("cache_size", "1000");

    hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
    hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    return hikariConfig;
  }
}