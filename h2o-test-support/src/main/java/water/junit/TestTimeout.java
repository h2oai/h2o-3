package water.junit;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Overrides the default per-test timeout enforced by {@code TestUtil#globalTimeout} for tests that
 * are legitimately long-running. Applies to a single test method, or to a whole class when the
 * annotation is placed on the type; a method-level value wins over a class-level one.
 *
 * Use this only for tests known to be slow - a test that hangs should be fixed or ignored instead
 * of being given more time.
 */
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface TestTimeout {
  int seconds();
}
