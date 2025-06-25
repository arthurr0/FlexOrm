package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class EntityManagerFactory {

  public static <T, ID> EntityManager<T, ID> createEntityManager(FlexOrm flexOrm, Class<T> entityClass) {
    return switch (flexOrm.getDatabaseType()) {
      case MYSQL -> new MySQLEntityManager<>(flexOrm, entityClass);
      case SQLLITE -> new SQLiteEntityManager<>(flexOrm, entityClass);
      case MONGODB -> new MongoEntityManager<>(flexOrm, entityClass);
      case JSON -> new JsonEntityManager<>(flexOrm, entityClass);
    };
  }
}