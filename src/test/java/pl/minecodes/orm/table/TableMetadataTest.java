package pl.minecodes.orm.table;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmManyToOne;
import pl.minecodes.orm.annotation.OrmOneToMany;
import pl.minecodes.orm.annotation.OrmTransient;
import pl.minecodes.orm.entity.EntityRepository;
import pl.minecodes.orm.exception.ObjectRequiredAnnotationsException;

class TableMetadataTest {

  @TempDir
  Path tempDir;

  private FlexOrm flexOrm;

  @BeforeEach
  void setUp() {
    File dbFile = tempDir.resolve("metadata-test.db").toFile();
    flexOrm = FlexOrm.sqllite(dbFile);
    flexOrm.connect();
  }

  @Test
  void testBasicMetadataExtraction() {
    EntityRepository<SimpleEntity, Long> agent = flexOrm.getEntityRepository(SimpleEntity.class);

    assertDoesNotThrow(() -> agent.query());
  }

  @Test
  void testTableNameFromAnnotation() {
    EntityRepository<CustomTableNameEntity, Long> agent = flexOrm.getEntityRepository(
        CustomTableNameEntity.class);

    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS custom_table (id INTEGER PRIMARY KEY, value TEXT)");

    CustomTableNameEntity entity = new CustomTableNameEntity();
    entity.setValue("test");
    agent.save(entity);

    List<CustomTableNameEntity> found = agent.findAll();
    assertEquals(1, found.size());
  }

  @Test
  void testDefaultTableName() {
    EntityRepository<DefaultTableEntity, Long> agent = flexOrm.getEntityRepository(
        DefaultTableEntity.class);

    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS defaulttableentity (id INTEGER PRIMARY KEY, name TEXT)");

    DefaultTableEntity entity = new DefaultTableEntity();
    entity.setName("test");
    agent.save(entity);

    assertNotNull(entity.getId());
  }

  @Test
  void testCustomColumnName() {
    EntityRepository<CustomColumnEntity, Long> agent = flexOrm.getEntityRepository(
        CustomColumnEntity.class);

    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS custom_column_test (id INTEGER PRIMARY KEY, user_name TEXT)");

    CustomColumnEntity entity = new CustomColumnEntity();
    entity.setName("TestUser");
    agent.save(entity);

    List<CustomColumnEntity> found = agent.findByField("user_name", "TestUser");
    assertEquals(1, found.size());
  }

  @Test
  void testTransientFieldExcluded() {
    EntityRepository<TransientFieldEntity, Long> agent = flexOrm.getEntityRepository(
        TransientFieldEntity.class);

    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS transient_test (id INTEGER PRIMARY KEY, name TEXT)");

    TransientFieldEntity entity = new TransientFieldEntity();
    entity.setName("Test");
    entity.setTransientValue("This should not be saved");
    agent.save(entity);

    TransientFieldEntity found = agent.findById(entity.getId()).orElseThrow();
    assertEquals("Test", found.getName());
    assertNull(found.getTransientValue());
  }

  @Test
  void testEntityWithoutOrmEntityAnnotation() {
    assertThrows(RuntimeException.class, () -> {
      EntityRepository<NoAnnotationEntity, Long> agent = flexOrm.getEntityRepository(
          NoAnnotationEntity.class);
      agent.query();
    });
  }

  @Test
  void testEntityWithoutIdField() {
    assertThrows(ObjectRequiredAnnotationsException.class, () -> {
      EntityRepository<NoIdEntity, Long> agent = flexOrm.getEntityRepository(NoIdEntity.class);
      agent.query();
    });
  }

  @Test
  void testRelationDetection() {
    EntityRepository<EntityWithRelations, Long> agent = flexOrm.getEntityRepository(
        EntityWithRelations.class);

    assertDoesNotThrow(() -> agent.query());
  }

  @Test
  void testTableMetadataRecord() throws NoSuchFieldException {
    Field idField = SimpleEntity.class.getDeclaredField("id");
    Map<String, Field> columnFields = new HashMap<>();
    columnFields.put("name", SimpleEntity.class.getDeclaredField("name"));

    Map<String, String> fieldColumnNames = new HashMap<>();
    fieldColumnNames.put("name", "name");

    TableMetadata metadata = new TableMetadata("simple", idField, columnFields, fieldColumnNames);

    assertEquals("simple", metadata.tableName());
    assertEquals(idField, metadata.idField());
    assertEquals(1, metadata.columnFields().size());
    assertTrue(metadata.relations().isEmpty());
  }

  @Test
  void testTableMetadataWithRelations() throws NoSuchFieldException {
    Field idField = SimpleEntity.class.getDeclaredField("id");
    Map<String, Field> columnFields = new HashMap<>();
    Map<String, String> fieldColumnNames = new HashMap<>();

    TableMetadata metadata = new TableMetadata("test", idField, columnFields, fieldColumnNames,
        List.of());

    assertEquals("test", metadata.tableName());
    assertTrue(metadata.relations().isEmpty());
  }

  @OrmEntity(table = "simple")
  public static class SimpleEntity {

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

  @OrmEntity(table = "custom_table")
  public static class CustomTableNameEntity {

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

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  @OrmEntity
  public static class DefaultTableEntity {

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

  @OrmEntity(table = "custom_column_test")
  public static class CustomColumnEntity {

    @OrmEntityId
    private Long id;

    @OrmField(name = "user_name")
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

  @OrmEntity(table = "transient_test")
  public static class TransientFieldEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String name;

    @OrmTransient
    private String transientValue;

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

    public String getTransientValue() {
      return transientValue;
    }

    public void setTransientValue(String transientValue) {
      this.transientValue = transientValue;
    }
  }

  public static class NoAnnotationEntity {

    private Long id;
    private String name;
  }

  @OrmEntity(table = "no_id_test")
  public static class NoIdEntity {

    @OrmField
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @OrmEntity(table = "entity_with_relations")
  public static class EntityWithRelations {

    @OrmEntityId
    private Long id;

    @OrmField
    private String name;

    @OrmManyToOne(targetEntity = SimpleEntity.class, joinColumn = "simple_id")
    private SimpleEntity parent;

    @OrmOneToMany(targetEntity = SimpleEntity.class, mappedBy = "parent")
    private List<SimpleEntity> children;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }
}
