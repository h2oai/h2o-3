package water.runner;

import org.junit.Ignore;
import water.*;


@Ignore
public class CleanNewKeysTask extends KeysMRTask<CleanNewKeysTask> {

    @Override
    protected void setupLocal() {
        DKVManager.retainLocal(LocalTestRuntime.initKeys);
    }

}
