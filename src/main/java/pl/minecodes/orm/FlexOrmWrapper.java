package pl.minecodes.orm;

import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.exception.ObjectIsNullException;
import pl.minecodes.orm.exception.ObjectRequiredAnnotationsException;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.zaxxer.hikari.HikariDataSource;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class FlexOrmWrapper {

  private final FlexOrm orm;
  private final Map<Class<?>, TableMetadata> metadataCache = new HashMap<>();

  public FlexOrmWrapper(FlexOrm orm) {
    this.orm = orm;
  }

  public <T> void save(T object, Class<T> objectClass) {
    validateObject(object, objectClass);

    TableMetadata metadata = getTableMetadata(objectClass);
    Object id = getEntityId(object, metadata);

    boolean exists = false;
    if (id != null) {
      exists = existsById(id, objectClass, metadata);
    }

    switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> {
        if (exists) {
          updateInRelationalDatabase(object, metadata);
        } else {
          insertIntoRelationalDatabase(object, metadata);
        }
      }
      case MONGODB -> saveToMongoDB(object, metadata);
      case JSON -> saveToJson(object, metadata);
    }
  }

  public <T> void delete(T object, Class<T> objectClass) {
    validateObject(object, objectClass);

    TableMetadata metadata = getTableMetadata(objectClass);
    Object id = getEntityId(object, metadata);

    switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> deleteFromRelationalDatabase(id, metadata);
      case MONGODB -> deleteFromMongoDB(id, metadata);
      case JSON -> deleteFromJson(id, metadata);
    }
  }

  public <T, ID> Optional<T> findById(ID id, Class<T> objectClass) {
    validateClass(objectClass);

    if (id == null) {
      throw new ObjectIsNullException("Provided ID is null!");
    }

    TableMetadata metadata = getTableMetadata(objectClass);

    return switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> findByIdInRelationalDatabase(id, objectClass, metadata);
      case MONGODB -> findByIdInMongoDB(id, objectClass, metadata);
      case JSON -> findByIdInJson(id, objectClass, metadata);
    };
  }

  public <T> List<T> findAll(Class<T> objectClass) {
    validateClass(objectClass);

    TableMetadata metadata = getTableMetadata(objectClass);

    return switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> findAllInRelationalDatabase(objectClass, metadata);
      case MONGODB -> findAllInMongoDB(objectClass, metadata);
      case JSON -> findAllInJson(objectClass, metadata);
    };
  }

  private <T> void validateObject(T object, Class<?> objectClass) {
    if (object == null) {
      throw new ObjectIsNullException("Provided object is null!");
    }

    validateClass(objectClass);
  }

  private void validateClass(Class<?> objectClass) {
    if (objectClass == null) {
      throw new ObjectIsNullException("Provided object class is null!");
    }

    if (!objectClass.isAnnotationPresent(OrmEntity.class)) {
      throw new ObjectRequiredAnnotationsException("Provided object class is not annotated with @OrmEntity!");
    }
  }

  private TableMetadata getTableMetadata(Class<?> objectClass) {
    return metadataCache.computeIfAbsent(objectClass, this::extractTableMetadata);
  }

  private TableMetadata extractTableMetadata(Class<?> objectClass) {
    OrmEntity ormEntity = objectClass.getAnnotation(OrmEntity.class);
    String tableName = ormEntity.table().isEmpty() ? objectClass.getSimpleName().toLowerCase() : ormEntity.table();

    Field idField = null;
    Map<String, Field> columnFields = new HashMap<>();
    Map<String, String> fieldColumnNames = new HashMap<>();

    for (Field field : objectClass.getDeclaredFields()) {
      field.setAccessible(true);

      if (field.isAnnotationPresent(OrmEntityId.class)) {
        idField = field;
      }

      if (field.isAnnotationPresent(OrmField.class)) {
        OrmField ormField = field.getAnnotation(OrmField.class);
        String columnName = ormField.name().isEmpty() ? field.getName() : ormField.name();
        columnFields.put(columnName, field);
        fieldColumnNames.put(field.getName(), columnName);
      } else {
        columnFields.put(field.getName(), field);
        fieldColumnNames.put(field.getName(), field.getName());
      }
    }

    if (idField == null) {
      throw new ObjectRequiredAnnotationsException("Class " + objectClass.getName() + " does not have a field annotated with @OrmEntityId");
    }

    return new TableMetadata(tableName, idField, columnFields, fieldColumnNames);
  }

  private Object getEntityId(Object entity, TableMetadata metadata) {
    try {
      return metadata.idField().get(entity);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Could not access ID field", e);
    }
  }

  private <T, ID> boolean existsById(ID id, Class<T> objectClass, TableMetadata metadata) {
    switch (orm.getDatabaseType()) {
      case MYSQL, SQLLITE -> {
        HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

        try (Connection connection = dataSource.getConnection()) {
          String sql = "SELECT 1 FROM " + metadata.tableName() + " WHERE " + getColumnNameForField(metadata.idField(), metadata) + " = ?";

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
      case MONGODB -> {
        MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
        MongoCollection<Document> collection = database.getCollection(metadata.tableName());

        Document query = new Document(getColumnNameForField(metadata.idField(), metadata), id);
        return collection.find(query).first() != null;
      }
      case JSON -> {
        throw new UnsupportedOperationException("JSON storage not implemented yet");
      }
    }

    return false;
  }

  private String getColumnNameForField(Field field, TableMetadata metadata) {
    return metadata.fieldColumnNames().getOrDefault(field.getName(), field.getName());
  }

  private <T> void insertIntoRelationalDatabase(T object, TableMetadata metadata) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      StringBuilder sql = new StringBuilder();
      sql.append("INSERT INTO ").append(metadata.tableName()).append(" (");

      List<Object> values = new ArrayList<>();
      StringBuilder placeholders = new StringBuilder();

      for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
        try {
          Object value = entry.getValue().get(object);
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
      throw new RuntimeException("Error inserting object to relational database", e);
    }
  }

  private <T> void updateInRelationalDatabase(T object, TableMetadata metadata) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      StringBuilder sql = new StringBuilder();
      sql.append("UPDATE ").append(metadata.tableName()).append(" SET ");

      List<Object> values = new ArrayList<>();
      Object idValue = null;
      String idColumnName = getColumnNameForField(metadata.idField(), metadata);

      boolean first = true;
      for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
        try {
          if (entry.getValue().equals(metadata.idField())) {
            idValue = entry.getValue().get(object);
            continue;
          }

          Object value = entry.getValue().get(object);
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
      throw new RuntimeException("Error updating object in relational database", e);
    }
  }

  private void deleteFromRelationalDatabase(Object id, TableMetadata metadata) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      String idColumnName = getColumnNameForField(metadata.idField(), metadata);
      String sql = "DELETE FROM " + metadata.tableName() + " WHERE " + idColumnName + " = ?";

      System.out.println(sql);
      System.out.println(id);

      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, id);
        statement.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error deleting object from relational database", e);
    }
  }

  private <T, ID> Optional<T> findByIdInRelationalDatabase(ID id, Class<T> objectClass, TableMetadata metadata) {
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      String idColumnName = getColumnNameForField(metadata.idField(), metadata);
      String sql = "SELECT * FROM " + metadata.tableName() + " WHERE " + idColumnName + " = ?";

      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, id);

        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            T instance = objectClass.getDeclaredConstructor().newInstance();

            for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
              Object value = resultSet.getObject(entry.getKey());
              if (value != null) {
                entry.getValue().set(instance, value);
              }
            }

            return Optional.of(instance);
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Error finding object by ID in relational database", e);
    }

    return Optional.empty();
  }

  private <T> List<T> findAllInRelationalDatabase(Class<T> objectClass, TableMetadata metadata) {
    List<T> results = new ArrayList<>();
    HikariDataSource dataSource = (HikariDataSource) orm.getConnection().getConnection();

    try (Connection connection = dataSource.getConnection()) {
      String sql = "SELECT * FROM " + metadata.tableName();

      try (PreparedStatement statement = connection.prepareStatement(sql);
          ResultSet resultSet = statement.executeQuery()) {

        while (resultSet.next()) {
          T instance = objectClass.getDeclaredConstructor().newInstance();

          for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
            Object value = resultSet.getObject(entry.getKey());
            if (value != null) {
              entry.getValue().set(instance, value);
            }
          }

          results.add(instance);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Error finding all objects in relational database", e);
    }

    return results;
  }

  private <T> void saveToMongoDB(T object, TableMetadata metadata) {
    MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
    MongoCollection<Document> collection = database.getCollection(metadata.tableName());

    Document document = new Document();
    Object id = null;

    try {
      for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
        Object value = entry.getValue().get(object);
        if (value != null) {
          document.append(entry.getKey(), value);
          if (entry.getValue().equals(metadata.idField())) {
            id = value;
          }
        }
      }

      if (id != null) {
        Document query = new Document(getColumnNameForField(metadata.idField(), metadata), id);
        Document existingDoc = collection.find(query).first();

        if (existingDoc != null) {
          collection.replaceOne(query, document);
        } else {
          collection.insertOne(document);
        }
      } else {
        collection.insertOne(document);
      }

    } catch (IllegalAccessException e) {
      throw new RuntimeException("Error creating MongoDB document", e);
    }
  }

  private void deleteFromMongoDB(Object id, TableMetadata metadata) {
    MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
    MongoCollection<Document> collection = database.getCollection(metadata.tableName());

    String idColumnName = getColumnNameForField(metadata.idField(), metadata);
    collection.deleteOne(new Document(idColumnName, id));
  }

  private <T, ID> Optional<T> findByIdInMongoDB(ID id, Class<T> objectClass, TableMetadata metadata) {
    MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
    MongoCollection<Document> collection = database.getCollection(metadata.tableName());

    String idColumnName = getColumnNameForField(metadata.idField(), metadata);
    Document query = new Document(idColumnName, id);
    Document result = collection.find(query).first();

    if (result == null) {
      return Optional.empty();
    }

    try {
      T instance = objectClass.getDeclaredConstructor().newInstance();

      for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
        Object value = result.get(entry.getKey());
        if (value != null) {
          entry.getValue().set(instance, value);
        }
      }

      return Optional.of(instance);
    } catch (Exception e) {
      throw new RuntimeException("Error creating instance from MongoDB document", e);
    }
  }

  private <T> List<T> findAllInMongoDB(Class<T> objectClass, TableMetadata metadata) {
    List<T> results = new ArrayList<>();
    MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
    MongoCollection<Document> collection = database.getCollection(metadata.tableName());

    for (Document document : collection.find()) {
      try {
        T instance = objectClass.getDeclaredConstructor().newInstance();

        for (Map.Entry<String, Field> entry : metadata.columnFields().entrySet()) {
          Object value = document.get(entry.getKey());
          if (value != null) {
            entry.getValue().set(instance, value);
          }
        }

        results.add(instance);
      } catch (Exception e) {
        throw new RuntimeException("Error creating instance from MongoDB document", e);
      }
    }

    return results;
  }

  private <T> void saveToJson(T object, TableMetadata metadata) {
    throw new UnsupportedOperationException("JSON storage not implemented yet");
  }

  private void deleteFromJson(Object id, TableMetadata metadata) {
    throw new UnsupportedOperationException("JSON storage not implemented yet");
  }

  private <T, ID> Optional<T> findByIdInJson(ID id, Class<T> objectClass, TableMetadata metadata) {
    throw new UnsupportedOperationException("JSON storage not implemented yet");
  }

  private <T> List<T> findAllInJson(Class<T> objectClass, TableMetadata metadata) {
    throw new UnsupportedOperationException("JSON storage not implemented yet");
  }

  private record TableMetadata(
      String tableName,
      Field idField,
      Map<String, Field> columnFields,
      Map<String, String> fieldColumnNames
  ) {}
}