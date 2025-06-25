package pl.minecodes.orm.query;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariDataSource;
import org.bson.Document;
import org.bson.conversions.Bson;

import pl.minecodes.orm.DatabaseType;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.entity.BaseEntityAgent;
import pl.minecodes.orm.table.TableMetadata;

public class Query<T> {

  private final FlexOrm orm;
  private final Class<T> entityClass;
  private final TableMetadata metadata;
  private final Map<String, Object> parameters = new HashMap<>();
  private final List<String> orderBy = new ArrayList<>();
  private final List<String> groupBy = new ArrayList<>();
  private final List<QueryCondition> conditions = new ArrayList<>();
  private Integer limit;
  private Integer offset;
  private String customSql;
  private Document mongoQuery;
  private boolean useDistinct = false;

  public Query(FlexOrm orm, Class<T> entityClass, TableMetadata metadata) {
    this.orm = orm;
    this.entityClass = entityClass;
    this.metadata = metadata;
  }

  public Query<T> where(String field, Object value) {
    return where(field, Operator.EQUALS, value);
  }

  public Query<T> where(String field, Operator operator, Object value) {
    conditions.add(new QueryCondition(field, operator, value));
    return this;
  }

  public Query<T> and(String field, Operator operator, Object value) {
    if (conditions.isEmpty()) {
      return where(field, operator, value);
    }
    conditions.add(new QueryCondition(LogicalOperator.AND, field, operator, value));
    return this;
  }

  public Query<T> or(String field, Operator operator, Object value) {
    if (conditions.isEmpty()) {
      return where(field, operator, value);
    }
    conditions.add(new QueryCondition(LogicalOperator.OR, field, operator, value));
    return this;
  }

  public Query<T> orderBy(String field) {
    return orderBy(field, true);
  }

  public Query<T> orderBy(String field, boolean ascending) {
    // Sprawdź, czy pole istnieje w metadanych
    String columnName = getActualColumnName(field);
    orderBy.add(columnName + (ascending ? " ASC" : " DESC"));
    return this;
  }

  public Query<T> groupBy(String... fields) {
    for (String field : fields) {
      String columnName = getActualColumnName(field);
      groupBy.add(columnName);
    }
    return this;
  }

  public Query<T> limit(int limit) {
    this.limit = limit;
    return this;
  }

  public Query<T> offset(int offset) {
    this.offset = offset;
    return this;
  }

  public Query<T> raw(String sql) {
    this.customSql = sql;
    return this;
  }

  public Query<T> mongoQuery(Document query) {
    this.mongoQuery = query;
    return this;
  }

  public Query<T> withParameter(String name, Object value) {
    parameters.put(name, value);
    return this;
  }

  public Query<T> distinct() {
    this.useDistinct = true;
    return this;
  }

