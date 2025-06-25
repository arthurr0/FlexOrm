package pl.minecodes.orm.entity;

import java.util.List;
import java.util.function.Consumer;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.exception.ObjectIsNullException;
import pl.minecodes.orm.exception.ObjectRequiredAnnotationsException;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import pl.minecodes.orm.query.Operator;
import pl.minecodes.orm.query.Query;
import pl.minecodes.orm.table.TableMetadata;

public abstract class BaseEntityAgent<T, ID> implements EntityAgent<T, ID> {

  protected final Class<T> entityClass;
  protected final FlexOrm orm;
  protected final Map<Class<?>, TableMetadata> metadataCache = new HashMap<>();
  protected boolean inTransaction = false;

  protected BaseEntityAgent(FlexOrm orm, Class<T> entityClass) {
    this.orm = orm;
    this.entityClass = entityClass;
  }

  @Override
  public void save(T entity) {
    System.out.println("test");
    validateEntity(entity);

    TableMetadata metadata = getTableMetadata(entityClass);
    Object id = getEntityId(entity, metadata);

    boolean exists = false;
    if (id != null) {
      exists = existsById((ID) id);
    }

    if (exists) {
      update(entity);
    } else {
      insert(entity);
    }
    System.out.println("s");
  }

  @Override
  public Query<T> query() {
    TableMetadata metadata = getTableMetadata(entityClass);
    return new Query<>(orm, entityClass, metadata);
  }

  @Override
  public List<T> findByField(String fieldName, Object value) {
    TableMetadata metadata = getTableMetadata(entityClass);
    return query()
        .where(fieldName, Operator.EQUALS, value)
        .execute();
  }

  @Override
  public List<T> executeQuery(String rawQuery) {
    return executeQuery(rawQuery, null);
  }

  @Override
  public List<T> executeQuery(String rawQuery, Consumer<Exception> errorHandler) {
    try {
      TableMetadata metadata = getTableMetadata(entityClass);
      return query()
          .raw(rawQuery)
          .execute();
    } catch (Exception e) {
      if (errorHandler != null) {
        errorHandler.accept(e);
        return List.of();
      }
      throw e;
    }
  }

  @Override
  public void executeUpdate(String rawQuery) {
    executeUpdate(rawQuery, null);
  }

  @Override
  public void executeUpdate(String rawQuery, Consumer<Exception> errorHandler) {
    TableMetadata metadata = getTableMetadata(entityClass);
    query()
        .raw(rawQuery)
        .executeRawUpdate(rawQuery, errorHandler);
  }

  @Override
  public <R> R executeRawQuery(String rawQuery, QueryResultMapper<R> mapper) {
    return executeRawQuery(rawQuery, mapper, null);
  }

  @Override
  public <R> R executeRawQuery(String rawQuery, QueryResultMapper<R> mapper, Consumer<Exception> errorHandler) {
    try {
      Object result = executeRawQueryInternal(rawQuery);
      return mapper.map(result);
    } catch (Exception e) {
      if (errorHandler != null) {
        errorHandler.accept(e);
        return null;
      }
      throw e;
    }
  }

  protected abstract Object executeRawQueryInternal(String rawQuery);

  protected abstract void insert(T entity);

  protected void validateEntity(T entity) {
    if (entity == null) {
      throw new ObjectIsNullException("Provided entity is null!");
    }

    validateClass(entityClass);
  }

  protected void validateClass(Class<?> objectClass) {
    if (objectClass == null) {
      throw new ObjectIsNullException("Provided entity class is null!");
    }

    if (!objectClass.isAnnotationPresent(OrmEntity.class)) {
      throw new ObjectRequiredAnnotationsException("Provided entity class is not annotated with @OrmEntity!");
    }
  }

  protected TableMetadata getTableMetadata(Class<?> objectClass) {
    return metadataCache.computeIfAbsent(objectClass, this::extractTableMetadata);
  }

  protected TableMetadata extractTableMetadata(Class<?> objectClass) {
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

  protected ID getEntityId(T entity, TableMetadata metadata) {
    try {
      return (ID) metadata.idField().get(entity);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Could not access ID field", e);
    }
  }

  protected String getColumnNameForField(Field field, TableMetadata metadata) {
    return metadata.fieldColumnNames().getOrDefault(field.getName(), field.getName());
  }

  @Override
  public void beginTransaction() {
    if (inTransaction) {
      throw new IllegalStateException("Transaction already started");
    }
    inTransaction = true;
    beginTransactionInternal();
  }

  @Override
  public void commitTransaction() {
    if (!inTransaction) {
      throw new IllegalStateException("No transaction to commit");
    }
    commitTransactionInternal();
    inTransaction = false;
  }

  @Override
  public void rollbackTransaction() {
    if (!inTransaction) {
      throw new IllegalStateException("No transaction to rollback");
    }
    rollbackTransactionInternal();
    inTransaction = false;
  }

  protected abstract void beginTransactionInternal();
  protected abstract void commitTransactionInternal();
  protected abstract void rollbackTransactionInternal();

  protected abstract boolean existsById(ID id);
}