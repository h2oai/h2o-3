package water.runner;

import org.junit.Ignore;
import water.*;

import java.util.Set;

@Ignore
public class CleanNewKeysTask extends KeysMRTask<CleanNewKeysTask> {

    @Override
    protected void setupLocal() {
        DKVManager.retain(LocalTestRuntime.initKeys.toArray(new Key[0]));
    }

}
