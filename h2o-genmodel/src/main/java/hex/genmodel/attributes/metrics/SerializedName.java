package hex.genmodel.attributes.metrics;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD})
public @interface SerializedName {

  String value();
  String insideElementPath() default ""; // TODO: Unite or refactor code, temporary solution to work with my outdated codebase
}
