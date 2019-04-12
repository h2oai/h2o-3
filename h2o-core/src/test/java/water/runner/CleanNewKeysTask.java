package water.runner;

import org.junit.Ignore;
import water.*;

import java.util.Set;

@Ignore
public class CleanNewKeysTask extends KeysMRTask<CleanNewKeysTask> {

    @Override
    protected void setupLocal() {
        final Set<Key> initKeys = LocalTestRuntime.initKeys;
        final Set<Key> actualKeys = H2O.localKeySet();
        for (Key actualKey : actualKeys){
            final Value keyValue = Value.STORE_get(actualKey);
            if(initKeys.contains(actualKey) || isIgnorableKeyLeak(actualKey, keyValue)) continue;
            H2O.STORE.remove(actualKey);
            actualKey.remove();
        }
        
    }

}
