package pl.minecodes.orm.relation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.FetchType;
import pl.minecodes.orm.table.TableMetadata;
import pl.minecodes.orm.util.SqlSanitizer;

public class RelationLoader {

  private static final Map<Class<?>, Constructor<?>> constructorCache = new ConcurrentHashMap<>();

  private final FlexOrm orm;
  private final Map<Class<?>, TableMetadata> metadataCache;
  private final Function<Class<?>, TableMetadata> metadataExtractor;

  public RelationLoader(FlexOrm orm, Map<Class<?>, TableMetadata> metadataCache,
      Function<Class<?>, TableMetadata> metadataExtractor) {
    this.orm = orm;
    this.metadataCache = metadataCache;
    this.metadataExtractor = metadataExtractor;
  }

  public <T> void loadRelations(T entity, TableMetadata metadata, Connection connection) {
    for (RelationInfo relation : metadata.relations()) {
      if (relation.fetchType() == FetchType.EAGER) {
        loadRelation(entity, relation, metadata, connection);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T> void loadRelation(T entity, RelationInfo relation, TableMetadata metadata,
      Connection connection) {
    try {
      switch (relation.type()) {
        case ONE_TO_ONE -> loadOneToOne(entity, relation, metadata, connection);
        case MANY_TO_ONE -> loadManyToOne(entity, relation, metadata, connection);
        case ONE_TO_MANY -> loadOneToMany(entity, relation, metadata, connection);
        case MANY_TO_MANY -> loadManyToMany(entity, relation, metadata, connection);
      }
    } catch (Exception e) {
      throw new RuntimeException("Error loading relation " + relation.field().getName(), e);
    }
  }

  private <T> void loadOneToOne(T entity, RelationInfo relation, TableMetadata metadata,
      Connection connection)
      throws Exception {
    Class<?> targetClass = relation.targetEntity();
    TableMetadata targetMetadata = getOrCreateMetadata(targetClass);

    if (relation.isOwning()) {
      String fkColumn = relation.joinColumn().isEmpty()
          ? relation.field().getName() + "_id"
          : relation.joinColumn();

      Object fkValue = getFkValueFromEntity(entity, metadata, fkColumn, connection);
      if (fkValue != null) {
        Object related = findById(targetClass, targetMetadata, fkValue, connection);
        relation.field().set(entity, related);
      }
    } else if (relation.isInverse()) {
      Object entityId = metadata.idField().get(entity);
      String mappedByColumn = relation.mappedBy() + "_id";
      Object related = findByFk(targetClass, targetMetadata, mappedByColumn, entityId, connection);
      relation.field().set(entity, related);
    }
  }

  private <T> void loadManyToOne(T entity, RelationInfo relation, TableMetadata metadata,
      Connection connection)
      throws Exception {
    Class<?> targetClass = relation.targetEntity();
    TableMetadata targetMetadata = getOrCreateMetadata(targetClass);

    String fkColumn = relation.joinColumn().isEmpty()
        ? relation.field().getName() + "_id"
        : relation.joinColumn();

    Object fkValue = getFkValueFromEntity(entity, metadata, fkColumn, connection);
    if (fkValue != null) {
      Object related = findById(targetClass, targetMetadata, fkValue, connection);
      relation.field().set(entity, related);
    }
  }

  private <T> void loadOneToMany(T entity, RelationInfo relation, TableMetadata metadata,
      Connection connection)
      throws Exception {
    Class<?> targetClass = relation.targetEntity();
    TableMetadata targetMetadata = getOrCreateMetadata(targetClass);

    Object entityId = metadata.idField().get(entity);
    String fkColumn = relation.mappedBy().isEmpty()
        ? metadata.tableName() + "_id"
        : relation.mappedBy() + "_id";

    List<?> relatedList = findAllByFk(targetClass, targetMetadata, fkColumn, entityId, connection);

    Field field = relation.field();
    if (field.getType() == List.class) {
      field.set(entity, relatedList);
    } else if (field.getType() == Set.class) {
      field.set(entity, new HashSet<>(relatedList));
    } else if (Collection.class.isAssignableFrom(field.getType())) {
      field.set(entity, relatedList);
    }
  }

  private <T> void loadManyToMany(T entity, RelationInfo relation, TableMetadata metadata,
      Connection connection)
      throws Exception {
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

    List<?> relatedList = findManyToMany(
        targetClass, targetMetadata, joinTable, joinColumn, inverseJoinColumn, entityId,
        connection);

    Field field = relation.field();
    if (field.getType() == List.class) {
      field.set(entity, relatedList);
    } else if (field.getType() == Set.class) {
      field.set(entity, new HashSet<>(relatedList));
    } else if (Collection.class.isAssignableFrom(field.getType())) {
      field.set(entity, relatedList);
    }
  }

  private Object getFkValueFromEntity(Object entity, TableMetadata metadata, String fkColumn,
      Connection connection)
      throws Exception {
    String sanitizedFkColumn = SqlSanitizer.sanitizeColumnName(fkColumn);
    String sanitizedTableName = SqlSanitizer.sanitizeTableName(metadata.tableName());
    String sanitizedIdColumn = SqlSanitizer.sanitizeColumnName(getIdColumnName(metadata));

    String sql = "SELECT " + sanitizedFkColumn + " FROM " + sanitizedTableName + " WHERE "
        + sanitizedIdColumn + " = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setObject(1, metadata.idField().get(entity));
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getObject(1);
        }
      }
    }
    return null;
  }

