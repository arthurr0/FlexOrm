package pl.minecodes.orm.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;

class TypeConversionTest {

  @TempDir
  Path tempDir;

  private FlexOrm flexOrm;

  @BeforeEach
  void setUp() {
    File dbFile = tempDir.resolve("type-conversion-test.db").toFile();
    flexOrm = FlexOrm.sqllite(dbFile);
    flexOrm.connect();
  }

  @Test
  void testStringType() {
    EntityRepository<StringEntity, Long> agent = flexOrm.getEntityRepository(StringEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS string_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)");

    StringEntity entity = new StringEntity();
    entity.setValue("Test String Value");
    agent.save(entity);

    Optional<StringEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals("Test String Value", found.get().getValue());
  }

  @Test
  void testIntegerType() {
    EntityRepository<IntegerEntity, Long> agent = flexOrm.getEntityRepository(IntegerEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS integer_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    IntegerEntity entity = new IntegerEntity();
    entity.setValue(42);
    agent.save(entity);

    Optional<IntegerEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(42, found.get().getValue());
  }

  @Test
  void testLongType() {
    EntityRepository<LongEntity, Long> agent = flexOrm.getEntityRepository(LongEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS long_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    LongEntity entity = new LongEntity();
    entity.setValue(9999999999L);
    agent.save(entity);

    Optional<LongEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(9999999999L, found.get().getValue());
  }

  @Test
  void testDoubleType() {
    EntityRepository<DoubleEntity, Long> agent = flexOrm.getEntityRepository(DoubleEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS double_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value REAL)");

    DoubleEntity entity = new DoubleEntity();
    entity.setValue(3.14159);
    agent.save(entity);

    Optional<DoubleEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(3.14159, found.get().getValue(), 0.00001);
  }

  @Test
  void testFloatType() {
    EntityRepository<FloatEntity, Long> agent = flexOrm.getEntityRepository(FloatEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS float_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value REAL)");

    FloatEntity entity = new FloatEntity();
    entity.setValue(2.71828f);
    agent.save(entity);

    Optional<FloatEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(2.71828f, found.get().getValue(), 0.0001f);
  }

  @Test
  void testBooleanTrueType() {
    EntityRepository<BooleanEntity, Long> agent = flexOrm.getEntityRepository(BooleanEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS boolean_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    BooleanEntity entity = new BooleanEntity();
    entity.setValue(true);
    agent.save(entity);

    Optional<BooleanEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertTrue(found.get().isValue());
  }

  @Test
  void testBooleanFalseType() {
    EntityRepository<BooleanEntity, Long> agent = flexOrm.getEntityRepository(BooleanEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS boolean_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    BooleanEntity entity = new BooleanEntity();
    entity.setValue(false);
    agent.save(entity);

    Optional<BooleanEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertFalse(found.get().isValue());
  }

  @Test
  void testPrimitiveIntType() {
    EntityRepository<PrimitiveIntEntity, Long> agent = flexOrm.getEntityRepository(
        PrimitiveIntEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS prim_int_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    PrimitiveIntEntity entity = new PrimitiveIntEntity();
    entity.setValue(100);
    agent.save(entity);

    Optional<PrimitiveIntEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(100, found.get().getValue());
  }

  @Test
  void testPrimitiveBooleanType() {
    EntityRepository<PrimitiveBooleanEntity, Long> agent = flexOrm.getEntityRepository(
        PrimitiveBooleanEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS prim_bool_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    PrimitiveBooleanEntity entity = new PrimitiveBooleanEntity();
    entity.setValue(true);
    agent.save(entity);

    Optional<PrimitiveBooleanEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertTrue(found.get().isValue());
  }

  @Test
  void testNullValue() {
    EntityRepository<NullableEntity, Long> agent = flexOrm.getEntityRepository(
        NullableEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS nullable_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)");

    NullableEntity entity = new NullableEntity();
    entity.setValue(null);
    agent.save(entity);

    Optional<NullableEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertNull(found.get().getValue());
  }

  @Test
  void testEmptyString() {
    EntityRepository<StringEntity, Long> agent = flexOrm.getEntityRepository(StringEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS string_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)");

    StringEntity entity = new StringEntity();
    entity.setValue("");
    agent.save(entity);

    Optional<StringEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals("", found.get().getValue());
  }

  @Test
  void testZeroInteger() {
    EntityRepository<IntegerEntity, Long> agent = flexOrm.getEntityRepository(IntegerEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS integer_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    IntegerEntity entity = new IntegerEntity();
    entity.setValue(0);
    agent.save(entity);

    Optional<IntegerEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(0, found.get().getValue());
  }

  @Test
  void testNegativeInteger() {
    EntityRepository<IntegerEntity, Long> agent = flexOrm.getEntityRepository(IntegerEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS integer_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    IntegerEntity entity = new IntegerEntity();
    entity.setValue(-999);
    agent.save(entity);

    Optional<IntegerEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(-999, found.get().getValue());
  }

  @Test
  void testMultipleTypesInEntity() {
    EntityRepository<MultiTypeEntity, Long> agent = flexOrm.getEntityRepository(
        MultiTypeEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS multi_type_test (id INTEGER PRIMARY KEY AUTOINCREMENT, stringValue TEXT, intValue INTEGER, doubleValue REAL, boolValue INTEGER)");

    MultiTypeEntity entity = new MultiTypeEntity();
    entity.setStringValue("Test");
    entity.setIntValue(42);
    entity.setDoubleValue(3.14);
    entity.setBoolValue(true);
    agent.save(entity);

    Optional<MultiTypeEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals("Test", found.get().getStringValue());
    assertEquals(42, found.get().getIntValue());
    assertEquals(3.14, found.get().getDoubleValue(), 0.01);
    assertTrue(found.get().isBoolValue());
  }

  @Test
  void testMaxIntegerValue() {
    EntityRepository<IntegerEntity, Long> agent = flexOrm.getEntityRepository(IntegerEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS integer_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    IntegerEntity entity = new IntegerEntity();
    entity.setValue(Integer.MAX_VALUE);
    agent.save(entity);

    Optional<IntegerEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(Integer.MAX_VALUE, found.get().getValue());
  }

  @Test
  void testMinIntegerValue() {
    EntityRepository<IntegerEntity, Long> agent = flexOrm.getEntityRepository(IntegerEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS integer_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value INTEGER)");

    IntegerEntity entity = new IntegerEntity();
    entity.setValue(Integer.MIN_VALUE);
    agent.save(entity);

    Optional<IntegerEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals(Integer.MIN_VALUE, found.get().getValue());
  }

  @Test
  void testSpecialCharactersInString() {
    EntityRepository<StringEntity, Long> agent = flexOrm.getEntityRepository(StringEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS string_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)");

    StringEntity entity = new StringEntity();
    entity.setValue("Special: ' \" \\ \n \t");
    agent.save(entity);

    Optional<StringEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals("Special: ' \" \\ \n \t", found.get().getValue());
  }

  @Test
  void testUnicodeInString() {
    EntityRepository<StringEntity, Long> agent = flexOrm.getEntityRepository(StringEntity.class);
    agent.executeUpdate(
        "CREATE TABLE IF NOT EXISTS string_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)");

    StringEntity entity = new StringEntity();
    entity.setValue("Unicode: zażółć gęślą jaźń 日本語 emoji 😀");
    agent.save(entity);

    Optional<StringEntity> found = agent.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals("Unicode: zażółć gęślą jaźń 日本語 emoji 😀", found.get().getValue());
  }

  @OrmEntity(table = "string_test")
  public static class StringEntity {

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

  @OrmEntity(table = "integer_test")
  public static class IntegerEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private Integer value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public Integer getValue() {
      return value;
    }

    public void setValue(Integer value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "long_test")
  public static class LongEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private Long value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public Long getValue() {
      return value;
    }

    public void setValue(Long value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "double_test")
  public static class DoubleEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private Double value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public Double getValue() {
      return value;
    }

    public void setValue(Double value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "float_test")
  public static class FloatEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private Float value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public Float getValue() {
      return value;
    }

    public void setValue(Float value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "boolean_test")
  public static class BooleanEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private Boolean value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public Boolean isValue() {
      return value;
    }

    public void setValue(Boolean value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "prim_int_test")
  public static class PrimitiveIntEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private int value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public int getValue() {
      return value;
    }

    public void setValue(int value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "prim_bool_test")
  public static class PrimitiveBooleanEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private boolean value;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public boolean isValue() {
      return value;
    }

    public void setValue(boolean value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "nullable_test")
  public static class NullableEntity {

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

  @OrmEntity(table = "multi_type_test")
  public static class MultiTypeEntity {

    @OrmEntityId
    private Long id;
    @OrmField
    private String stringValue;
    @OrmField
    private int intValue;
    @OrmField
    private double doubleValue;
    @OrmField
    private boolean boolValue;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getStringValue() {
      return stringValue;
    }

    public void setStringValue(String stringValue) {
      this.stringValue = stringValue;
    }

    public int getIntValue() {
      return intValue;
    }

    public void setIntValue(int intValue) {
      this.intValue = intValue;
    }

    public double getDoubleValue() {
      return doubleValue;
    }

    public void setDoubleValue(double doubleValue) {
      this.doubleValue = doubleValue;
    }

    public boolean isBoolValue() {
      return boolValue;
    }

    public void setBoolValue(boolean boolValue) {
      this.boolValue = boolValue;
    }
  }
}
