package pl.minecodes.orm.validation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmNotNull;
import pl.minecodes.orm.exception.ValidationException;

public class EntityValidator {

  public static <T> void validate(T entity) {
    if (entity == null) {
      throw new ValidationException("Entity cannot be null");
    }

    List<String> errors = new ArrayList<>();
    Class<?> entityClass = entity.getClass();

    for (Field field : entityClass.getDeclaredFields()) {
      field.setAccessible(true);

      try {
        Object value = field.get(entity);

        if (field.isAnnotationPresent(OrmNotNull.class)) {
          if (value == null) {
            OrmNotNull annotation = field.getAnnotation(OrmNotNull.class);
            errors.add(field.getName() + ": " + annotation.message());
          }
        }

        if (field.isAnnotationPresent(OrmField.class)) {
          OrmField ormField = field.getAnnotation(OrmField.class);

          if (!ormField.nullable() && value == null) {
            errors.add(field.getName() + ": Field is not nullable");
          }

          if (value instanceof String strValue) {
            if (strValue.length() > ormField.length()) {
              errors.add(
                  field.getName() + ": String exceeds maximum length of " + ormField.length());
            }
          }
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Error accessing field " + field.getName(), e);
      }
    }

    if (!errors.isEmpty()) {
      throw new ValidationException("Validation failed: " + String.join(", ", errors));
    }
  }
}
