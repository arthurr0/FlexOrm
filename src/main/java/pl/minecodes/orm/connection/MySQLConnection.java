package pl.minecodes.plots.plugin.database.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.minecodes.plots.plugin.configuration.section.DatabaseConfiguration;
import pl.minecodes.plots.plugin.database.DatabaseObjectType;

public class MySQLDatabaseConnection implements DatabaseConnection<HikariDataSource> {

  private HikariDataSource hikariDataSource;

  private final DatabaseConfiguration databaseConfiguration;

  public MySQLDatabaseConnection(
      DatabaseConfiguration databaseConfiguration
  ) {
    this.databaseConfiguration = databaseConfiguration;
  }

  @Override
  public void connect(DatabaseObjectType databaseObjectType) {
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

    hikariConfig.setJdbcUrl("jdbc:mysql://" + this.databaseConfiguration.hostname + ":" + this.databaseConfiguration.port + "/" + this.databaseConfiguration.base);
    hikariConfig.setUsername(this.databaseConfiguration.username);
    hikariConfig.setPassword(this.databaseConfiguration.password);

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
