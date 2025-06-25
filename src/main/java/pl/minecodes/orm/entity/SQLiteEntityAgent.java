package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class SQLiteEntityAgent<T, ID> extends RelationalEntityAgent<T, ID> {

  public SQLiteEntityAgent(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
  }

}