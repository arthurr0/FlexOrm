package pl.minecodes.orm.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmNotNull;
import pl.minecodes.orm.exception.ValidationException;

class ValidationTest {

  @Test
  void testValidEntity() {
    ValidEntity entity = new ValidEntity();
    entity.setId(1L);
    entity.setName("Test");
    entity.setDescription("Description");

    assertDoesNotThrow(() -> EntityValidator.validate(entity));
  }

  @Test
  void testNullEntity() {
    assertThrows(ValidationException.class, () -> EntityValidator.validate(null));
  }

  @Test
  void testNotNullFieldIsNull() {
    ValidEntity entity = new ValidEntity();
    entity.setId(1L);
    entity.setName(null);
    entity.setDescription("Description");

    ValidationException exception = assertThrows(ValidationException.class,
        () -> EntityValidator.validate(entity));
    assertTrue(exception.getMessage().contains("name"));
  }

  @Test
  void testNonNullableFieldIsNull() {
    ValidEntity entity = new ValidEntity();
    entity.setId(1L);
    entity.setName("Test");
    entity.setDescription(null);

    ValidationException exception = assertThrows(ValidationException.class,
        () -> EntityValidator.validate(entity));
    assertTrue(exception.getMessage().contains("description"));
  }

  @Test
  void testStringExceedsMaxLength() {
    ValidEntity entity = new ValidEntity();
    entity.setId(1L);
    entity.setName(
        "This name is way too long and exceeds the maximum length of 50 characters that we set");
    entity.setDescription("OK");

    ValidationException exception = assertThrows(ValidationException.class,
        () -> EntityValidator.validate(entity));
    assertTrue(exception.getMessage().contains("exceeds maximum length"));
  }

  @OrmEntity(table = "valid_entities")
  static class ValidEntity {

    @OrmEntityId
    private Long id;

    @OrmNotNull
    @OrmField(length = 50)
    private String name;

    @OrmField(nullable = false)
    private String description;

    public ValidEntity() {
    }

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

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }
  }
}
