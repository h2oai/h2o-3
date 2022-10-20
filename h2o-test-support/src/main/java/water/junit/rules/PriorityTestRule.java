package water.junit.rules;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public interface PriorityTestRule extends TestRule {
  
  int IGNORED_TEST_RULE_PRIORITY = 1_000; // all rules with lower priority will be skipped if the test itself is skipped.
  
  /** 
   * the higher the priority, the earlier the rule is evaluated compared with other rules,
   * meaning its {@link #apply(Statement, Description)} will be called after others, 
   * but if it wraps the given statement, then its {@link Statement#evaluate()} logic will be the outermost one.
   **/
  int priority();
  
}
