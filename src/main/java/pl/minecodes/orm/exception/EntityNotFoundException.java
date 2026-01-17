package pl.minecodes.orm.exception;

public class EntityNotFoundException extends OrmException {
  public EntityNotFoundException(String message) {
    super(message);
  }

  public EntityNotFoundException(Class<?> entityClass, Object id) {
    super("Entity " + entityClass.getSimpleName() + " with id " + id + " not found");
  }
}
