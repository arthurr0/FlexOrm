package pl.minecodes.orm.relation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.table.TableMetadata;

public class CascadeHandler {

  private final FlexOrm orm;
  private final Map<Class<?>, TableMetadata> metadataCache;
  private final Function<Class<?>, TableMetadata> metadataExtractor;

  public CascadeHandler(FlexOrm orm, Map<Class<?>, TableMetadata> metadataCache,
      Function<Class<?>, TableMetadata> metadataExtractor) {
    this.orm = orm;
    this.metadataCache = metadataCache;
    this.metadataExtractor = metadataExtractor;
  }

  public <T> void handleCascadeSave(T entity, TableMetadata metadata, Connection connection,
      BiConsumer<Object, Connection> saveCallback) {
    for (RelationInfo relation : metadata.relations()) {
      if (!relation.cascade()) {
        continue;
      }

      try {
        Object relatedValue = relation.field().get(entity);
        if (relatedValue == null) {
          continue;
        }

        switch (relation.type()) {
          case ONE_TO_ONE, MANY_TO_ONE -> {
            saveCallback.accept(relatedValue, connection);
          }
          case ONE_TO_MANY, MANY_TO_MANY -> {
            if (relatedValue instanceof Collection<?> collection) {
              for (Object item : collection) {
                saveCallback.accept(item, connection);
              }
            }
          }
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Error accessing relation field for cascade save", e);
      }
    }
  }

  public <T> void handleCascadeDelete(T entity, TableMetadata metadata, Connection connection) {
    for (RelationInfo relation : metadata.relations()) {
      if (!relation.cascade()) {
        continue;
      }

      try {
        Object relatedValue = relation.field().get(entity);
        if (relatedValue == null) {
          continue;
        }

        Class<?> targetClass = relation.targetEntity();
        TableMetadata targetMetadata = getOrCreateMetadata(targetClass);

        switch (relation.type()) {
          case ONE_TO_ONE -> {
            deleteEntity(relatedValue, targetMetadata, connection);
          }
          case ONE_TO_MANY -> {
            if (relatedValue instanceof Collection<?> collection) {
              for (Object item : collection) {
                deleteEntity(item, targetMetadata, connection);
              }
            }
          }
          case MANY_TO_MANY -> {
            Object entityId = metadata.idField().get(entity);
            String joinTable = relation.joinTable().isEmpty()
                ? metadata.tableName() + "_" + targetMetadata.tableName()
                : relation.joinTable();
            String joinColumn = relation.joinColumn().isEmpty()
                ? metadata.tableName() + "_id"
                : relation.joinColumn();

            deleteFromJoinTable(joinTable, joinColumn, entityId, connection);
          }
          default -> {
          }
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Error accessing relation field for cascade delete", e);
      }
    }
  }

  private void deleteEntity(Object entity, TableMetadata metadata, Connection connection) {
    try {
      Object id = metadata.idField().get(entity);
      if (id == null) {
        return;
      }

      String idColumn = metadata.fieldColumnNames().getOrDefault(
          metadata.idField().getName(),
          metadata.idField().getName()
      );

      String sql = "DELETE FROM " + metadata.tableName() + " WHERE " + idColumn + " = ?";

      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setObject(1, id);
        stmt.executeUpdate();
      }
    } catch (Exception e) {
      throw new RuntimeException("Error deleting related entity", e);
    }
  }

  private void deleteFromJoinTable(String joinTable, String joinColumn, Object entityId,
      Connection connection) {
    try {
      String sql = "DELETE FROM " + joinTable + " WHERE " + joinColumn + " = ?";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setObject(1, entityId);
        stmt.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error deleting from join table", e);
    }
  }

  public <T> void saveManyToManyRelations(T entity, TableMetadata metadata, Connection connection) {
    for (RelationInfo relation : metadata.relations()) {
      if (relation.type() != RelationType.MANY_TO_MANY) {
        continue;
      }

      try {
        Object relatedCollection = relation.field().get(entity);
        if (relatedCollection == null || !(relatedCollection instanceof Collection<?> collection)) {
          continue;
        }

        if (collection.isEmpty()) {
          continue;
        }

        Class<?> targetClass = relation.targetEntity();
        TableMetadata targetMetadata = getOrCreateMetadata(targetClass);

        Object entityId = metadata.idField().get(entity);

        String joinTable = relation.joinTable().isEmpty()
            ? metadata.tableName() + "_" + targetMetadata.tableName()
            : relation.joinTable();

        String joinColumn = relation.joinColumn().isEmpty()
            ? metadata.tableName() + "_id"
            : relation.joinColumn();

        String inverseJoinColumn = relation.inverseJoinColumn().isEmpty()
            ? targetMetadata.tableName() + "_id"
            : relation.inverseJoinColumn();

        deleteFromJoinTable(joinTable, joinColumn, entityId, connection);

        for (Object related : collection) {
          Object relatedId = targetMetadata.idField().get(related);
          if (relatedId != null) {
            insertIntoJoinTable(joinTable, joinColumn, inverseJoinColumn, entityId, relatedId,
                connection);
          }
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Error saving ManyToMany relations", e);
      }
    }
  }

  private void insertIntoJoinTable(String joinTable, String joinColumn, String inverseJoinColumn,
      Object entityId, Object relatedId, Connection connection) {
    try {
      String sql = "INSERT INTO " + joinTable + " (" + joinColumn + ", " + inverseJoinColumn
          + ") VALUES (?, ?)";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setObject(1, entityId);
        stmt.setObject(2, relatedId);
        stmt.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error inserting into join table", e);
    }
  }

  private TableMetadata getOrCreateMetadata(Class<?> entityClass) {
    return metadataCache.computeIfAbsent(entityClass, metadataExtractor);
  }
}
