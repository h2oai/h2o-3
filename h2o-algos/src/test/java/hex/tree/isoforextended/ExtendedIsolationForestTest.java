package hex.tree.isoforextended;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

public class ExtendedIsolationForestTest extends TestUtil {

    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testBasic() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));

            ExtendedIsolationForestParameters p = new ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 7;
            p._extension_level = train.numCols() - 1;
            p._min_rows = 1;
//            p._sample_size = 5;

            ExtendedIsolationForestModel model = new ExtendedIsolationForest(p).trainModel().get();
        } finally {
            Scope.exit();
        }
    }
}
