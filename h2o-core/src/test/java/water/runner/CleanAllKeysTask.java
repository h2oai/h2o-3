package water.runner;

import org.junit.Ignore;
import water.H2O;
import water.MRTask;

import java.io.Serializable;

@Ignore
public class CleanAllKeysTask extends MRTask<CleanAllKeysTask> {

    @Override
    protected void setupLocal() {
        LocalTestRuntime.initKeys.clear();
        H2O.raw_clear();
        water.fvec.Vec.ESPC.clear();
    }
}
