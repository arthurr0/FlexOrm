package pl.minecodes.orm.entity;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import pl.minecodes.orm.query.Query;

public interface EntityRepository<T, ID> {

  void save(T entity);

  void update(T entity);

  void delete(T entity);

  void deleteById(ID id);

  Optional<T> findById(ID id);

  List<T> findAll();

  void beginTransaction();

  void commitTransaction();

  void rollbackTransaction();

  Query<T> query();

  List<T> findByField(String fieldName, Object value);

  List<T> executeQuery(String rawQuery);

  List<T> executeQuery(String rawQuery, Consumer<Exception> errorHandler);

  void executeUpdate(String rawQuery);

  void executeUpdate(String rawQuery, Consumer<Exception> errorHandler);

  <R> R executeRawQuery(String rawQuery, QueryResultMapper<R> mapper);

  <R> R executeRawQuery(String rawQuery, QueryResultMapper<R> mapper, Consumer<Exception> errorHandler);

  interface QueryResultMapper<R> {
    R map(Object resultSet);
  }
}