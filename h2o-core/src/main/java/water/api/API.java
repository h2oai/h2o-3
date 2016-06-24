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

  /** Is a given field an input, an output, or both? */
  enum Direction {INPUT, OUTPUT, INOUT}

  /** How important is it to specify a given field to get a useful result? */
  enum Level {critical, secondary, expert}

  /**
   * A short help description to appear alongside the field in a UI.
   */
  String help();

  /**
   * The label that should be displayed for the field if the name is insufficient.
   */
  String label() default "";

  /**
   * Is this field required, or is the default value generally sufficient?
   */
  boolean required() default false;

  /**
   * How important is this field?  The web UI uses the level to do a slow reveal of the parameters.
   */
  Level level() default Level.critical;

  /**
   * Is this field an input, output or inout?
   */
  Direction direction() default Direction.INPUT;

  // The following are markers for *input* fields.

  /**
   * For enum-type fields the allowed values are specified using the values annotation.
   * This is used in UIs to tell the user the allowed values, and for validation.
   */
  String[] values() default {};

  /** Proovide values for enum-like types if it cannot be provided as a constant in annotation. */
  Class<? extends ValuesProvider> valuesProvider() default ValuesProvider.class;

  /**
   * Should this field be rendered in the JSON representation?
   */
  boolean json() default true;

  /**
   * For Vec-type fields this is the set of Frame-type fields which must contain the named column.
   * For example, for a SupervisedModel the response_column must be in both the training_frame
   * and (if it's set) the validation_frame.
   */
  String[] is_member_of_frames() default {};

  /**
   * For Vec-type fields this is the set of other Vec-type fields which must contain
   * mutually exclusive values.  For example, for a SupervisedModel the response_column
   * must be mutually exclusive with the weights_column.
   */
  String[] is_mutually_exclusive_with() default {};

  /**
   * Identify grid-able parameter.
   */
  boolean gridable() default false;
}
