package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class SQLiteEntityManager<T, ID> extends RelationalEntityManager<T, ID> {

  public SQLiteEntityManager(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
  }

}