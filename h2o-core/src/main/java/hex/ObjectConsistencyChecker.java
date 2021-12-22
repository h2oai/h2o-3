package hex;

import water.*;

public class ObjectConsistencyChecker extends MRTask<ObjectConsistencyChecker> {

    private final Key<?> _key;
    private final byte[] _bytes;

    public ObjectConsistencyChecker(Key<?> key) {
        _key = key;
        Iced<?> pojo = DKV.getGet(key);
        if (pojo == null) {
            throw new IllegalArgumentException("Object with key='" + key + "' doesn't exist in DKV.");
        }
        _bytes = pojo.asBytes();
    }

    @Override
    protected void setupLocal() {
        Value val = H2O.STORE.get(_key);
        if (val == null)
            return;

        if (!val.isConsistent()) {
            throw new IllegalStateException("Object " + _key + " is locally modified on node " + H2O.SELF + ".");
        }
    }

}
