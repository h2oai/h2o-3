package water.schemas;

import java.lang.annotation.*;

/** API Annotation
 *
 *  API annotations are used to document *input field* behaviors for the
 *  external REST API.  Each input field to some web page is described by a
 *  matching Java field, plus these annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface API {
  
  // A Short help text to appear beside the input
  String help();

  // The following are markers for *input* fields.
  // If at least one of these annotations appears, this is an input field.
  // If none appear, this is NOT an input field.

  // A list of field names that this field depends on
  String[] dependsOn() default {};

  // A short boolean expression that can be executed *on the front end* to
  // validate inputs with requiring as much chatty traffic with the server.
  // The language here is TBD, but will be easily eval'd by JavaScript.
  //
  // For example, a "this field is required" test can be done by checking that
  // the URL string is not empty in the front end.
  //
  // The Big Hammer Notation for overriding all other validation schemes in the
  // API language is to call out a ?validation URL:
  //       "Cloud?validation=some_java_func"  calls 
  //       boolean CloudV1Handler.some_java_func(CloudV1 cv1)
  String validation() default "";

  // A short JS-like expression, same as "validation" above, that returns a
  // selection of valid values.  Used for e.g. drop-down menus where response
  // times are interactive.
  String values() default "";
}
