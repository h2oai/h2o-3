package water.runner;

import water.H2O;
import water.Key;
import water.MRTask;

import java.util.Set;

public class CleanNewKeysTask extends MRTask<CleanNewKeysTask> {

    @Override
    protected void setupLocal() {
        final Set<Key> initKeys = LocalTestRuntime.initKeys;
        final Set<Key> actualKeys = H2O.localKeySet();
        for (Key actualKey : actualKeys){
            if(initKeys.contains(actualKey)) continue;
            actualKeys.remove(actualKey);
            actualKey.remove();
        }
        
    }
}
