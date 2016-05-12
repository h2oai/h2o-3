package hex.genmodel.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark method which should be generated automatically.
 */
public class CG {

  /**
   * Delegate code generation of the value to a method/name specified in target field.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
  public @interface Delegate {
    /** Name of method/parameter of target object which is called/used during generation. */
    String target();

    String comment() default NA;

    /** Skip value generation when query is true: ".output#isSupervised" */
    String when() default NA;
  }

  /**
   * Marks fields/methods which need manual generation.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.FIELD})
  public @interface Manual {
    String comment() default NA;
  }

  public static class CGException extends RuntimeException {

    public CGException(String message) {
      super(message);
    }

    public CGException(String message, Throwable cause) {
      super(message, cause);
    }

    public CGException(Throwable cause) {
      super(cause);
    }
  }

  public static final String NA = "N/A";
}
