package hex.genmodel.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to simplify identification of model pojos.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModelPojo {
  /** Model name - in fact name of class in the most of cases. */
  String name();

  /** Model algorithm name - drf, gbm, deeplearning, ... */
  String algorithm();

  /** Model memory requirements. Estimated size of model instance in memory.
   * This can help model pojo user to optimize pojo memory utilization. */
  long requiredMemory() default -1L;
}
