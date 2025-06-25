package pl.minecodes.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrmIndex {
  /**
   * Nazwa indeksu.
   */
  String name() default "";
  
  /**
   * Czy indeks ma być unikalny.
   */
  boolean unique() default false;
}
