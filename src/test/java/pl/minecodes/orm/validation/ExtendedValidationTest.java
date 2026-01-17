package pl.minecodes.orm.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmNotNull;
import pl.minecodes.orm.exception.ValidationException;

class ExtendedValidationTest {

  @Test
  void testValidEntityWithAllFields() {
    FullEntity entity = new FullEntity();
    entity.setId(1L);
    entity.setName("Valid Name");
    entity.setEmail("test@example.com");
    entity.setAge(25);
    entity.setBalance(new BigDecimal("100.50"));

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testMultipleNotNullViolations() {
    MultipleNotNullEntity entity = new MultipleNotNullEntity();
    entity.setId(1L);

    ValidationException ex = assertThrows(ValidationException.class,
        () -> EntityValidator.validate(entity));

    assertTrue(ex.getMessage().contains("firstName"));
    assertTrue(ex.getMessage().contains("lastName"));
  }

  @Test
  void testStringLengthExactlyAtLimit() {
    LengthEntity entity = new LengthEntity();
    entity.setId(1L);
    entity.setName("12345678901234567890");

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testStringLengthExceedsByOne() {
    LengthEntity entity = new LengthEntity();
    entity.setId(1L);
    entity.setName("123456789012345678901");

    ValidationException ex = assertThrows(ValidationException.class,
        () -> EntityValidator.validate(entity));
    assertTrue(ex.getMessage().contains("exceeds maximum length"));
  }

  @Test
  void testEmptyStringIsValid() {
    LengthEntity entity = new LengthEntity();
    entity.setId(1L);
    entity.setName("");

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testNotNullWithEmptyString() {
    NotNullStringEntity entity = new NotNullStringEntity();
    entity.setId(1L);
    entity.setValue("");

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testNullableFieldCanBeNull() {
    NullableEntity entity = new NullableEntity();
    entity.setId(1L);
    entity.setOptionalField(null);
    entity.setRequiredField("Required");

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testNonNullableFieldCannotBeNull() {
    NullableEntity entity = new NullableEntity();
    entity.setId(1L);
    entity.setOptionalField("Optional");
    entity.setRequiredField(null);

    ValidationException ex = assertThrows(ValidationException.class,
        () -> EntityValidator.validate(entity));
    assertTrue(ex.getMessage().contains("requiredField"));
  }

  @Test
  void testValidationWithPrimitiveTypes() {
    PrimitiveEntity entity = new PrimitiveEntity();
    entity.setId(1L);
    entity.setCount(0);
    entity.setActive(false);
    entity.setValue(0.0);

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testValidationWithDateTypes() {
    DateEntity entity = new DateEntity();
    entity.setId(1L);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setBirthDate(LocalDate.of(1990, 1, 1));

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testValidationWithNullDateField() {
    DateEntity entity = new DateEntity();
    entity.setId(1L);
    entity.setCreatedAt(null);
    entity.setBirthDate(null);

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testOrmFieldNullableDefaultIsTrue() {
    DefaultNullableEntity entity = new DefaultNullableEntity();
    entity.setId(1L);
    entity.setField(null);

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testCustomNotNullMessage() {
    CustomMessageEntity entity = new CustomMessageEntity();
    entity.setId(1L);
    entity.setUsername(null);

    ValidationException ex = assertThrows(ValidationException.class,
        () -> EntityValidator.validate(entity));
    assertTrue(ex.getMessage().contains("Username is required"));
  }

  @Test
  void testMultipleLengthConstraints() {
    MultipleLengthEntity entity = new MultipleLengthEntity();
    entity.setId(1L);
    entity.setShortField("12345");
    entity.setLongField("This is a longer string that should fit within 100 characters.");

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testNestedObjectNotValidated() {
    NestedEntity entity = new NestedEntity();
    entity.setId(1L);
    entity.setNested(new SimpleInner());

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @OrmEntity(table = "full_entity")
  public static class FullEntity {

    @OrmEntityId
    private Long id;

    @OrmNotNull
    @OrmField(length = 100)
    private String name;

    @OrmField
    private String email;

    @OrmField
    private int age;

    @OrmField
    private BigDecimal balance;

    public void setId(Long id) {
      this.id = id;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public void setAge(int age) {
      this.age = age;
    }

    public void setBalance(BigDecimal balance) {
      this.balance = balance;
    }
  }

  @OrmEntity(table = "multi_not_null")
  public static class MultipleNotNullEntity {

    @OrmEntityId
    private Long id;

    @OrmNotNull
    private String firstName;

    @OrmNotNull
    private String lastName;

    public void setId(Long id) {
      this.id = id;
    }

    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    public void setLastName(String lastName) {
      this.lastName = lastName;
    }
  }

  @OrmEntity(table = "length_entity")
  public static class LengthEntity {

    @OrmEntityId
    private Long id;

    @OrmField(length = 20)
    private String name;

    public void setId(Long id) {
      this.id = id;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @OrmEntity(table = "not_null_string")
  public static class NotNullStringEntity {

    @OrmEntityId
    private Long id;

    @OrmNotNull
    private String value;

    public void setId(Long id) {
      this.id = id;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "nullable_entity")
  public static class NullableEntity {

    @OrmEntityId
    private Long id;

    @OrmField(nullable = true)
    private String optionalField;

    @OrmField(nullable = false)
    private String requiredField;

    public void setId(Long id) {
      this.id = id;
    }

    public void setOptionalField(String optionalField) {
      this.optionalField = optionalField;
    }

    public void setRequiredField(String requiredField) {
      this.requiredField = requiredField;
    }
  }

  @OrmEntity(table = "primitive_entity")
  public static class PrimitiveEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private int count;

    @OrmField
    private boolean active;

    @OrmField
    private double value;

    public void setId(Long id) {
      this.id = id;
    }

    public void setCount(int count) {
      this.count = count;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public void setValue(double value) {
      this.value = value;
    }
  }

  @OrmEntity(table = "date_entity")
  public static class DateEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private LocalDateTime createdAt;

    @OrmField
    private LocalDate birthDate;

    public void setId(Long id) {
      this.id = id;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
    }

    public void setBirthDate(LocalDate birthDate) {
      this.birthDate = birthDate;
    }
  }

  @OrmEntity(table = "default_nullable")
  public static class DefaultNullableEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String field;

    public void setId(Long id) {
      this.id = id;
    }

    public void setField(String field) {
      this.field = field;
    }
  }

  @OrmEntity(table = "custom_msg")
  public static class CustomMessageEntity {

    @OrmEntityId
    private Long id;

    @OrmNotNull(message = "Username is required")
    private String username;

    public void setId(Long id) {
      this.id = id;
    }

    public void setUsername(String username) {
      this.username = username;
    }
  }

  @OrmEntity(table = "multi_length")
  public static class MultipleLengthEntity {

    @OrmEntityId
    private Long id;

    @OrmField(length = 10)
    private String shortField;

    @OrmField(length = 100)
    private String longField;

    public void setId(Long id) {
      this.id = id;
    }

    public void setShortField(String shortField) {
      this.shortField = shortField;
    }

    public void setLongField(String longField) {
      this.longField = longField;
    }
  }

  @OrmEntity(table = "nested_entity")
  public static class NestedEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private SimpleInner nested;

    public void setId(Long id) {
      this.id = id;
    }

    public void setNested(SimpleInner nested) {
      this.nested = nested;
    }
  }

  public static class SimpleInner {

    private String value;
  }
}
