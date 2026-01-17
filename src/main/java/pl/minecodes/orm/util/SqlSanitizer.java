package pl.minecodes.orm.util;

import java.util.regex.Pattern;

public final class SqlSanitizer {

  private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
  private static final int MAX_IDENTIFIER_LENGTH = 128;

  private SqlSanitizer() {
  }

  public static String sanitizeIdentifier(String identifier) {
    if (identifier == null || identifier.isEmpty()) {
      throw new IllegalArgumentException("SQL identifier cannot be null or empty");
    }

    if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException(
          "SQL identifier exceeds maximum length of " + MAX_IDENTIFIER_LENGTH);
    }

    if (!VALID_IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
          "Invalid SQL identifier: '" + identifier + "'. Only alphanumeric characters and underscores are allowed, must start with letter or underscore.");
    }

    return identifier;
  }

  public static String sanitizeTableName(String tableName) {
    return sanitizeIdentifier(tableName);
  }

  public static String sanitizeColumnName(String columnName) {
    return sanitizeIdentifier(columnName);
  }

  public static String escapeStringValue(String value) {
    if (value == null) {
      return null;
    }
    return value
        .replace("\\", "\\\\")
        .replace("'", "''")
        .replace("\0", "\\0")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  public static boolean isValidIdentifier(String identifier) {
    if (identifier == null || identifier.isEmpty()) {
      return false;
    }
    if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
      return false;
    }
    return VALID_IDENTIFIER.matcher(identifier).matches();
  }
}
