package pl.minecodes.orm.entity;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.table.TableMetadata;

public class MongoEntityRepository<T, ID> extends BaseEntityRepository<T, ID> {

  private ClientSession activeSession;

  public MongoEntityRepository(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
  }

  @Override
  protected Object executeRawQueryInternal(String rawQuery) {
    MongoDatabase database = (MongoDatabase) orm.getConnection().getConnection();
    TableMetadata metadata = getTableMetadata(entityClass);

    Document queryDocument = Document.parse(rawQuery);

    return database.getCollection(metadata.tableName()).find(queryDocument);
  }

  @Override
  protected void insert(T entity) {
    TableMetadata metadata = getTableMetadata(entityClass);
    insertIntoCollection(entity, metadata);
  }

  @Override
  public void update(T entity) {
    validateEntity(entity);
    TableMetadata metadata = getTableMetadata(entityClass);
    updateInCollection(entity, metadata);
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
    deleteFromCollection(id, metadata);
  }

  @Override
  public Optional<T> findById(ID id) {
    if (id == null) {
      return Optional.empty();
    }
    TableMetadata metadata = getTableMetadata(entityClass);
    return findByIdInCollection(id, metadata);
  }

  @Override
  public List<T> findAll() {
    TableMetadata metadata = getTableMetadata(entityClass);
    return findAllInCollection(metadata);
  }

  @Override
  protected void beginTransactionInternal() {
    MongoClient mongoClient = (MongoClient) orm.getConnection().getConnection();
    activeSession = mongoClient.startSession();
    activeSession.startTransaction();
  }

  @Override
  protected void commitTransactionInternal() {
    if (activeSession != null) {
      activeSession.commitTransaction();
      activeSession.close();
      activeSession = null;
    }
  }

  @Override
  protected void rollbackTransactionInternal() {
    if (activeSession != null) {
      activeSession.abortTransaction();
      activeSession.close();
      activeSession = null;
    }
  }

  protected MongoDatabase getDatabase() {
    return (MongoDatabase) orm.getConnection().getConnection();
  }

  protected boolean existsById(ID id) {
    TableMetadata metadata = getTableMetadata(entityClass);
    MongoCollection<Document> collection = getDatabase().getCollection(metadata.tableName());

    String idColumnName = getColumnNameForField(metadata.idField(), metadata);
    Document query = new Document(idColumnName, id);

    if (activeSession != null) {
      return collection.find(activeSession, query).first() != null;
    } else {
      return collection.find(query).first() != null;
    }
  }

  protected void insertIntoCollection(T entity, TableMetadata metadata) {
    MongoCollection<Document> collection = getDatabase().getCollection(metadata.tableName());
    Document document = new Document();

    try {
      for (var entry : metadata.columnFields().entrySet()) {
        Object value = entry.getValue().get(entity);
        if (value != null) {
          document.append(entry.getKey(), value);
        }
      }

      if (activeSession != null) {
        collection.insertOne(activeSession, document);
      } else {
        collection.insertOne(document);
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Error creating MongoDB document", e);
    }
  }

  protected void updateInCollection(T entity, TableMetadata metadata) {
    MongoCollection<Document> collection = getDatabase().getCollection(metadata.tableName());
    Document document = new Document();
    Object id = null;

    try {
      for (var entry : metadata.columnFields().entrySet()) {
        Object value = entry.getValue().get(entity);
        if (value != null) {
          document.append(entry.getKey(), value);
          if (entry.getValue().equals(metadata.idField())) {
            id = value;
          }
        }
      }

      if (id != null) {
        String idColumnName = getColumnNameForField(metadata.idField(), metadata);
        Document query = new Document(idColumnName, id);

        if (activeSession != null) {
          collection.replaceOne(activeSession, query, document);
        } else {
          collection.replaceOne(query, document);
        }
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Error updating MongoDB document", e);
    }
  }

  protected void deleteFromCollection(ID id, TableMetadata metadata) {
    MongoCollection<Document> collection = getDatabase().getCollection(metadata.tableName());
    String idColumnName = getColumnNameForField(metadata.idField(), metadata);
    Document query = new Document(idColumnName, id);

    if (activeSession != null) {
      collection.deleteOne(activeSession, query);
    } else {
      collection.deleteOne(query);
    }
  }

  protected Optional<T> findByIdInCollection(ID id, TableMetadata metadata) {
    MongoCollection<Document> collection = getDatabase().getCollection(metadata.tableName());
    String idColumnName = getColumnNameForField(metadata.idField(), metadata);
    Document query = new Document(idColumnName, id);

    Document result;
    if (activeSession != null) {
      result = collection.find(activeSession, query).first();
    } else {
      result = collection.find(query).first();
    }

    if (result == null) {
      return Optional.empty();
    }

    try {
      T instance = entityClass.getDeclaredConstructor().newInstance();

      for (var entry : metadata.columnFields().entrySet()) {
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

  protected List<T> findAllInCollection(TableMetadata metadata) {
    List<T> results = new ArrayList<>();
    MongoCollection<Document> collection = getDatabase().getCollection(metadata.tableName());

    Iterable<Document> documents;
    if (activeSession != null) {
      documents = collection.find(activeSession);
    } else {
      documents = collection.find();
    }

    for (Document document : documents) {
      try {
        T instance = entityClass.getDeclaredConstructor().newInstance();

        for (var entry : metadata.columnFields().entrySet()) {
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
}