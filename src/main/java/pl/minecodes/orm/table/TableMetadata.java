package pl.minecodes.orm.table;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import pl.minecodes.orm.relation.RelationInfo;

public record TableMetadata(
    String tableName,
    Field idField,
    Map<String, Field> columnFields,
    Map<String, String> fieldColumnNames,
    List<RelationInfo> relations
) {

  public TableMetadata(String tableName, Field idField, Map<String, Field> columnFields,
      Map<String, String> fieldColumnNames) {
    this(tableName, idField, columnFields, fieldColumnNames, Collections.emptyList());
  }
}