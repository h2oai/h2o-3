package water.runner;

import water.H2O;
import water.MRTask;

public class CleanAllKeysTask extends MRTask<CleanAllKeysTask> {

    @Override
    protected void setupLocal() {
        LocalTestRuntime.initKeys.clear();
        H2O.raw_clear();
        water.fvec.Vec.ESPC.clear();
    }
}
