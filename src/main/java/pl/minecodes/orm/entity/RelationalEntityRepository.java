package pl.minecodes.orm.entity;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.relation.CascadeHandler;
import pl.minecodes.orm.relation.RelationLoader;
import pl.minecodes.orm.table.TableMetadata;

public abstract class RelationalEntityRepository<T, ID> extends BaseEntityRepository<T, ID> {

  protected Connection activeConnection;
  protected final RelationLoader relationLoader;
  protected final CascadeHandler cascadeHandler;

  protected RelationalEntityRepository(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
    this.relationLoader = new RelationLoader(orm, metadataCache, this::extractTableMetadata);
    this.cascadeHandler = new CascadeHandler(orm, metadataCache, this::extractTableMetadata);
  }

  @Override
  protected Object executeRawQueryInternal(String rawQuery) {
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;

      try {
        PreparedStatement statement = connection.prepareStatement(rawQuery);
        return statement.executeQuery();
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error executing raw query", e);
    }
  }

  @Override
  protected void insert(T entity) {
    TableMetadata metadata = getTableMetadata(entityClass);
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;
      try {
        cascadeHandler.handleCascadeSave(entity, metadata, connection, (relatedEntity, conn) -> {
          saveRelatedEntity(relatedEntity, conn);
        });
        insertIntoDatabase(entity, metadata);
        cascadeHandler.saveManyToManyRelations(entity, metadata, connection);
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error in cascade insert", e);
    }
  }

  @SuppressWarnings("unchecked")
  private void saveRelatedEntity(Object entity, Connection connection) {
    Class<?> entityClass = entity.getClass();
    TableMetadata metadata = getTableMetadata(entityClass);
    try {
      Object id = metadata.idField().get(entity);
      if (id != null && existsByIdInternal(id, metadata, connection)) {
        updateInDatabaseInternal(entity, metadata, connection);
      } else {
        insertIntoDatabaseInternal(entity, metadata, connection);
      }
    } catch (Exception e) {
      throw new RuntimeException("Error saving related entity", e);
    }
  }

  private boolean existsByIdInternal(Object id, TableMetadata metadata, Connection connection) {
    try {
      String idColumnName = getColumnNameForField(metadata.idField(), metadata);
      String sql = "SELECT 1 FROM " + metadata.tableName() + " WHERE " + idColumnName + " = ?";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, id);
        try (ResultSet resultSet = statement.executeQuery()) {
          return resultSet.next();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error checking entity existence", e);
    }
  }

  @Override
  public void update(T entity) {
    validateEntity(entity);
    TableMetadata metadata = getTableMetadata(entityClass);
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;
      try {
        cascadeHandler.handleCascadeSave(entity, metadata, connection, (relatedEntity, conn) -> {
          saveRelatedEntity(relatedEntity, conn);
        });
        updateInDatabase(entity, metadata);
        cascadeHandler.saveManyToManyRelations(entity, metadata, connection);
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error in cascade update", e);
    }
  }

  @Override
  public void delete(T entity) {
    validateEntity(entity);
    TableMetadata metadata = getTableMetadata(entityClass);
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;
      try {
        cascadeHandler.handleCascadeDelete(entity, metadata, connection);
        Object id = getEntityId(entity, metadata);
        deleteFromDatabase((ID) id, metadata);
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error in cascade delete", e);
    }
  }

