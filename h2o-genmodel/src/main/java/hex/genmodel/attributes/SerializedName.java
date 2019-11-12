package hex.genmodel.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD})
public @interface SerializedName {

  String value();
  String insideElementPath() default "";
}
