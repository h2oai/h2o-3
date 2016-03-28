package hex.genmodel.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark method which should be generated automatically.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
public @interface CG {
  /** Name of method/parameter of target object which is called/used during generation. */
  String delegate();

  String comment() default NA;

  /** Skip value generation when query is true: ".output#isSupervised" */
  String when() default NA;

  String NA = "N/A";

}
