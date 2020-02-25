package water.runner;

import org.junit.Ignore;
import water.*;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Set;

@Ignore
public class CheckKeysTask extends MRTask<CheckKeysTask> {

    Key[] leakedKeys;

    /**
     * Determines if a key leak is ignorable
     *
     * @param key   A leaked key
     * @param value An instance of {@link Value} associated with the key
     * @return True if the leak is considered to be ignorable, otherwise false
     */
    protected static boolean isIgnorableKeyLeak(final Key key, final Value value) {

        return value == null || value.isVecGroup() || value.isESPCGroup() || key.equals(Job.LIST) ||
                (value.isJob() && value.<Job>get().isStopped());
    }

    @Override
    public void reduce(CheckKeysTask mrt) {
        leakedKeys = ArrayUtils.append(leakedKeys, mrt.leakedKeys);
    }

    @Override
    protected void setupLocal() {

        final Set<Key> initKeys = LocalTestRuntime.beforeTestKeys;
        final Set<Key> keysAfterTest = H2O.localKeySet();

        final int numLeakedKeys = keysAfterTest.size() - initKeys.size();
        leakedKeys = numLeakedKeys > 0 ? new Key[numLeakedKeys] : new Key[]{};
        if (numLeakedKeys > 0) {
            int leakedKeysPointer = 0;

            for (Key key : keysAfterTest) {
                if (initKeys.contains(key)) continue;

                final Value keyValue = Value.STORE_get(key);
                if (!isIgnorableKeyLeak(key, keyValue)) {
                    leakedKeys[leakedKeysPointer++] = key;
                }
            }
            if (leakedKeysPointer < numLeakedKeys) leakedKeys = Arrays.copyOfRange(leakedKeys, 0, leakedKeysPointer);
        }

    }
}
