package pl.minecodes.orm.table;

import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.minecodes.orm.DatabaseType;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmIndex;
import pl.minecodes.orm.exception.ObjectRequiredAnnotationsException;

public class TableManager {

  private final FlexOrm orm;

  public TableManager(FlexOrm orm) {
    this.orm = orm;
  }

  public <T> void createTable(Class<T> entityClass) {
    if (!entityClass.isAnnotationPresent(OrmEntity.class)) {
      throw new ObjectRequiredAnnotationsException(
          "Class " + entityClass.getName() + " is not annotated with @OrmEntity");
    }

    OrmEntity ormEntity = entityClass.getAnnotation(OrmEntity.class);
    String tableName =
        ormEntity.table().isEmpty() ? entityClass.getSimpleName().toLowerCase() : ormEntity.table();

    switch (orm.getDatabaseType()) {
      case MYSQL -> {
        createMySQLTable(entityClass, tableName);
        createIndexes(entityClass, tableName);
      }
      case SQLLITE -> {
        createSQLiteTable(entityClass, tableName);
        createIndexes(entityClass, tableName);
      }
      case MONGODB -> createMongoCollection(tableName);
    }
  }

  public <T> void updateTable(Class<T> entityClass) {
    if (!entityClass.isAnnotationPresent(OrmEntity.class)) {
      throw new ObjectRequiredAnnotationsException(
          "Class " + entityClass.getName() + " is not annotated with @OrmEntity");
    }

    OrmEntity ormEntity = entityClass.getAnnotation(OrmEntity.class);
    String tableName =
        ormEntity.table().isEmpty() ? entityClass.getSimpleName().toLowerCase() : ormEntity.table();

    switch (orm.getDatabaseType()) {
      case MYSQL -> updateMySQLTable(entityClass, tableName);
      case SQLLITE -> updateSQLiteTable(entityClass, tableName);
      case MONGODB -> {
      }
    }
  }

  public <T> void createOrUpdateTable(Class<T> entityClass) {
    if (!entityClass.isAnnotationPresent(OrmEntity.class)) {
      throw new ObjectRequiredAnnotationsException(
          "Class " + entityClass.getName() + " is not annotated with @OrmEntity");
    }

    OrmEntity ormEntity = entityClass.getAnnotation(OrmEntity.class);
    String tableName =
        ormEntity.table().isEmpty() ? entityClass.getSimpleName().toLowerCase() : ormEntity.table();

    boolean tableExists = tableExists(tableName);

    if (tableExists) {
      updateTable(entityClass);
    } else {
      createTable(entityClass);
    }
  }

