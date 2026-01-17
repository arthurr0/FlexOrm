package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class SQLiteEntityRepository<T, ID> extends RelationalEntityRepository<T, ID> {

  public SQLiteEntityRepository(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
  }

}