package water.junit.rules.tasks;

import org.junit.Ignore;
import water.Key;

import java.util.HashSet;
import java.util.Set;

@Ignore
class LocalTestRuntime {
    static Set<Key> beforeTestKeys = new HashSet<>();
}
