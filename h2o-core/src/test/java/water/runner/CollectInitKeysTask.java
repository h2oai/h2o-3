package water.runner;

import water.H2O;
import water.MRTask;

import java.io.Serializable;

public class CollectInitKeysTask extends MRTask<CollectInitKeysTask> implements Serializable {

    @Override
    protected void setupLocal() {
        LocalTestRuntime.initKeys.addAll(H2O.localKeySet());
    }
}
