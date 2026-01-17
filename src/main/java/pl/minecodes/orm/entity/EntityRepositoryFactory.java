package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class EntityRepositoryFactory {

  public static <T, ID> EntityRepository<T, ID> createEntityRepository(FlexOrm flexOrm,
      Class<T> entityClass) {
    return switch (flexOrm.getDatabaseType()) {
      case MYSQL -> new MySQLEntityRepository<>(flexOrm, entityClass);
      case SQLLITE -> new SQLiteEntityRepository<>(flexOrm, entityClass);
      case MONGODB -> new MongoEntityRepository<>(flexOrm, entityClass);
    };
  }
}