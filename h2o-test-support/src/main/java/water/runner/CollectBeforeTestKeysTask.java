package water.runner;

import org.junit.Ignore;
import water.H2O;
import water.MRTask;

@Ignore
public class CollectBeforeTestKeysTask extends MRTask<CollectBeforeTestKeysTask>  {

    @Override
    protected void setupLocal() {
        LocalTestRuntime.beforeTestKeys.addAll(H2O.localKeySet());
    }
}