  public boolean tableExists(String tableName) {
    switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> {
        HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

        try (Connection connection = dataSource.getConnection()) {
          DatabaseMetaData metaData = connection.getMetaData();
          try (ResultSet resultSet = metaData.getTables(null, null, tableName,
              new String[]{"TABLE"})) {
            return resultSet.next();
          }
        } catch (SQLException e) {
          throw new RuntimeException("Error checking if table exists: " + e.getMessage(), e);
        }
      }
      case MONGODB -> {
        MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
        for (String collectionName : database.listCollectionNames()) {
          if (collectionName.equals(tableName)) {
            return true;
          }
        }
        return false;
      }
    }
    return false;
  }

  private <T> void createMySQLTable(Class<T> entityClass, String tableName) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      StringBuilder sql = new StringBuilder();
      sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

      List<String> columns = new ArrayList<>();
      Field idField = null;

      for (Field field : entityClass.getDeclaredFields()) {
        if (shouldSkipField(field)) {
          continue;
        }

        String columnDefinition = createColumnDefinition(field, DatabaseType.MYSQL);
        if (columnDefinition != null) {
          columns.add(columnDefinition);

          if (field.isAnnotationPresent(OrmEntityId.class)) {
            idField = field;
          }
        }
      }

      if (idField == null) {
        throw new ObjectRequiredAnnotationsException("Class " + entityClass.getName()
            + " does not have a field annotated with @OrmEntityId");
      }

      sql.append(String.join(",\n", columns));
      sql.append("\n)");

      try (Statement statement = connection.createStatement()) {
        statement.execute(sql.toString());
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error creating MySQL table: " + e.getMessage(), e);
    }
  }

  private <T> void updateMySQLTable(Class<T> entityClass, String tableName) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      Map<String, String> existingColumns = getExistingColumns(connection, tableName);
      Map<String, ColumnInfo> entityColumns = getEntityColumns(entityClass, DatabaseType.MYSQL);

      for (Map.Entry<String, ColumnInfo> entry : entityColumns.entrySet()) {
        String columnName = entry.getKey();
        ColumnInfo columnInfo = entry.getValue();

        if (!existingColumns.containsKey(columnName)) {
          String alterSql =
              "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnInfo.sqlType;

          try (Statement statement = connection.createStatement()) {
            statement.execute(alterSql);
          }
        } else {
          String existingType = existingColumns.get(columnName);
          if (!isCompatibleType(existingType, columnInfo.sqlType, DatabaseType.MYSQL)) {
            String alterSql = "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " "
                + columnInfo.sqlType;

            try (Statement statement = connection.createStatement()) {
              statement.execute(alterSql);
            }
          }
        }
      }

      if (shouldDropUnusedColumns()) {
        for (String columnName : existingColumns.keySet()) {
          if (!entityColumns.containsKey(columnName)) {
            String alterSql = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;

            try (Statement statement = connection.createStatement()) {
              statement.execute(alterSql);
            }
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error updating MySQL table: " + e.getMessage(), e);
    }
  }

  private <T> void createSQLiteTable(Class<T> entityClass, String tableName) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      StringBuilder sql = new StringBuilder();
      sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

      List<String> columns = new ArrayList<>();
      Field idField = null;

      for (Field field : entityClass.getDeclaredFields()) {
        if (shouldSkipField(field)) {
          continue;
        }

        String columnDefinition = createColumnDefinition(field, DatabaseType.SQLLITE);
        if (columnDefinition != null) {
          columns.add(columnDefinition);

          if (field.isAnnotationPresent(OrmEntityId.class)) {
            idField = field;
          }
        }
      }

      if (idField == null) {
        throw new ObjectRequiredAnnotationsException("Class " + entityClass.getName()
            + " does not have a field annotated with @OrmEntityId");
      }

      sql.append(String.join(",\n", columns));
      sql.append("\n)");

      try (Statement statement = connection.createStatement()) {
        statement.execute(sql.toString());
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error creating SQLite table: " + e.getMessage(), e);
    }
  }

  private <T> void updateSQLiteTable(Class<T> entityClass, String tableName) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      Map<String, String> existingColumns = getExistingColumns(connection, tableName);
      Map<String, ColumnInfo> entityColumns = getEntityColumns(entityClass, DatabaseType.SQLLITE);

      boolean hasChanges = false;

      for (String columnName : entityColumns.keySet()) {
        if (!existingColumns.containsKey(columnName)) {
          hasChanges = true;
          break;
        }
      }

      if (shouldDropUnusedColumns()) {
        for (String columnName : existingColumns.keySet()) {
          if (!entityColumns.containsKey(columnName)) {
            hasChanges = true;
            break;
          }
        }
      }

      if (!hasChanges) {
        for (Map.Entry<String, ColumnInfo> entry : entityColumns.entrySet()) {
          String columnName = entry.getKey();
          ColumnInfo columnInfo = entry.getValue();

          if (existingColumns.containsKey(columnName)) {
            String existingType = existingColumns.get(columnName);
            if (!isCompatibleType(existingType, columnInfo.sqlType, DatabaseType.SQLLITE)) {
              hasChanges = true;
              break;
            }
          }
        }
      }

      if (hasChanges) {
        connection.setAutoCommit(false);

        try {
          String tempTableName = tableName + "_new";

          StringBuilder createSql = new StringBuilder();
          createSql.append("CREATE TABLE ").append(tempTableName).append(" (\n");

          List<String> columns = new ArrayList<>();
          for (Map.Entry<String, ColumnInfo> entry : entityColumns.entrySet()) {
            columns.add(entry.getKey() + " " + entry.getValue().sqlType);
          }

          createSql.append(String.join(",\n", columns));
          createSql.append("\n)");

          try (Statement statement = connection.createStatement()) {
            statement.execute(createSql.toString());
          }

          Set<String> commonColumns = new HashSet<>(existingColumns.keySet());
          commonColumns.retainAll(entityColumns.keySet());

          if (!commonColumns.isEmpty()) {
            StringBuilder insertSql = new StringBuilder();
            insertSql.append("INSERT INTO ").append(tempTableName).append(" (");
            insertSql.append(String.join(", ", commonColumns));
            insertSql.append(") SELECT ");
            insertSql.append(String.join(", ", commonColumns));
            insertSql.append(" FROM ").append(tableName);

            try (Statement statement = connection.createStatement()) {
              statement.execute(insertSql.toString());
            }
          }

          try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + tableName);
          }

          try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tempTableName + " RENAME TO " + tableName);
          }

          connection.commit();
        } catch (SQLException e) {
          connection.rollback();
          throw e;
        } finally {
          connection.setAutoCommit(true);
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error updating SQLite table: " + e.getMessage(), e);
    }
  }

  private void createMongoCollection(String collectionName) {
    MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
    database.createCollection(collectionName);
  }

  private <T> void createIndexes(Class<T> entityClass, String tableName) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      for (Field field : entityClass.getDeclaredFields()) {
        if (!field.isAnnotationPresent(OrmIndex.class)) {
          continue;
        }

        OrmIndex indexAnnotation = field.getAnnotation(OrmIndex.class);
        String columnName = getColumnName(field);

        String indexName = indexAnnotation.name().isEmpty()
            ? "idx_" + tableName + "_" + columnName
            : indexAnnotation.name();

        String indexType = indexAnnotation.unique() ? "UNIQUE INDEX" : "INDEX";

        String sql;
        if (orm.getDatabaseType() == DatabaseType.MYSQL) {
          sql = "CREATE " + indexType + " IF NOT EXISTS " + indexName + " ON " + tableName + " ("
              + columnName + ")";
        } else {
          sql = "CREATE " + indexType + " IF NOT EXISTS " + indexName + " ON " + tableName + " ("
              + columnName + ")";
        }

        try (Statement statement = connection.createStatement()) {
          statement.execute(sql);
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error creating indexes: " + e.getMessage(), e);
    }
  }

  private String createColumnDefinition(Field field, DatabaseType databaseType) {
    if (shouldSkipField(field)) {
      return null;
    }

    String columnName = getColumnName(field);
    String sqlType = mapJavaTypeToSQLType(field.getType(), databaseType);

    StringBuilder definition = new StringBuilder();
    definition.append(columnName).append(" ").append(sqlType);

    boolean nullable = true;
    int length = 255;
    String defaultValue = "";

    if (field.isAnnotationPresent(OrmField.class)) {
      OrmField ormField = field.getAnnotation(OrmField.class);
      nullable = ormField.nullable();
      length = ormField.length();
      defaultValue = ormField.defaultValue();

      if (sqlType.startsWith("VARCHAR") && databaseType == DatabaseType.MYSQL) {
        sqlType = "VARCHAR(" + length + ")";
        definition = new StringBuilder(columnName + " " + sqlType);
      }
    }

    if (field.isAnnotationPresent(OrmEntityId.class)) {
      if (databaseType == DatabaseType.MYSQL) {
        if (field.getType() == int.class || field.getType() == Integer.class
            || field.getType() == long.class || field.getType() == Long.class) {
          definition.append(" PRIMARY KEY AUTO_INCREMENT");
        } else {
          definition.append(" PRIMARY KEY");
        }
      } else if (databaseType == DatabaseType.SQLLITE) {
        if (field.getType() == int.class || field.getType() == Integer.class
            || field.getType() == long.class || field.getType() == Long.class) {
          definition.append(" PRIMARY KEY AUTOINCREMENT");
        } else {
          definition.append(" PRIMARY KEY");
        }
      }
      nullable = false;
    }

    if (!nullable) {
      definition.append(" NOT NULL");
    } else {
      definition.append(" NULL");
    }

    if (!defaultValue.isEmpty() && !field.isAnnotationPresent(OrmEntityId.class)) {
      if (field.getType() == String.class) {
        definition.append(" DEFAULT '").append(defaultValue).append("'");
      } else {
        definition.append(" DEFAULT ").append(defaultValue);
      }
    }

    return definition.toString();
  }

  private String getColumnName(Field field) {
    if (field.isAnnotationPresent(OrmField.class)) {
      OrmField ormField = field.getAnnotation(OrmField.class);
      if (!ormField.name().isEmpty()) {
        return ormField.name();
      }
    }
    return field.getName();
  }

  private boolean shouldSkipField(Field field) {
    return false;
  }

  private Map<String, String> getExistingColumns(Connection connection, String tableName)
      throws SQLException {
    Map<String, String> columns = new HashMap<>();

    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet resultSet = metaData.getColumns(null, null, tableName, null)) {
      while (resultSet.next()) {
        String columnName = resultSet.getString("COLUMN_NAME");
        String typeName = resultSet.getString("TYPE_NAME");
        columns.put(columnName, typeName);
      }
    }

    return columns;
  }

  private <T> Map<String, ColumnInfo> getEntityColumns(Class<T> entityClass,
      DatabaseType databaseType) {
    Map<String, ColumnInfo> columns = new HashMap<>();

    for (Field field : entityClass.getDeclaredFields()) {
      if (shouldSkipField(field)) {
        continue;
      }

      String columnName = getColumnName(field);
      String sqlType = mapJavaTypeToSQLType(field.getType(), databaseType);
      boolean isPrimaryKey = field.isAnnotationPresent(OrmEntityId.class);

      int length = 255;
      if (field.isAnnotationPresent(OrmField.class)) {
        OrmField ormField = field.getAnnotation(OrmField.class);
        length = ormField.length();

        if (sqlType.equals("VARCHAR") && databaseType == DatabaseType.MYSQL) {
          sqlType = "VARCHAR(" + length + ")";
        }
      }

      StringBuilder fullType = new StringBuilder(sqlType);

      if (isPrimaryKey) {
        if (databaseType == DatabaseType.MYSQL) {
          if (field.getType() == int.class || field.getType() == Integer.class
              || field.getType() == long.class || field.getType() == Long.class) {
            fullType.append(" PRIMARY KEY AUTO_INCREMENT");
          } else {
            fullType.append(" PRIMARY KEY");
          }
        } else if (databaseType == DatabaseType.SQLLITE) {
          if (field.getType() == int.class || field.getType() == Integer.class
              || field.getType() == long.class || field.getType() == Long.class) {
            fullType.append(" PRIMARY KEY AUTOINCREMENT");
          } else {
            fullType.append(" PRIMARY KEY");
          }
        }
      }

      boolean nullable = true;
      if (field.isAnnotationPresent(OrmField.class)) {
        OrmField ormField = field.getAnnotation(OrmField.class);
        nullable = ormField.nullable();
      }

      if (!nullable || isPrimaryKey) {
        fullType.append(" NOT NULL");
      }

      columns.put(columnName, new ColumnInfo(sqlType, fullType.toString(), field));
    }

    return columns;
  }

  private record ColumnInfo(String baseType, String sqlType, Field field) {

  }

  private boolean isCompatibleType(String existingType, String newType, DatabaseType databaseType) {
    String baseExistingType = existingType.split("\\s+")[0].toUpperCase();
    String baseNewType = newType.split("\\s+")[0].toUpperCase();

    baseExistingType = baseExistingType.replaceAll("\\(.*\\)", "");
    baseNewType = baseNewType.replaceAll("\\(.*\\)", "");

    if (databaseType == DatabaseType.SQLLITE) {
      if ((baseExistingType.equals("INTEGER") || baseExistingType.equals("INT")) && (
          baseNewType.equals("INTEGER") || baseNewType.equals("INT"))) {
        return true;
      }

      if ((baseExistingType.equals("REAL") || baseExistingType.equals("FLOAT")
          || baseExistingType.equals("DOUBLE")) && (baseNewType.equals("REAL")
          || baseNewType.equals("FLOAT") || baseNewType.equals("DOUBLE"))) {
        return true;
      }

      if ((baseExistingType.equals("TEXT") || baseExistingType.equals("VARCHAR")) &&
          (baseNewType.equals("TEXT") || baseNewType.equals("VARCHAR"))) {
        return true;
      }
    }

    if (databaseType == DatabaseType.MYSQL) {
      if ((baseExistingType.equals("INT") || baseExistingType.equals("BIGINT")) && (
          baseNewType.equals("INT") || baseNewType.equals("BIGINT"))) {
        return true;
      }

      if ((baseExistingType.equals("FLOAT") || baseExistingType.equals("DOUBLE")) && (
          baseNewType.equals("FLOAT") || baseNewType.equals("DOUBLE"))) {
        return true;
      }

      if ((baseExistingType.equals("VARCHAR") || baseExistingType.equals("TEXT")) && (
          baseNewType.equals("VARCHAR") || baseNewType.equals("TEXT"))) {
        return true;
      }
    }

    return baseExistingType.equals(baseNewType);
  }

  private String mapJavaTypeToSQLType(Class<?> javaType, DatabaseType databaseType) {
    if (databaseType == DatabaseType.MYSQL) {
      if (javaType == String.class) {
        return "VARCHAR(255)";
      } else if (javaType == int.class || javaType == Integer.class) {
        return "INT";
      } else if (javaType == long.class || javaType == Long.class) {
        return "BIGINT";
      } else if (javaType == boolean.class || javaType == Boolean.class) {
        return "BOOLEAN";
      } else if (javaType == double.class || javaType == Double.class) {
        return "DOUBLE";
      } else if (javaType == float.class || javaType == Float.class) {
        return "FLOAT";
      } else if (javaType == java.util.Date.class || javaType == java.sql.Date.class) {
        return "DATETIME";
      } else if (javaType == java.sql.Timestamp.class) {
        return "TIMESTAMP";
      } else if (javaType == java.time.LocalDateTime.class) {
        return "DATETIME";
      } else if (javaType == java.time.LocalDate.class) {
        return "DATE";
      } else if (javaType == java.time.LocalTime.class) {
        return "TIME";
      } else if (javaType == java.math.BigDecimal.class) {
        return "DECIMAL(19,4)";
      } else if (javaType == java.math.BigInteger.class) {
        return "BIGINT";
      } else if (javaType == byte[].class) {
        return "BLOB";
      } else if (javaType.isEnum()) {
        return "VARCHAR(50)";
      } else {
        return "VARCHAR(255)";
      }
    } else if (databaseType == DatabaseType.SQLLITE) {
      if (javaType == String.class) {
        return "TEXT";
      } else if (javaType == int.class || javaType == Integer.class || javaType == long.class
          || javaType == Long.class) {
        return "INTEGER";
      } else if (javaType == boolean.class || javaType == Boolean.class) {
        return "INTEGER";
      } else if (javaType == double.class || javaType == Double.class || javaType == float.class
          || javaType == Float.class) {
        return "REAL";
      } else if (javaType == java.util.Date.class || javaType == java.sql.Date.class) {
        return "TEXT";
      } else if (javaType == java.sql.Timestamp.class) {
        return "TEXT";
      } else if (javaType == java.time.LocalDateTime.class) {
        return "TEXT";
      } else if (javaType == java.time.LocalDate.class) {
        return "TEXT";
      } else if (javaType == java.time.LocalTime.class) {
        return "TEXT";
      } else if (javaType == java.math.BigDecimal.class) {
        return "TEXT";
      } else if (javaType == java.math.BigInteger.class) {
        return "INTEGER";
      } else if (javaType == byte[].class) {
        return "BLOB";
      } else if (javaType.isEnum()) {
        return "TEXT";
      } else {
        return "TEXT";
      }
    }

    return "TEXT";
  }

  private boolean shouldDropUnusedColumns() {
    return false;
  }
}