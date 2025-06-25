package pl.minecodes.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrmManyToMany {
  /**
   * Klasa encji docelowej.
   */
  Class<?> targetEntity();
  
  /**
   * Nazwa tabeli łączącej.
   */
  String joinTable() default "";
  
  /**
   * Nazwa kolumny w tabeli łączącej wskazującej na tę encję.
   */
  String joinColumn() default "";
  
  /**
   * Nazwa kolumny w tabeli łączącej wskazującej na encję docelową.
   */
  String inverseJoinColumn() default "";
  
  /**
   * Strategia wczytywania (eager/lazy).
   */
  FetchType fetch() default FetchType.LAZY;
  
  /**
   * Czy kaskadowo usuwać powiązane encje.
   */
  boolean cascade() default false;
}
