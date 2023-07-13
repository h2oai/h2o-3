package water;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PersistenceContext implements AutoCloseable{
    static private ThreadLocal<Boolean> withinContext = ThreadLocal.withInitial(() -> false);
    static private ThreadLocal<Set<Key>> processed = ThreadLocal.withInitial(HashSet::new);

    private PersistenceContext(){
        withinContext.set(true);
    }

    public static PersistenceContext begin() {
        return new PersistenceContext();
    }

    public static AutoBuffer putKey(AutoBuffer ab, Key k) {
        if (!withinContext.get())
            return ab.putKey(k);

        if (processed.get().contains(k))
            return ab;

        processed.get().add(k);
        return ab.putKey(k);
    }

    public static void loadKey(AutoBuffer ab, Futures fs,  Key k) {
        if (!withinContext.get()) {
            ab.getKey(k, fs);
            return;
        }

        if (processed.get().contains(k))
            return;

        ab.getKey(k, fs);
        processed.get().add(k);
    }

    public static Keyed getKey(AutoBuffer ab, Futures fs, Key k) {
        loadKey(ab, fs, k);
        return k.get();
    }

    @Override
    public void close() throws Exception {
        processed.remove();
        withinContext.remove();
    }
}
