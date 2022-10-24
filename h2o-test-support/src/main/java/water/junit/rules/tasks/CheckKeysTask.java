package water.junit.rules.tasks;

import org.junit.Ignore;
import water.*;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Ignore
public class CheckKeysTask extends MRTask<CheckKeysTask> {

    public Key[] leakedKeys;
    public LeakInfo[] leakInfos;

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
        leakInfos = ArrayUtils.append(leakInfos, mrt.leakInfos);
    }

    @Override
    protected void setupLocal() {

        final Set<Key> initKeys = LocalTestRuntime.beforeTestKeys;
        final Set<Key> keysAfterTest = H2O.localKeySet();

        Set<Key> leaks = new HashSet<>(keysAfterTest);
        leaks.removeAll(initKeys);
        final int numLeakedKeys = leaks.size();
        leakedKeys = numLeakedKeys > 0 ? new Key[numLeakedKeys] : new Key[]{};
        leakInfos = new LeakInfo[]{};
        if (numLeakedKeys > 0) {
            int leakedKeysPointer = 0;

            for (Key key : leaks) {
                final Value keyValue = Value.STORE_get(key);
                if (!isIgnorableKeyLeak(key, keyValue)) {
                    leakedKeys[leakedKeysPointer] = key;
                    LeakInfo leakInfo = makeLeakInfo(leakedKeysPointer, keyValue);
                    if (leakInfo != null) {
                        leakInfos = ArrayUtils.append(leakInfos, leakInfo);
                    }
                    leakedKeysPointer++;
                }
            }
            if (leakedKeysPointer < numLeakedKeys) leakedKeys = Arrays.copyOfRange(leakedKeys, 0, leakedKeysPointer);
        }
    }

    private LeakInfo makeLeakInfo(int keyIdx, Value value) {
        if (value == null)
            return null;
        String vClass = value.className();
        switch (vClass) {
            case "water.fvec.RollupStats":
                @SuppressWarnings("unchecked")
                Key<Vec> vecKey = Vec.getVecKey(leakedKeys[keyIdx]);
                return new LeakInfo(keyIdx, vecKey, String.valueOf(value.get()));
            default:
                return null;
        }
    }
    
    public static class LeakInfo extends Iced<LeakInfo> {
        public final int _keyIdx;
        public final Key<Vec> _vecKey;
        public final int _nodeId;
        public final String _info;

        private LeakInfo(int keyIdx, Key<Vec> vecKey, String info) {
            _keyIdx = keyIdx;
            _vecKey = vecKey;
            _nodeId = H2O.SELF.index();
            _info = info;
        }

        @Override
        public String toString() {
            return "nodeId=" + _nodeId + ", vecKey=" + String.valueOf(_vecKey) + ", _info='" + _info;
        }
    }
    
}
