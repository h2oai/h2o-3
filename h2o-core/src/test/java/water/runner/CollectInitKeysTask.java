package water.runner;

import org.junit.Ignore;
import water.H2O;
import water.MRTask;

import java.io.Serializable;

@Ignore
public class CollectInitKeysTask extends MRTask<CollectInitKeysTask> implements Serializable {

    @Override
    protected void setupLocal() {
        LocalTestRuntime.initKeys.addAll(H2O.localKeySet());
    }
}