  private Object findById(Class<?> entityClass, TableMetadata metadata, Object id,
      Connection connection)
      throws Exception {
    String sanitizedTableName = SqlSanitizer.sanitizeTableName(metadata.tableName());
    String sanitizedIdColumn = SqlSanitizer.sanitizeColumnName(getIdColumnName(metadata));

    String sql = "SELECT * FROM " + sanitizedTableName + " WHERE " + sanitizedIdColumn + " = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setObject(1, id);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return mapResultSetToEntity(rs, entityClass, metadata);
        }
      }
    }
    return null;
  }

  private Object findByFk(Class<?> entityClass, TableMetadata metadata, String fkColumn,
      Object fkValue,
      Connection connection) throws Exception {
    String sanitizedTableName = SqlSanitizer.sanitizeTableName(metadata.tableName());
    String sanitizedFkColumn = SqlSanitizer.sanitizeColumnName(fkColumn);

    String sql = "SELECT * FROM " + sanitizedTableName + " WHERE " + sanitizedFkColumn + " = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setObject(1, fkValue);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return mapResultSetToEntity(rs, entityClass, metadata);
        }
      }
    }
    return null;
  }

  private List<Object> findAllByFk(Class<?> entityClass, TableMetadata metadata, String fkColumn,
      Object fkValue,
      Connection connection) throws Exception {
    List<Object> results = new ArrayList<>();
    String sanitizedTableName = SqlSanitizer.sanitizeTableName(metadata.tableName());
    String sanitizedFkColumn = SqlSanitizer.sanitizeColumnName(fkColumn);

    String sql = "SELECT * FROM " + sanitizedTableName + " WHERE " + sanitizedFkColumn + " = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setObject(1, fkValue);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapResultSetToEntity(rs, entityClass, metadata));
        }
      }
    }
    return results;
  }

  private List<Object> findManyToMany(Class<?> entityClass, TableMetadata targetMetadata,
      String joinTable,
      String joinColumn, String inverseJoinColumn, Object entityId, Connection connection)
      throws Exception {
    List<Object> results = new ArrayList<>();

    String sanitizedTargetTable = SqlSanitizer.sanitizeTableName(targetMetadata.tableName());
    String sanitizedJoinTable = SqlSanitizer.sanitizeTableName(joinTable);
    String sanitizedIdColumn = SqlSanitizer.sanitizeColumnName(getIdColumnName(targetMetadata));
    String sanitizedInverseJoinColumn = SqlSanitizer.sanitizeColumnName(inverseJoinColumn);
    String sanitizedJoinColumn = SqlSanitizer.sanitizeColumnName(joinColumn);

    String sql = "SELECT t.* FROM " + sanitizedTargetTable + " t " +
        "INNER JOIN " + sanitizedJoinTable + " j ON t." + sanitizedIdColumn + " = j."
        + sanitizedInverseJoinColumn +
        " WHERE j." + sanitizedJoinColumn + " = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setObject(1, entityId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapResultSetToEntity(rs, entityClass, targetMetadata));
        }
      }
    }
    return results;
  }

  private Object mapResultSetToEntity(ResultSet rs, Class<?> entityClass, TableMetadata metadata)
      throws Exception {
    Object instance = getCachedConstructor(entityClass).newInstance();

    for (var entry : metadata.columnFields().entrySet()) {
      String columnName = entry.getKey();
      Field field = entry.getValue();

      try {
        Object value = rs.getObject(columnName);
        if (value != null) {
          if (field.getType() == boolean.class || field.getType() == Boolean.class) {
            if (value instanceof Integer) {
              value = ((Integer) value) != 0;
            } else if (value instanceof Long) {
              value = ((Long) value) != 0L;
            }
          }
          field.set(instance, value);
        }
      } catch (SQLException ignored) {
      }
    }

    return instance;
  }

  @SuppressWarnings("unchecked")
  private <E> Constructor<E> getCachedConstructor(Class<E> clazz) {
    return (Constructor<E>) constructorCache.computeIfAbsent(clazz, cls -> {
      try {
        Constructor<?> constructor = cls.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor;
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("No default constructor found for " + cls.getName(), e);
      }
    });
  }

  private String getIdColumnName(TableMetadata metadata) {
    return metadata.fieldColumnNames().getOrDefault(
        metadata.idField().getName(),
        metadata.idField().getName()
    );
  }

  private TableMetadata getOrCreateMetadata(Class<?> entityClass) {
    return metadataCache.computeIfAbsent(entityClass, metadataExtractor);
  }
}
