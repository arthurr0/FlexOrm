package pl.minecodes.orm.query;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.conversions.Bson;
import pl.minecodes.orm.DatabaseType;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.table.TableMetadata;
import pl.minecodes.orm.util.SqlSanitizer;

public class Query<T> {

  public static final int DEFAULT_QUERY_LIMIT = 1000;

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
  private boolean unlimitedResults = false;

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

  @Deprecated
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

  public Query<T> unlimited() {
    this.unlimitedResults = true;
    return this;
  }

  public List<T> execute() {
    return switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> executeRelationalQuery();
      case MONGODB -> executeMongoQuery();
    };
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

    FindIterable<Document> findIterable = collection.find(filter);

    if (useDistinct && !orderBy.isEmpty()) {
      Document sort = new Document();
      for (String order : orderBy) {
        String[] parts = order.split(" ");
        sort.append(parts[0], "ASC".equals(parts[1]) ? 1 : -1);
      }
      findIterable.sort(sort);
    }

    int effectiveLimit = getEffectiveLimit();
    if (effectiveLimit > 0) {
      findIterable.limit(effectiveLimit);
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

  private String buildSqlQuery() {
    StringBuilder sql = new StringBuilder();

    sql.append("SELECT ");
    if (useDistinct) {
      sql.append("DISTINCT ");
    }
    sql.append("* FROM ").append(SqlSanitizer.sanitizeTableName(metadata.tableName()));

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

    int effectiveLimit = getEffectiveLimit();
    if (effectiveLimit > 0) {
      sql.append(" LIMIT ").append(effectiveLimit);
    }

    if (offset != null) {
      sql.append(" OFFSET ").append(offset);
    }

    return sql.toString();
  }

  private int getEffectiveLimit() {
    if (limit != null) {
      return limit;
    }
    if (unlimitedResults) {
      return -1;
    }
    return DEFAULT_QUERY_LIMIT;
  }

  private void buildWhereClause(StringBuilder sql) {
    for (int i = 0; i < conditions.size(); i++) {
      QueryCondition condition = conditions.get(i);

      if (i > 0) {
        sql.append(condition.logicalOperator() == LogicalOperator.AND ? " AND " : " OR ");
      }

      String columnName = getActualColumnName(condition.field());
      sql.append(columnName);

      switch (condition.operator()) {
        case EQUALS -> {
          if (condition.value() == null) {
            sql.append(" IS NULL");
          } else if (condition.value() instanceof Boolean) {
            if (orm.getDatabaseType() == DatabaseType.SQLLITE) {
              boolean boolValue = (Boolean) condition.value();
              sql.append(boolValue ? " = 1" : " = 0");
            } else {
              sql.append(" = ?");
            }
          } else {
            sql.append(" = ?");
          }
        }
        case NOT_EQUALS -> {
          if (condition.value() == null) {
            sql.append(" IS NOT NULL");
          } else if (condition.value() instanceof Boolean) {
            if (orm.getDatabaseType() == DatabaseType.SQLLITE) {
              boolean boolValue = (Boolean) condition.value();
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
          List<?> values = (List<?>) condition.value();
          if (values.isEmpty()) {
            sql.append(" IN (NULL)");
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
      String columnName = getActualColumnName(condition.field());
      Document conditionDoc = new Document();

      switch (condition.operator()) {
        case EQUALS -> query.append(columnName, condition.value());
        case NOT_EQUALS -> conditionDoc.append("$ne", condition.value());
        case GREATER_THAN -> conditionDoc.append("$gt", condition.value());
        case LESS_THAN -> conditionDoc.append("$lt", condition.value());
        case GREATER_THAN_OR_EQUALS -> conditionDoc.append("$gte", condition.value());
        case LESS_THAN_OR_EQUALS -> conditionDoc.append("$lte", condition.value());
        case LIKE -> {
          String pattern = condition.value().toString();
          if (!pattern.startsWith("%")) {
            pattern = "^" + pattern;
          } else {
            pattern = pattern.substring(1);
          }
          if (!pattern.endsWith("%")) {
            pattern = pattern + "$";
          } else {
            pattern = pattern.substring(0, pattern.length() - 1);
          }
          pattern = pattern.replace("%", ".*");
          conditionDoc.append("$regex", pattern).append("$options", "i");
        }
        case IN -> conditionDoc.append("$in", condition.value());
        case IS_NULL -> conditionDoc.append("$exists", false);
        case IS_NOT_NULL -> conditionDoc.append("$exists", true);
      }

      if (!conditionDoc.isEmpty()) {
        query.append(columnName, conditionDoc);
      }
    }

    return query;
  }

  private PreparedStatement prepareStatement(Connection connection, String sql)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);

    if (customSql != null) {
      int paramIndex = 1;
      for (Map.Entry<String, Object> entry : parameters.entrySet()) {
        String name = entry.getKey();
        Object value = entry.getValue();

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
      if (condition.operator() == Operator.IS_NULL ||
          condition.operator() == Operator.IS_NOT_NULL) {
        continue;
      }

      if ((condition.operator() == Operator.EQUALS
          || condition.operator() == Operator.NOT_EQUALS) &&
          (condition.value() == null ||
              (condition.value() instanceof Boolean
                  && orm.getDatabaseType() == DatabaseType.SQLLITE))) {
        continue;
      }

      if (condition.operator() == Operator.IN) {
        List<?> values = (List<?>) condition.value();
        if (!values.isEmpty()) {
          for (Object value : values) {
            setStatementParameter(statement, paramIndex++, value);
          }
        }
      } else if (condition.operator() == Operator.LIKE) {
        String value = condition.value().toString();
        if (!value.contains("%")) {
          value = "%" + value + "%";
        }
        statement.setString(paramIndex++, value);
      } else {
        setStatementParameter(statement, paramIndex++, condition.value());
      }
    }

    return statement;
  }

  private void setStatementParameter(PreparedStatement statement, int index, Object value)
      throws SQLException {
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
    Map<Object, T> uniqueResults = new HashMap<>();

    while (resultSet.next()) {
      try {
        T instance = entityClass.getDeclaredConstructor().newInstance();
        Object idValue = null;

        for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
          String columnName = entry.getKey();
          Field field = entry.getValue();

          try {
            Object value = resultSet.getObject(columnName);

            if (value != null) {
              value = convertValue(value, field.getType());
              field.set(instance, value);

              if (field.equals(metadata.idField())) {
                idValue = value;
              }
            }
          } catch (SQLException ignored) {
          }
        }

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
        } catch (Exception ignored) {
        }
      }

      return instance;
    } catch (Exception e) {
      throw new RuntimeException("Error mapping MongoDB document to entity", e);
    }
  }

  private String getActualColumnName(String fieldName) {
    String columnName;
    if (metadata.fieldColumnNames().containsKey(fieldName)) {
      columnName = metadata.fieldColumnNames().get(fieldName);
    } else if (metadata.columnFields().containsKey(fieldName)) {
      columnName = fieldName;
    } else {
      columnName = fieldName;
    }
    return SqlSanitizer.sanitizeColumnName(columnName);
  }

  public static <T> QueryBuilder<T> builder(FlexOrm orm, Class<T> entityClass,
      TableMetadata metadata) {
    return new QueryBuilder<>(orm, entityClass, metadata);
  }

  public long count() {
    return switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> countRelational();
      case MONGODB -> countMongo();
    };
  }

  private long countRelational() {
    try {
      HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();
      try (Connection connection = dataSource.getConnection()) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(");
        if (useDistinct) {
          sql.append("DISTINCT ");
        }
        sql.append("*) FROM ").append(SqlSanitizer.sanitizeTableName(metadata.tableName()));

        if (!conditions.isEmpty()) {
          sql.append(" WHERE ");
          buildWhereClause(sql);
        }

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

  private long countMongo() {
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

  public void executeRawUpdate(String sql, Consumer<Exception> errorHandler) {
    if (orm.getDatabaseType() == DatabaseType.MYSQL
        || orm.getDatabaseType() == DatabaseType.SQLLITE) {
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
      throw new UnsupportedOperationException(
          "Raw SQL updates are only supported for relational databases");
    }
  }

  private Object convertValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }

    if (targetType == Long.class || targetType == long.class) {
      if (value instanceof Integer) {
        return ((Integer) value).longValue();
      } else if (value instanceof Number) {
        return ((Number) value).longValue();
      }
    } else if (targetType == Integer.class || targetType == int.class) {
      if (value instanceof Long) {
        return ((Long) value).intValue();
      } else if (value instanceof Number) {
        return ((Number) value).intValue();
      }
    } else if (targetType == Double.class || targetType == double.class) {
      if (value instanceof Number) {
        return ((Number) value).doubleValue();
      }
    } else if (targetType == Float.class || targetType == float.class) {
      if (value instanceof Number) {
        return ((Number) value).floatValue();
      }
    } else if (targetType == boolean.class || targetType == Boolean.class) {
      if (value instanceof Integer) {
        return ((Integer) value) != 0;
      } else if (value instanceof Long) {
        return ((Long) value) != 0L;
      } else if (value instanceof String) {
        return "true".equalsIgnoreCase((String) value) || "1".equals(value);
      }
    }

    return value;
  }
}