package water.runner;

import org.junit.Ignore;
import water.Job;
import water.Key;
import water.MRTask;
import water.Value;

@Ignore
public abstract class KeysMRTask<T extends KeysMRTask<T>> extends MRTask<T> {

    /**
     * Determines if a key leak is ignorable
     * @param key      A leaked key
     * @param value An instance of {@link Value} associated with the key
     * @return True if the leak is considered to be ignorable, otherwise false
     */
    protected static boolean isIgnorableKeyLeak(final Key key, final Value value) {

        return value == null || value.isVecGroup() || value.isESPCGroup() || key.equals(Job.LIST) ||
                (value.isJob() && value.<Job>get().isStopped());
    }
}
