package pl.minecodes.orm.entity;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import pl.minecodes.orm.FlexOrm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import pl.minecodes.orm.table.TableMetadata;

public abstract class RelationalEntityAgent<T, ID> extends BaseEntityAgent<T, ID> {

  protected Connection activeConnection;

  protected RelationalEntityAgent(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
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
    insertIntoDatabase(entity, metadata);
  }

  @Override
  public void update(T entity) {
    validateEntity(entity);
    TableMetadata metadata = getTableMetadata(entityClass);
    updateInDatabase(entity, metadata);
  }

  @Override
  public void delete(T entity) {
    validateEntity(entity);
    TableMetadata metadata = getTableMetadata(entityClass);
    Object id = getEntityId(entity, metadata);
    deleteById((ID) id);
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
      } finally {
        if (autoClose) {
          connection.close();
        }
      }
    } catch (SQLException e) {
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
                    // Konwersja dla pól typu boolean
                    if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                      if (value instanceof Integer) {
                        boolean boolValue = ((Integer) value) != 0;
                        field.set(instance, boolValue);
                      } else if (value instanceof Long) {
                        boolean boolValue = ((Long) value) != 0L;
                        field.set(instance, boolValue);
                      } else if (value instanceof String) {
                        boolean boolValue = "true".equalsIgnoreCase((String) value) || "1".equals(value);
                        field.set(instance, boolValue);
                      } else {
                        field.set(instance, value);
                      }
                    } else {
                      field.set(instance, value);
                    }
                  }
                } catch (SQLException e) {
                  System.err.println("Warning: Problem accessing column: " + e.getMessage());
                }
              }

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
                  // Konwersja dla pól typu boolean
                  if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    if (value instanceof Integer) {
                      boolean boolValue = ((Integer) value) != 0;
                      field.set(instance, boolValue);
                    } else if (value instanceof Long) {
                      boolean boolValue = ((Long) value) != 0L;
                      field.set(instance, boolValue);
                    } else if (value instanceof String) {
                      boolean boolValue = "true".equalsIgnoreCase((String) value) || "1".equals(value);
                      field.set(instance, boolValue);
                    } else {
                      field.set(instance, value);
                    }
                  } else {
                    field.set(instance, value);
                  }
                }
              } catch (SQLException e) {
                System.err.println("Warning: Problem accessing column: " + e.getMessage());
              }
            }

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
}