  @Override
  public void deleteById(ID id) {
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }
    TableMetadata metadata = getTableMetadata(entityClass);
    deleteFromDatabase(id, metadata);
  }

  @Override
  public Optional<T> findById(ID id) {
    if (id == null) {
      return Optional.empty();
    }
    TableMetadata metadata = getTableMetadata(entityClass);
    return findByIdInDatabase(id, metadata);
  }

  @Override
  public List<T> findAll() {
    TableMetadata metadata = getTableMetadata(entityClass);
    return findAllInDatabase(metadata);
  }

  @Override
  protected void beginTransactionInternal() {
    try {
      HikariDataSource dataSource = getDataSource();
      activeConnection = dataSource.getConnection();
      activeConnection.setAutoCommit(false);
    } catch (SQLException e) {
      throw new RuntimeException("Error starting transaction", e);
    }
  }

  @Override
  protected void commitTransactionInternal() {
    try {
      if (activeConnection != null && !activeConnection.isClosed()) {
        activeConnection.commit();
        activeConnection.close();
      }
      activeConnection = null;
    } catch (SQLException e) {
      throw new RuntimeException("Error committing transaction", e);
    }
  }

  @Override
  protected void rollbackTransactionInternal() {
    try {
      if (activeConnection != null && !activeConnection.isClosed()) {
        activeConnection.rollback();
        activeConnection.close();
      }
      activeConnection = null;
    } catch (SQLException e) {
      throw new RuntimeException("Error rolling back transaction", e);
    }
  }

  protected HikariDataSource getDataSource() {
    return (HikariDataSource) orm.getConnection().getConnection();
  }

  protected boolean existsById(ID id) {
    TableMetadata metadata = getTableMetadata(entityClass);

    try {
      Connection connection = getConnection();
      String idColumnName = getColumnNameForField(metadata.idField(), metadata);
      String sql = "SELECT 1 FROM " + metadata.tableName() + " WHERE " + idColumnName + " = ?";

      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, id);

        try (ResultSet resultSet = statement.executeQuery()) {
          return resultSet.next();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error checking if entity exists", e);
    }
  }

  protected Connection getConnection() throws SQLException {
    if (activeConnection != null && !activeConnection.isClosed()) {
      return activeConnection;
    }
    return getDataSource().getConnection();
  }

  protected void insertIntoDatabase(T entity, TableMetadata metadata) {
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;

      try {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(metadata.tableName()).append(" (");

        List<Object> values = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        StringBuilder placeholders = new StringBuilder();

        for (var entry : metadata.columnFields().entrySet()) {
          try {
            if (entry.getValue().equals(metadata.idField())) {
              Object idValue = entry.getValue().get(entity);
              if (idValue == null) {
                continue;
              }
            }
            Object value = entry.getValue().get(entity);
            if (!values.isEmpty()) {
              sql.append(", ");
              placeholders.append(", ");
            }
            sql.append(entry.getKey());
            columns.add(entry.getKey());
            placeholders.append("?");
            values.add(value);
          } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access field " + entry.getKey(), e);
          }
        }

        sql.append(") VALUES (").append(placeholders).append(")");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString(),
            java.sql.Statement.RETURN_GENERATED_KEYS)) {
          for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
          }
          statement.executeUpdate();

          try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
              Object generatedId = generatedKeys.getObject(1);
              Field idField = metadata.idField();
              if (idField.getType() == Long.class || idField.getType() == long.class) {
                idField.set(entity, generatedKeys.getLong(1));
              } else if (idField.getType() == Integer.class || idField.getType() == int.class) {
                idField.set(entity, generatedKeys.getInt(1));
              } else {
                idField.set(entity, generatedId);
              }
            }
          }
        }
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException | IllegalAccessException e) {
      throw new RuntimeException("Error inserting entity to database", e);
    }
  }

  protected void updateInDatabase(T entity, TableMetadata metadata) {
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;

      try {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(metadata.tableName()).append(" SET ");

        List<Object> values = new ArrayList<>();
        Object idValue = null;
        String idColumnName = getColumnNameForField(metadata.idField(), metadata);

        boolean first = true;
        for (var entry : metadata.columnFields().entrySet()) {
          try {
            if (entry.getValue().equals(metadata.idField())) {
              idValue = entry.getValue().get(entity);
              continue;
            }

            Object value = entry.getValue().get(entity);
            if (!first) {
              sql.append(", ");
            }
            sql.append(entry.getKey()).append(" = ?");
            values.add(value);
            first = false;
          } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access field " + entry.getKey(), e);
          }
        }

        sql.append(" WHERE ").append(idColumnName).append(" = ?");
        values.add(idValue);

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
          for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
          }
          statement.executeUpdate();
        }
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error updating entity in database", e);
    }
  }

  protected void deleteFromDatabase(ID id, TableMetadata metadata) {
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;

      try {
        String idColumnName = getColumnNameForField(metadata.idField(), metadata);
        String sql = "DELETE FROM " + metadata.tableName() + " WHERE " + idColumnName + " = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
          statement.setObject(1, id);
          statement.executeUpdate();
        }
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error deleting entity from database", e);
    }
  }

  protected Optional<T> findByIdInDatabase(ID id, TableMetadata metadata) {
    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;

      try {
        String idColumnName = getColumnNameForField(metadata.idField(), metadata);
        String sql = "SELECT * FROM " + metadata.tableName() + " WHERE " + idColumnName + " = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
          statement.setObject(1, id);

          try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
              T instance = entityClass.getDeclaredConstructor().newInstance();

              for (var entry : metadata.columnFields().entrySet()) {
                String columnName = entry.getKey();
                Field field = entry.getValue();

                try {
                  Object value = resultSet.getObject(columnName);

                  if (value != null) {
                    value = convertValue(value, field.getType());
                    field.set(instance, value);
                  }
                } catch (SQLException ignored) {
                }
              }

              relationLoader.loadRelations(instance, metadata, connection);

              return Optional.of(instance);
            }
          }
        }

        return Optional.empty();
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Error finding entity by ID in database", e);
    }
  }

  protected List<T> findAllInDatabase(TableMetadata metadata) {
    List<T> results = new ArrayList<>();

    try {
      Connection connection = getConnection();
      boolean autoClose = activeConnection == null;

      try {
        String sql = "SELECT * FROM " + metadata.tableName();

        try (PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()) {

          while (resultSet.next()) {
            T instance = entityClass.getDeclaredConstructor().newInstance();

            for (var entry : metadata.columnFields().entrySet()) {
              try {
                String columnName = entry.getKey();
                Field field = entry.getValue();

                Object value = resultSet.getObject(columnName);

                if (value != null) {
                  value = convertValue(value, field.getType());
                  field.set(instance, value);
                }
              } catch (SQLException ignored) {
              }
            }

            relationLoader.loadRelations(instance, metadata, connection);

            results.add(instance);
          }
        }

        return results;
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Error finding all entities in database", e);
    }
  }

  private void insertIntoDatabaseInternal(Object entity, TableMetadata metadata,
      Connection connection) {
    try {
      StringBuilder sql = new StringBuilder();
      sql.append("INSERT INTO ").append(metadata.tableName()).append(" (");

      List<Object> values = new ArrayList<>();
      StringBuilder placeholders = new StringBuilder();

      for (var entry : metadata.columnFields().entrySet()) {
        try {
          Object value = entry.getValue().get(entity);
          if (!values.isEmpty()) {
            sql.append(", ");
            placeholders.append(", ");
          }
          sql.append(entry.getKey());
          placeholders.append("?");
          values.add(value);
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Could not access field " + entry.getKey(), e);
        }
      }

      sql.append(") VALUES (").append(placeholders).append(")");

      try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
        for (int i = 0; i < values.size(); i++) {
          statement.setObject(i + 1, values.get(i));
        }
        statement.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error inserting related entity to database", e);
    }
  }

  private void updateInDatabaseInternal(Object entity, TableMetadata metadata,
      Connection connection) {
    try {
      StringBuilder sql = new StringBuilder();
      sql.append("UPDATE ").append(metadata.tableName()).append(" SET ");

      List<Object> values = new ArrayList<>();
      Object idValue = null;
      String idColumnName = getColumnNameForField(metadata.idField(), metadata);

      boolean first = true;
      for (var entry : metadata.columnFields().entrySet()) {
        try {
          if (entry.getValue().equals(metadata.idField())) {
            idValue = entry.getValue().get(entity);
            continue;
          }

          Object value = entry.getValue().get(entity);
          if (!first) {
            sql.append(", ");
          }
          sql.append(entry.getKey()).append(" = ?");
          values.add(value);
          first = false;
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Could not access field " + entry.getKey(), e);
        }
      }

      sql.append(" WHERE ").append(idColumnName).append(" = ?");
      values.add(idValue);

      try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
        for (int i = 0; i < values.size(); i++) {
          statement.setObject(i + 1, values.get(i));
        }
        statement.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error updating related entity in database", e);
    }
  }

  protected Object convertValue(Object value, Class<?> targetType) {
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