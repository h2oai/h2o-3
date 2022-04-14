package hex.tree.isoforextended;

import hex.Model;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtils;
import water.util.RandomUtils;

import java.util.Arrays;

import static hex.genmodel.algos.isoforextended.ExtendedIsolationForestMojoModel.averagePathLengthOfUnsuccessfulSearch;
import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ExtendedIsolationForestTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForestTest.class);

    @Test
    public void testBasicTrain() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScore() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());

            model.testJavaScoring(train, out, 1e-3);
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTrain0Trees() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 0;
            p._sample_size = 10;
            p._extension_level = 0;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorOnlyRootsDoesNotMakeSense() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 1;
            p._extension_level = -1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorNegativeExtensionLevel() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = -1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTooHighExtensionLevel() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = 2;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigData() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(128, 100_000, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 30_000;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigDataRows() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(2, 65536, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigDataCols() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(128, 500, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicWithCategoricalData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EE", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .withDataForCol(3, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(train);

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 2;
            p._extension_level = 2;
            p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
            model.testJavaScoring(train, out, 1e-3);
        } finally {
            Scope.exit();
        }
    }

    /**
     * String data will be ignored
     */
    @Test
    public void testBasicWithStringData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_STR, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EEa", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .withDataForCol(3, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(train);

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 2;
            p._extension_level = 2; // Maximum is 2 because String column will be removed

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void avgPathLengTest() {
        assertEquals(10.244770920116851,
                averagePathLengthOfUnsuccessfulSearch(256), 1e-5);
        assertEquals(11.583643521303037,
                averagePathLengthOfUnsuccessfulSearch(500), 1e-5);
        assertEquals(1, averagePathLengthOfUnsuccessfulSearch(2), 0);
        assertEquals(0, averagePathLengthOfUnsuccessfulSearch(1), 0);
        assertEquals(0, averagePathLengthOfUnsuccessfulSearch(0), 0);
        assertEquals(0, averagePathLengthOfUnsuccessfulSearch(-1), 0);
    }

    @Test
    public void testMapReducePrint() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            new PrintMRTask().doAll(train);

        } finally {
            Scope.exit();
        }
    }

    class PrintMRTask extends MRTask<PrintMRTask> {

        @Override
        public void map(Chunk[] cs) {
            int numCols = cs.length;
            System.out.println("numCols = " + numCols);
            for (int row = 0; row < cs[0]._len; row++) {
                for (int col = 0; col < numCols; col++) {
                    System.out.print(cs[col].atd(row) + ", ");
                }
                System.out.println("");
            }
        }
    }

    @Test
    public void testMapReduceWithOutput() {
        try {
            // Each test should start with empty DKV and finish with empty DKV. We have a Scope class to help with that.
            Scope.enter();
            
            // Define testing frame
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_STR, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    // Col 1 is categorical, it means represented as a number and the number is index in train.domains()[1] array.
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EEa", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .withDataForCol(3, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();

            // Track the frame stored in DKV
            Scope.track(train);

            System.out.println("train.domains() = " + Arrays.toString(train.domains()[1]));
            System.out.println("The B is represented as " + train.vec(1).at8(0));
            assertEquals(1, train.vec(1).at8(0));

            byte[] outputTypes = new byte[]{Vec.T_NUM, Vec.T_CAT, Vec.T_STR};
            Key<Frame> outputKey = Key.make("result_frame");
            String[] outputColNames = new String[]{"First", "Second", "Third"};
            String[][] outputDomains = new String[][]{null, train.domains()[1], null};

            int multiply = 2;

            // Define task
            DifferentOutputMRTask task = new DifferentOutputMRTask(multiply);
            
            // Run task
            task.doAll(outputTypes, train);

            // Get result
            Frame result = task.outputFrame(
                    outputKey, // The output Frame will be stored in DKV and you can access it with this Key, can be null, in case you don't wanted in DKV
                    outputColNames,
                    outputDomains // Categorical columns need domain, pass null for Numerical and String columns
            );
            Scope.track(result);

            // Expected result
            Frame resultExpected = new TestFrameBuilder()
                    .withColNames("First", "Second", "Third")
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_STR)
                    .withDataForCol(0, ard(0.0 * multiply, 2.0 * multiply, 4.0 * multiply, 6.0 * multiply, 8.0 * multiply, 10.0 * multiply, 12.0 * multiply, 14.0 * multiply, 16.0 * multiply, 18.0 * multiply))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EEa", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .build();
            Scope.track(resultExpected);

            assertFrameEquals(resultExpected, result, 1e-3);
            
            // Get result form DKV manually
            result = null;
            result = DKV.get(outputKey).get();
            // Key of result is already tracked not need to add Scope.track(result); here.
            assertFrameEquals(resultExpected, result, 1e-3);

            // Put result to the DKV manually
            result = task.outputFrame(
                    null,
                    outputColNames,
                    outputDomains // Categorical columns need domain, pass null for Numerical and String columns
            );
            result._key = Key.make();
            DKV.put(result);
            
            // Remove something from DKV manually
            DKV.remove(result._key);
        }
        finally {
            // Delete all tracked keys in DKV, if DKV is empty -> test passed
            Scope.exit();
        }
    }

    /**
     * Do (First col + Last col) * multiply. 
     * Drop last col.
     */
    class DifferentOutputMRTask extends MRTask<DifferentOutputMRTask> {
        int _multiply;
        
        public DifferentOutputMRTask(int multiply) {
            _multiply = multiply;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] nc) {
            int numCols = cs.length;
            int lastCol = numCols - 1;
            for (int row = 0; row < cs[0]._len; row++) {
                    nc[0].addNum((cs[0].atd(row) + cs[lastCol].atd(row)) * _multiply);
                    nc[1].addNum(cs[1].atd(row));
                    nc[2].addStr(cs[2].stringAt(row));
                }
            }
        }
}
