package ai.h2o.automl;

import org.junit.Test;
import water.Key;

public class EventLogTest extends water.TestUtil {

    private static Key<AutoML> dummy = Key.make();

    @Test public void test() {
        EventLog eventLog = new EventLog(dummy);

    }
}
