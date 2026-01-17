package pl.minecodes.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrmManyToMany {

  Class<?> targetEntity();

  String joinTable() default "";

  String joinColumn() default "";

  String inverseJoinColumn() default "";

  FetchType fetch() default FetchType.LAZY;

  boolean cascade() default false;
}
