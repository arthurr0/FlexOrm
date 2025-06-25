package pl.minecodes.orm.query;

import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.table.TableMetadata;

public class QueryBuilder<T> {

  private final FlexOrm orm;
  private final Class<T> entityClass;
  private final TableMetadata metadata;

  public QueryBuilder(FlexOrm orm, Class<T> entityClass, TableMetadata metadata) {
    this.orm = orm;
    this.entityClass = entityClass;
    this.metadata = metadata;
  }

  public Query<T> build() {
    return new Query<>(orm, entityClass, metadata);
  }
}