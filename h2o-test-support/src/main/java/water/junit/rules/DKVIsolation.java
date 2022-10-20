package water.junit.rules;

import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import water.DKVManager;
import water.Key;
import water.junit.rules.tasks.ClearBeforeTestKeysTask;

@Ignore 
public class DKVIsolation implements PriorityTestRule {
  
  final Key[] retainedKeys;

  public DKVIsolation(Key... retainedKeys) {
    this.retainedKeys = retainedKeys;
  }

  /**
   * High priority so that it applies even if the test was ignored. 
   * Prevents having ignored tests showing leaked keys failures
   * because ignored tests skip rules evaluation but don't skip checks for key leakage, 
   * therefore they can inherit the state of the previous state and appear as failures.
   */
  @Override
  public int priority() {
    return 100;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        resetKeys();
        base.evaluate();
      }
    };
  }

  protected void resetKeys() {
    new ClearBeforeTestKeysTask(retainedKeys).doAllNodes();
    if (retainedKeys.length > 0)
      DKVManager.retain(retainedKeys);
    else
      DKVManager.clear();
  }
}
