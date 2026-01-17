package pl.minecodes.orm.relation;

import pl.minecodes.orm.annotation.FetchType;

import java.lang.reflect.Field;

public record RelationInfo(
    Field field,
    RelationType type,
    Class<?> targetEntity,
    String joinColumn,
    String mappedBy,
    String joinTable,
    String inverseJoinColumn,
    FetchType fetchType,
    boolean cascade
) {
  public boolean isOwning() {
    return joinColumn != null && !joinColumn.isEmpty();
  }

  public boolean isInverse() {
    return mappedBy != null && !mappedBy.isEmpty();
  }
}
