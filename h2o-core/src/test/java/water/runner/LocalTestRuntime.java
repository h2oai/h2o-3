package water.runner;

import org.junit.Ignore;
import water.Key;

import java.util.HashSet;
import java.util.Set;

@Ignore
class LocalTestRuntime {
    protected static Set<Key> initKeys;

    static{
        initKeys = new HashSet<>();
    }
}
