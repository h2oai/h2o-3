package water.junit.rules;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * For rules, the higher the priority, the earlier the rule is evaluated compared with other rules,
 * meaning its {@link #apply(Statement, Description)} will be called after others, 
 * but if it wraps the given statement, then its {@link Statement#evaluate()} logic will be the outermost one.
 **/
public final class RulesPriorities {
  private RulesPriorities() {}

  /**
   * all rules with lower priority will be skipped if the test itself is skipped.
   */
  public static final int RUN_TEST = 1_000;

  /**
   * the highest possible priority for non ignored test, and therefore the first applied, ensuring that all other rules are applied in a clean DKV state.
   */
  public static final int DKV_ISOLATION = RUN_TEST - 1;
  
  /**
   * the highest possible priority that can safely be skipped if test is ignored.
   */
  public static final int CHECK_LEAKED_KEYS = RUN_TEST - 2;
  
}
