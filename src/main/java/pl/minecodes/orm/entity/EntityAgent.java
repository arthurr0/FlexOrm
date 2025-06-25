package pl.minecodes.orm.entity;

import java.util.List;
import java.util.Optional;

public interface EntityManager<T, ID> {

  void save(T entity);

  void update(T entity);

  void delete(T entity);

  void deleteById(ID id);

  Optional<T> findById(ID id);

  List<T> findAll();

  void beginTransaction();

  void commitTransaction();

  void rollbackTransaction();
}