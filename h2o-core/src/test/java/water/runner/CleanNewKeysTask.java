package water.runner;

import water.*;

import java.util.Set;

public class CleanNewKeysTask extends MRTask<CleanNewKeysTask> {

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

    private static boolean isIgnorableKeyLeak(final Key key, final Value keyValue) {
        return keyValue == null || keyValue.isVecGroup() || keyValue.isESPCGroup() || key == Job.LIST
                || (keyValue.isJob() && keyValue.<Job>get().isStopped());
    }
}
