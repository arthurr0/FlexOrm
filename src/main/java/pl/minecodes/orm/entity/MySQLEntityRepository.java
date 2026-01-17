package pl.minecodes.orm.entity;

import pl.minecodes.orm.FlexOrm;

public class MySQLEntityRepository<T, ID> extends RelationalEntityRepository<T, ID> {

  public MySQLEntityRepository(FlexOrm orm, Class<T> entityClass) {
    super(orm, entityClass);
  }

}