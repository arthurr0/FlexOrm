package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class MySQLEntityManager<T, ID> extends RelationalEntityManager<T, ID> {

  public MySQLEntityManager(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
  }

}