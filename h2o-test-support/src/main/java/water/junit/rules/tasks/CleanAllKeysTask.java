package water.junit.rules.tasks;

import org.junit.Ignore;
import water.H2O;
import water.MRTask;

@Ignore
public class CleanAllKeysTask extends MRTask<CleanAllKeysTask> {

    @Override
    protected void setupLocal() {
        LocalTestRuntime.beforeTestKeys.clear();
        H2O.raw_clear();
        water.fvec.Vec.ESPC.clear();
    }
}
