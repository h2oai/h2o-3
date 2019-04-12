package water.runner;

import water.Job;
import water.Key;
import water.MRTask;
import water.Value;

public abstract class KeysMRTask<T extends KeysMRTask<T>> extends MRTask<T> {

    /**
     * Determines if a key leak is ignorable
     * @param key      A leaked key
     * @param keyValue An instance of {@link Value} associated with the key
     * @return True if the leak is considered to be ignorable, otherwise false
     */
    protected static boolean isIgnorableKeyLeak(final Key key, final Value keyValue) {
        return keyValue == null || keyValue.isVecGroup() || keyValue.isESPCGroup() || key == Job.LIST
                || (keyValue.isJob() && keyValue.<Job>get().isStopped());
    }
}
