package pl.minecodes.orm.table;

import java.lang.reflect.Field;
import java.util.Map;

public record TableMetadata(String tableName, Field idField, Map<String, Field> columnFields, Map<String, String> fieldColumnNames) {
}