package pl.minecodes.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrmOneToOne {
  /**
   * Klasa encji docelowej.
   */
  Class<?> targetEntity();
  
  /**
   * Nazwa kolumny przechowującej klucz obcy.
   */
  String joinColumn() default "";
  
  /**
   * Nazwa pola w encji docelowej, które wskazuje na tę encję.
   */
  String mappedBy() default "";
  
  /**
   * Strategia wczytywania (eager/lazy).
   */
  FetchType fetch() default FetchType.EAGER;
  
  /**
   * Czy kaskadowo usuwać powiązane encje.
   */
  boolean cascade() default false;
}