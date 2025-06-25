package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class MySQLEntityAgent<T, ID> extends RelationalEntityAgent<T, ID> {

  public MySQLEntityAgent(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
  }

}