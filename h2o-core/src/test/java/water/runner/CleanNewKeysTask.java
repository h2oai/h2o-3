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

            final Value value = Value.STORE_get(actualKey);
            if (initKeys.contains(actualKey) || isIgnorableKeyLeak(actualKey, value)) continue;
            if (!(value.get() instanceof Keyed)) {
                // Keyed objects might override remove_impl to excerscise their own removal strategy
                // Non-keyed objects should just be removed from the DKV
                DKV.remove(actualKey);
            } else {
                actualKey.remove();
            }
        }
        
    }

}
