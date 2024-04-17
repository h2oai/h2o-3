package water.junit.rules;

import org.junit.Ignore;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import water.DKVManager;
import water.Key;
import water.junit.Priority;
import water.junit.rules.tasks.ClearBeforeTestKeysTask;

@Ignore @Priority(RulesPriorities.DKV_ISOLATION)
public class DKVIsolation implements TestRule {
  
  final Key[] retainedKeys;

  public DKVIsolation(Key... retainedKeys) {
    this.retainedKeys = retainedKeys;
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
