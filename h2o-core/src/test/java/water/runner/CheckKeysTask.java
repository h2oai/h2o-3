package water.runner;

import org.junit.Ignore;
import water.*;
import water.util.ArrayUtils;

import java.util.*;

@Ignore
public class CheckKeysTask extends KeysMRTask<CheckKeysTask> {

    Key[] leakedKeys;

    @Override
    protected void setupLocal() {

        final Set<Key> initKeys = LocalTestRuntime.initKeys;
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

    @Override
    public void reduce(CheckKeysTask mrt) {
        leakedKeys = ArrayUtils.append(leakedKeys, mrt.leakedKeys);
    }
}
