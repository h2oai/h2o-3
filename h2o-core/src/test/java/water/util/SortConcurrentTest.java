package water.util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.H2ORunner;

/**
 * Test that Sort can be used concurrently - concurrent sort is used by Isotonic Regression in CV
 * See <a href="https://github.com/h2oai/h2o-3/issues/6930">https://github.com/h2oai/h2o-3/issues/6930</a>
 * for the original issue.
 */
@RunWith(H2ORunner.class)
public class SortConcurrentTest extends TestUtil {

    @After
    public void cleanUpDKV() {
        new TestUtil.DKVCleaner().doAllNodes(); // leaked keys are inevitable in this kind of test
    }

    @Test
    public void testSequentialCVSort() {
        try {
            Scope.enter();
            Frame f = parseAndTrackTestFile("smalldata/logreg/prostate.csv");
            SortModel.SortParameters sp = new SortModel.SortParameters();
            sp._train = f._key;
            sp._nfolds = 5;
            sp._nModelsInParallel = 1;
            SortModel m = new Sort(sp).trainModel().get();
            Assert.assertNotNull(m);
            m.delete();
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testParallelCVSort() {
        try {
            Scope.enter();
            Frame f = parseAndTrackTestFile("smalldata/logreg/prostate.csv");
            SortModel.SortParameters sp = new SortModel.SortParameters();
            sp._train = f._key;
            sp._nfolds = 5;
            sp._nModelsInParallel = 5;
            SortModel m = new Sort(sp).trainModel().get();
            Assert.assertNotNull(m);
            m.delete();
        } finally {
            Scope.exit();
        }
    }

}
