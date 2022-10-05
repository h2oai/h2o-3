package water.runner;

import org.junit.Ignore;
import water.Key;
import water.MRTask;

import java.util.Arrays;

@Ignore
public class ClearBeforeTestKeysTask extends MRTask<ClearBeforeTestKeysTask>  {

  private final Key[] _retainedKeys;
  public ClearBeforeTestKeysTask(Key... retainedKeys) {
    _retainedKeys = retainedKeys;
  }

  @Override
  protected void setupLocal() {
    if (_retainedKeys.length > 0)
      LocalTestRuntime.beforeTestKeys.retainAll(Arrays.asList(_retainedKeys));
    else
      LocalTestRuntime.beforeTestKeys.clear();
  }
}
