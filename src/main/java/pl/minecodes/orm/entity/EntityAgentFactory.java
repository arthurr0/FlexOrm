package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class EntityAgentFactory {

  public static <T, ID> EntityAgent<T, ID> createEntityAgent(FlexOrm flexOrm, Class<T> entityClass) {
    return switch (flexOrm.getDatabaseType()) {
      case MYSQL -> new MySQLEntityAgent<>(flexOrm, entityClass);
      case SQLLITE -> new SQLiteEntityAgent<>(flexOrm, entityClass);
      case MONGODB -> new MongoEntityAgent<>(flexOrm, entityClass);
      case JSON -> throw new UnsupportedOperationException("Not supported yet.");
    };
  }
}