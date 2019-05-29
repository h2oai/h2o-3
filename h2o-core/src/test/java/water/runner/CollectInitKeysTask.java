package water.runner;

import water.H2O;
import water.MRTask;

public class CollectInitKeysTask extends MRTask<CollectInitKeysTask> {


    @Override
    protected void setupLocal() {
        LocalTestRuntime.initKeys.addAll(H2O.localKeySet());
    }
}