  public List<T> execute() {
    switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> {
        return executeRelationalQuery();
      }
      case MONGODB -> {
        return executeMongoQuery();
      }
      case JSON -> {
        return executeJsonQuery();
      }
      default -> throw new UnsupportedOperationException("Database type not supported");
    }
  }

  private List<T> executeRelationalQuery() {
    try {
      HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();
      try (Connection connection = dataSource.getConnection()) {
        String sql;
        if (customSql != null) {
          sql = customSql;
        } else {
          sql = buildSqlQuery();
        }

        System.out.println("Executing SQL: " + sql); // DEBUG

        try (PreparedStatement statement = prepareStatement(connection, sql)) {
          try (ResultSet resultSet = statement.executeQuery()) {
            return mapResultSetToEntities(resultSet);
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error executing query: " + e.getMessage(), e);
    }
  }

  private List<T> executeMongoQuery() {
    MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
    MongoCollection<Document> collection = database.getCollection(metadata.tableName());

    Bson filter;
    if (mongoQuery != null) {
      filter = mongoQuery;
    } else {
      filter = buildMongoQuery();
    }

    System.out.println("Executing MongoDB query: " + filter.toString()); // DEBUG

    FindIterable<Document> findIterable = collection.find(filter);

    if (useDistinct && !orderBy.isEmpty()) {
      Document sort = new Document();
      for (String order : orderBy) {
        String[] parts = order.split(" ");
        sort.append(parts[0], "ASC".equals(parts[1]) ? 1 : -1);
      }
      findIterable.sort(sort);
    }

    if (limit != null) {
      findIterable.limit(limit);
    }

    if (offset != null) {
      findIterable.skip(offset);
    }

    List<T> results = new ArrayList<>();
    for (Document document : findIterable) {
      T entity = mapDocumentToEntity(document);
      results.add(entity);
    }

    return results;
  }

  private List<T> executeJsonQuery() {
    // Implementacja JSON podobna do poprzedniej
    throw new UnsupportedOperationException("Custom queries for JSON storage are not implemented yet");
  }

  private String buildSqlQuery() {
    StringBuilder sql = new StringBuilder();

    sql.append("SELECT ");
    if (useDistinct) {
      sql.append("DISTINCT ");
    }
    sql.append("* FROM ").append(metadata.tableName());

    if (!conditions.isEmpty()) {
      sql.append(" WHERE ");
      buildWhereClause(sql);
    }

    if (!groupBy.isEmpty()) {
      sql.append(" GROUP BY ").append(String.join(", ", groupBy));
    }

    if (!orderBy.isEmpty()) {
      sql.append(" ORDER BY ").append(String.join(", ", orderBy));
    }

    if (limit != null) {
      sql.append(" LIMIT ").append(limit);
    }

    if (offset != null) {
      sql.append(" OFFSET ").append(offset);
    }

    return sql.toString();
  }

  private void buildWhereClause(StringBuilder sql) {
    for (int i = 0; i < conditions.size(); i++) {
      QueryCondition condition = conditions.get(i);

      if (i > 0) {
        sql.append(condition.getLogicalOperator() == LogicalOperator.AND ? " AND " : " OR ");
      }

      // Sprawdź czy pole istnieje w metadanych
      String columnName = getActualColumnName(condition.getField());
      sql.append(columnName);

      switch (condition.getOperator()) {
        case EQUALS -> {
          if (condition.getValue() == null) {
            sql.append(" IS NULL");
          } else if (condition.getValue() instanceof Boolean) {
            // Specjalna obsługa dla boolean w SQLite
            if (orm.getDatabaseType() == DatabaseType.SQLLITE) {
              boolean boolValue = (Boolean) condition.getValue();
              sql.append(boolValue ? " = 1" : " = 0");
            } else {
              sql.append(" = ?");
            }
          } else {
            sql.append(" = ?");
          }
        }
        case NOT_EQUALS -> {
          if (condition.getValue() == null) {
            sql.append(" IS NOT NULL");
          } else if (condition.getValue() instanceof Boolean) {
            // Specjalna obsługa dla boolean w SQLite
            if (orm.getDatabaseType() == DatabaseType.SQLLITE) {
              boolean boolValue = (Boolean) condition.getValue();
              sql.append(boolValue ? " != 1" : " != 0");
            } else {
              sql.append(" != ?");
            }
          } else {
            sql.append(" != ?");
          }
        }
        case GREATER_THAN -> sql.append(" > ?");
        case LESS_THAN -> sql.append(" < ?");
        case GREATER_THAN_OR_EQUALS -> sql.append(" >= ?");
        case LESS_THAN_OR_EQUALS -> sql.append(" <= ?");
        case LIKE -> sql.append(" LIKE ?");
        case IN -> {
          List<?> values = (List<?>) condition.getValue();
          if (values.isEmpty()) {
            sql.append(" IN (NULL)"); // Puste IN zwraca zawsze false
          } else {
            String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(", "));
            sql.append(" IN (").append(placeholders).append(")");
          }
        }
        case IS_NULL -> sql.append(" IS NULL");
        case IS_NOT_NULL -> sql.append(" IS NOT NULL");
      }
    }
  }

  private Document buildMongoQuery() {
    Document query = new Document();

    for (QueryCondition condition : conditions) {
      String columnName = getActualColumnName(condition.getField());
      Document conditionDoc = new Document();

      switch (condition.getOperator()) {
        case EQUALS -> query.append(columnName, condition.getValue());
        case NOT_EQUALS -> conditionDoc.append("$ne", condition.getValue());
        case GREATER_THAN -> conditionDoc.append("$gt", condition.getValue());
        case LESS_THAN -> conditionDoc.append("$lt", condition.getValue());
        case GREATER_THAN_OR_EQUALS -> conditionDoc.append("$gte", condition.getValue());
        case LESS_THAN_OR_EQUALS -> conditionDoc.append("$lte", condition.getValue());
        case LIKE -> {
          String pattern = condition.getValue().toString();
          if (!pattern.startsWith("%")) pattern = "^" + pattern;
          else pattern = pattern.substring(1);
          if (!pattern.endsWith("%")) pattern = pattern + "$";
          else pattern = pattern.substring(0, pattern.length() - 1);
          pattern = pattern.replace("%", ".*");
          conditionDoc.append("$regex", pattern).append("$options", "i");
        }
        case IN -> conditionDoc.append("$in", condition.getValue());
        case IS_NULL -> conditionDoc.append("$exists", false);
        case IS_NOT_NULL -> conditionDoc.append("$exists", true);
      }

      if (!conditionDoc.isEmpty()) {
        query.append(columnName, conditionDoc);
      }
    }

    return query;
  }

  private PreparedStatement prepareStatement(Connection connection, String sql) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);

    if (customSql != null) {
      int paramIndex = 1;
      for (Map.Entry<String, Object> entry : parameters.entrySet()) {
        String name = entry.getKey();
        Object value = entry.getValue();

        // Prosta implementacja podstawiania parametrów nazwanych w SQL
        if (sql.contains(":" + name)) {
          String updatedSql = sql.replace(":" + name, "?");
          sql = updatedSql;
          statement = connection.prepareStatement(updatedSql);
          statement.setObject(paramIndex++, value);
        }
      }
      return statement;
    }

    int paramIndex = 1;
    for (QueryCondition condition : conditions) {
      // Pomiń warunki, które nie wymagają parametrów
      if (condition.getOperator() == Operator.IS_NULL ||
          condition.getOperator() == Operator.IS_NOT_NULL) {
        continue;
      }

      // Pomiń wartości null i boolean w SQLite, które są już obsłużone w SQL
      if ((condition.getOperator() == Operator.EQUALS || condition.getOperator() == Operator.NOT_EQUALS) &&
          (condition.getValue() == null ||
              (condition.getValue() instanceof Boolean && orm.getDatabaseType() == DatabaseType.SQLLITE))) {
        continue;
      }

      if (condition.getOperator() == Operator.IN) {
        List<?> values = (List<?>) condition.getValue();
        if (!values.isEmpty()) {
          for (Object value : values) {
            setStatementParameter(statement, paramIndex++, value);
          }
        }
      } else if (condition.getOperator() == Operator.LIKE) {
        // Specjalna obsługa dla LIKE - dodajemy znaki % jeśli ich nie ma
        String value = condition.getValue().toString();
        if (!value.contains("%")) {
          value = "%" + value + "%";
        }
        statement.setString(paramIndex++, value);
      } else {
        setStatementParameter(statement, paramIndex++, condition.getValue());
      }
    }

    return statement;
  }

  private void setStatementParameter(PreparedStatement statement, int index, Object value) throws SQLException {
    if (value instanceof String) {
      statement.setString(index, (String) value);
    } else if (value instanceof Integer) {
      statement.setInt(index, (Integer) value);
    } else if (value instanceof Long) {
      statement.setLong(index, (Long) value);
    } else if (value instanceof Double) {
      statement.setDouble(index, (Double) value);
    } else if (value instanceof Boolean) {
      statement.setBoolean(index, (Boolean) value);
    } else if (value == null) {
      statement.setNull(index, java.sql.Types.NULL);
    } else {
      statement.setObject(index, value);
    }
  }

  private List<T> mapResultSetToEntities(ResultSet resultSet) throws SQLException {
    List<T> results = new ArrayList<>();
    Map<Object, T> uniqueResults = new HashMap<>(); // Dla unikania duplikatów

    while (resultSet.next()) {
      try {
        T instance = entityClass.getDeclaredConstructor().newInstance();
        Object idValue = null;

        for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
          String columnName = entry.getKey();
          Field field = entry.getValue();

          try {
            Object value = resultSet.getObject(columnName);

            // Konwersja typów dla SQLite, które ma ograniczone typy
            if (value != null) {
              if (field.getType() == Boolean.class || field.getType() == boolean.class) {
                if (value instanceof Integer) {
                  value = ((Integer) value) == 1;
                } else if (value instanceof Long) {
                  value = ((Long) value) == 1L;
                } else if (value instanceof String) {
                  value = "true".equalsIgnoreCase((String) value) || "1".equals(value);
                }
              }

              field.set(instance, value);

              // Zapisz wartość ID do sprawdzania duplikatów
              if (field.equals(metadata.idField())) {
                idValue = value;
              }
            }
          } catch (SQLException e) {
            System.err.println("Warning: Column '" + columnName + "' not found in result set");
          }
        }

        // Dodaj tylko unikalne wyniki na podstawie ID
        if (idValue != null) {
          if (!uniqueResults.containsKey(idValue)) {
            uniqueResults.put(idValue, instance);
          }
        } else {
          results.add(instance);
        }
      } catch (Exception e) {
        throw new RuntimeException("Error mapping result set to entity", e);
      }
    }

    // Dodaj wszystkie unikalne wyniki
    results.addAll(uniqueResults.values());

    return results;
  }

  private T mapDocumentToEntity(Document document) {
    try {
      T instance = entityClass.getDeclaredConstructor().newInstance();

      for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
        String columnName = entry.getKey();
        Field field = entry.getValue();

        try {
          Object value = document.get(columnName);
          if (value != null) {
            // Konwersja typów dla MongoDB
            if (field.getType() == Boolean.class || field.getType() == boolean.class) {
              if (value instanceof Integer) {
                value = ((Integer) value) == 1;
              } else if (value instanceof Long) {
                value = ((Long) value) == 1L;
              } else if (value instanceof String) {
                value = "true".equalsIgnoreCase((String) value) || "1".equals(value);
              }
            }

            field.set(instance, value);
          }
        } catch (Exception e) {
          System.err.println("Warning: Field '" + columnName + "' not found in document");
        }
      }

      return instance;
    } catch (Exception e) {
      throw new RuntimeException("Error mapping MongoDB document to entity", e);
    }
  }

  // Metoda pomocnicza do znalezienia rzeczywistej nazwy kolumny
  private String getActualColumnName(String fieldName) {
    // Sprawdź, czy istnieje mapowanie dla tego pola
    if (metadata.fieldColumnNames().containsKey(fieldName)) {
      return metadata.fieldColumnNames().get(fieldName);
    }

    // Jeśli nie ma mapowania, sprawdź czy istnieje jako nazwa kolumny
    for (String columnName : metadata.columnFields().keySet()) {
      if (columnName.equals(fieldName)) {
        return columnName;
      }
    }

    // Jeśli nie znaleziono, zwróć oryginalną nazwę
    return fieldName;
  }

  public static <T> QueryBuilder<T> builder(FlexOrm orm, Class<T> entityClass, TableMetadata metadata) {
    return new QueryBuilder<>(orm, entityClass, metadata);
  }

  // Metoda pomocnicza do wykonywania zapytania count()
  public long count() {
    switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> {
        try {
          HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();
          try (Connection connection = dataSource.getConnection()) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT COUNT(");
            if (useDistinct) {
              sql.append("DISTINCT ");
            }
            sql.append("*) FROM ").append(metadata.tableName());

            if (!conditions.isEmpty()) {
              sql.append(" WHERE ");
              buildWhereClause(sql);
            }

            System.out.println("Executing COUNT SQL: " + sql.toString()); // DEBUG

            try (PreparedStatement statement = prepareStatement(connection, sql.toString())) {
              try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                  return resultSet.getLong(1);
                }
                return 0;
              }
            }
          }
        } catch (SQLException e) {
          throw new RuntimeException("Error executing count query", e);
        }
      }
      case MONGODB -> {
        MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
        MongoCollection<Document> collection = database.getCollection(metadata.tableName());

        Bson filter;
        if (mongoQuery != null) {
          filter = mongoQuery;
        } else {
          filter = buildMongoQuery();
        }

        return collection.countDocuments(filter);
      }
      case JSON -> {
        throw new UnsupportedOperationException("Count queries for JSON storage are not implemented yet");
      }
      default -> throw new UnsupportedOperationException("Database type not supported");
    }
  }

  // Metoda pomocnicza do wykonywania zapytań na surowym SQL
  public void executeRawUpdate(String sql, Consumer<Exception> errorHandler) {
    if (orm.getDatabaseType() == DatabaseType.MYSQL || orm.getDatabaseType() == DatabaseType.SQLLITE) {
      try {
        HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();
        try (Connection connection = dataSource.getConnection()) {
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
              String name = entry.getKey();
              Object value = entry.getValue();

              if (sql.contains(":" + name)) {
                String updatedSql = sql.replace(":" + name, "?");
                statement.setObject(1, value);
              }
            }

            statement.executeUpdate();
          }
        }
      } catch (SQLException e) {
        if (errorHandler != null) {
          errorHandler.accept(e);
        } else {
          throw new RuntimeException("Error executing raw SQL update", e);
        }
      }
    } else {
      throw new UnsupportedOperationException("Raw SQL updates are only supported for relational databases");
    }
  }
}