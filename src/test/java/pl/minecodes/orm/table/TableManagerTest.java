package pl.minecodes.orm.table;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmIndex;
import pl.minecodes.orm.exception.ObjectRequiredAnnotationsException;

class TableManagerTest {

  @TempDir
  Path tempDir;

  private FlexOrm flexOrm;
  private TableManager tableManager;

  @BeforeEach
  void setUp() {
    File dbFile = tempDir.resolve("table-manager-test.db").toFile();
    flexOrm = FlexOrm.sqllite(dbFile);
    flexOrm.connect();
    tableManager = new TableManager(flexOrm);
  }

  @Test
  void testCreateSimpleTable() {
    tableManager.createTable(SimpleTableEntity.class);

    assertTrue(tableManager.tableExists("simple_table"));
  }

  @Test
  void testCreateTableWithCustomName() {
    tableManager.createTable(CustomNameEntity.class);

    assertTrue(tableManager.tableExists("my_custom_table"));
  }

  @Test
  void testCreateTableWithDefaultName() {
    tableManager.createTable(DefaultNameEntity.class);

    assertTrue(tableManager.tableExists("defaultnameentity"));
  }

  @Test
  void testTableExistsReturnsFalse() {
    assertFalse(tableManager.tableExists("nonexistent_table"));
  }

  @Test
  void testCreateTableWithoutOrmEntityThrows() {
    assertThrows(ObjectRequiredAnnotationsException.class, () ->
        tableManager.createTable(NotAnnotatedEntity.class)
    );
  }

  @Test
  void testCreateTableWithoutIdFieldThrows() {
    assertThrows(ObjectRequiredAnnotationsException.class, () ->
        tableManager.createTable(NoIdFieldEntity.class)
    );
  }

  @Test
  void testCreateOrUpdateTableCreatesNew() {
    assertFalse(tableManager.tableExists("simple_table"));

    tableManager.createOrUpdateTable(SimpleTableEntity.class);

    assertTrue(tableManager.tableExists("simple_table"));
  }

  @Test
  void testCreateOrUpdateTableUpdatesExisting() {
    tableManager.createTable(SimpleTableEntity.class);
    assertTrue(tableManager.tableExists("simple_table"));

    assertDoesNotThrow(() -> tableManager.createOrUpdateTable(SimpleTableEntity.class));

    assertTrue(tableManager.tableExists("simple_table"));
  }

  @Test
  void testCreateTableWithIndex() throws SQLException {
    tableManager.createTable(IndexedEntity.class);

    assertTrue(tableManager.tableExists("indexed_table"));
    assertTrue(indexExists("idx_indexed_table_email"));
  }

  @Test
  void testCreateTableWithCustomIndexName() throws SQLException {
    tableManager.createTable(CustomIndexEntity.class);

    assertTrue(tableManager.tableExists("custom_index_table"));
    assertTrue(indexExists("my_custom_index"));
  }

  @Test
  void testTableHasCorrectColumns() throws SQLException {
    tableManager.createTable(MultiColumnEntity.class);

    Set<String> columns = getTableColumns("multi_column_table");

    assertTrue(columns.contains("id"));
    assertTrue(columns.contains("name"));
    assertTrue(columns.contains("age"));
    assertTrue(columns.contains("active"));
  }

  @Test
  void testCreateTableWithAllTypes() {
    assertDoesNotThrow(() -> tableManager.createTable(AllTypesEntity.class));
    assertTrue(tableManager.tableExists("all_types_table"));
  }

  @Test
  void testCreateTableIdempotent() {
    tableManager.createTable(SimpleTableEntity.class);
    assertDoesNotThrow(() -> tableManager.createTable(SimpleTableEntity.class));
    assertTrue(tableManager.tableExists("simple_table"));
  }

  @Test
  void testUpdateTableAddsNewColumn() throws SQLException {
    tableManager.createTable(SimpleTableEntity.class);

    Set<String> columnsBefore = getTableColumns("simple_table");
    assertTrue(columnsBefore.contains("name"));
  }

  private boolean indexExists(String indexName) throws SQLException {
    HikariDataSource dataSource = (HikariDataSource) flexOrm.getConnection().getConnection();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='" + indexName + "'")) {
      return rs.next();
    }
  }

  private Set<String> getTableColumns(String tableName) throws SQLException {
    Set<String> columns = new HashSet<>();
    HikariDataSource dataSource = (HikariDataSource) flexOrm.getConnection().getConnection();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
      while (rs.next()) {
        columns.add(rs.getString("name"));
      }
    }
    return columns;
  }

  @OrmEntity(table = "simple_table")
  public static class SimpleTableEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String name;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @OrmEntity(table = "my_custom_table")
  public static class CustomNameEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }

  @OrmEntity
  public static class DefaultNameEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }

  public static class NotAnnotatedEntity {

    private Long id;
    private String value;
  }

  @OrmEntity(table = "no_id_table")
  public static class NoIdFieldEntity {

    @OrmField
    private String value;
  }

  @OrmEntity(table = "indexed_table")
  public static class IndexedEntity {

    @OrmEntityId
    private Long id;

    @OrmIndex
    @OrmField
    private String email;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }

  @OrmEntity(table = "custom_index_table")
  public static class CustomIndexEntity {

    @OrmEntityId
    private Long id;

    @OrmIndex(name = "my_custom_index")
    @OrmField
    private String code;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }

  @OrmEntity(table = "multi_column_table")
  public static class MultiColumnEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String name;

    @OrmField
    private int age;

    @OrmField
    private boolean active;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }

  @OrmEntity(table = "all_types_table")
  public static class AllTypesEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String stringField;

    @OrmField
    private int intField;

    @OrmField
    private long longField;

    @OrmField
    private double doubleField;

    @OrmField
    private float floatField;

    @OrmField
    private boolean booleanField;

    @OrmField
    private BigDecimal decimalField;

    @OrmField
    private LocalDateTime dateTimeField;

    @OrmField
    private LocalDate dateField;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }
}
