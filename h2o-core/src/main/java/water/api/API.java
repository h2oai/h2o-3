package water.api;

import java.lang.annotation.*;

/** API Annotation
 *
 *  API annotations are used to document field behaviors for the external REST API.  Each
 *  field is described by a matching Java field, plus these annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface API {

  enum Direction {INPUT, OUTPUT, INOUT}

  enum Level {critical, secondary, expert}

  // A short help text to appear beside the input
  String help();

  // The label that should be displayed for the field if the name is insufficient
  String label() default "";


  // Is this field required?
  boolean required() default false;

  // How important is this field?  The web UI uses the level to do a slow reveal of the parameters.
  Level level() default Level.critical;

  // Is this field an input, output or inout?
  Direction direction() default Direction.INPUT; // TODO: should this be INOUT?

  // The following are markers for *input* fields.
  // If at least one of these annotations appears, this is an input field.
  // If none appear, this is NOT an input field.

  // A list of field names that this field depends on
  String[] dependsOn() default {};

  // A short JS-like expression, same as "validation" above, that returns a
  // selection of valid values.  Used for e.g. drop-down menus where response
  // times are interactive.
  String[] values() default {};

  boolean json() default true;
}
