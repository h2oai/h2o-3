package hex.tree.gbm;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.CompressedTree;
import hex.tree.Sample;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertNotNull;


@CloudSize(1)
@RunWith(H2ORunner.class)
public class GBMRowToTreeAssignmentTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(GBMRowToTreeAssignmentTest.class);

    @Test
    public void testRowToTreeAssignmentSmokeBinomialHardcoded() {
        Scope.enter();
        try {
            String response = "y";
            Frame train = new TestFrameBuilder()
                    .withColNames("C1", response)
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("0", "1", "0", "1", "0", "1", "0", "1", "0", "1"))
                    .build();
            Scope.track(train);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._ntrees = 2;
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._min_rows = 1;
            parms._sample_rate = 0.5f;
            parms._response_column = response;

            GBMModel model = Scope.track_generic(new GBM(parms).trainModel().get());
            assertNotNull(model);

            Frame rowToTreeAssignmentReference = new TestFrameBuilder()
                    .withColNames("row_id", "tree_1", "tree_2")
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ar(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
                    .withDataForCol(1, ar("1", "0", "1", "1", "1", "0", "1", "0", "1", "1"))
                    .withDataForCol(2, ar("1", "1", "1", "0", "1", "1", "1", "1", "0", "1"))
                    .build();
            Scope.track(rowToTreeAssignmentReference);

            Frame rowToTreeAssignmentActual = Scope.track(model.rowToTreeAssignment(train, Key.make("row_to_tree_assignment"), null));
            LOG.info("rowToTreeAssignmentActual" + rowToTreeAssignmentActual.toTwoDimTable(0,10,false));
            assertFrameEquals(rowToTreeAssignmentReference, rowToTreeAssignmentActual, 0.0, 0.0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testRowToTreeAssignmentSmokeRegressionAndMultinomialHardcoded() {
        Scope.enter();
        try {
            String response = "y";
            Frame train = new TestFrameBuilder()
                    .withColNames("C1", response)
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar(0, 1, 2, 0, 1, 2, 0, 1, 2, 0))
                    .build();
            Scope.track(train);

            Frame rowToTreeAssignmentReference = new TestFrameBuilder()
                    .withColNames("row_id", "tree_1", "tree_2")
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ar(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
                    .withDataForCol(1, ar("1", "0", "1", "1", "1", "0", "1", "0", "1", "1"))
                    .withDataForCol(2, ar("1", "1", "1", "0", "1", "1", "1", "1", "0", "1"))
                    .build();
            Scope.track(rowToTreeAssignmentReference);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._ntrees = 2;
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._min_rows = 1;
            parms._sample_rate = 0.5f;
            parms._response_column = response;

            //Regression model
            GBMModel modelReg = Scope.track_generic(new GBM(parms).trainModel().get());
            assertNotNull(modelReg);

            Frame rowToTreeAssignmentActualReg = Scope.track(modelReg.rowToTreeAssignment(train, Key.make("row_to_tree_assignment"), null));
            LOG.info("rowToTreeAssignmentActualReg" + rowToTreeAssignmentActualReg.toTwoDimTable(0,10,false));
            assertFrameEquals(rowToTreeAssignmentReference, rowToTreeAssignmentActualReg, 0.0, 0.0);

            //Multinomial model
            train.toCategoricalCol(response);
            parms._distribution = DistributionFamily.AUTO;
            GBMModel modelMult = Scope.track_generic(new GBM(parms).trainModel().get());
            assertNotNull(modelMult);

            Frame rowToTreeAssignmentActualMult = Scope.track(modelMult.rowToTreeAssignment(train, Key.make("row_to_tree_assignment"), null));
            LOG.info("rowToTreeAssignmentActualMult" + rowToTreeAssignmentActualMult.toTwoDimTable(0,10,false));
            assertFrameEquals(rowToTreeAssignmentReference, rowToTreeAssignmentActualMult, 0.0, 0.0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testRowToTreeAssignmentSmokeBinomial() {
        Scope.enter();
        try {
            String response = "y";
            Frame train = new TestFrameBuilder()
                    .withColNames("C1", "C2", "C3", response)
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                    .withRandomDoubleDataForCol(0, 50, 0, 100, 0xDEDA)
                    .withRandomDoubleDataForCol(1, 50, 0, 100, 0xDEDA)
                    .withRandomDoubleDataForCol(2, 50, 0, 100, 0xDEDA)
                    .withRandomBinaryDataForCol(3, 50, 0xDEDA)
                    .build();
            Scope.track(train);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._ntrees = 20;
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._sample_rate = 0.6f;
            parms._response_column = response;

            GBMModel model = Scope.track_generic(new GBM(parms).trainModel().get());
            assertNotNull(model);
            checkRowToTreeAssignment(model, train, response);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testRowToTreeAssignmentSmokeMultinomial() {
        Scope.enter();
        try {
            String response = "y";
            Frame train = new TestFrameBuilder()
                    .withColNames("C1", "C2", "C3", response)
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withRandomDoubleDataForCol(0, 50, 0, 100, 0xDEDA)
                    .withRandomDoubleDataForCol(1, 50, 0, 100, 0xDEDA)
                    .withRandomDoubleDataForCol(2, 50, 0, 100, 0xDEDA)
                    .withRandomIntDataForCol(3, 50, 0, 5, 0xDEDA)
                    .build();
            train.toCategoricalCol(response);
            Scope.track(train);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._ntrees = 20;
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._sample_rate = 0.6f;
            parms._response_column = response;

            GBMModel model = Scope.track_generic(new GBM(parms).trainModel().get());
            assertNotNull(model);
            checkRowToTreeAssignment(model, train, response);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testRowToTreeAssignmentSmall() {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0, 2, 4, 8}));
            train.toCategoricalCol(response);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._ntrees = 100;
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._sample_rate = 0.8f;
            parms._response_column = response;

            GBMModel model = Scope.track_generic(new GBM(parms).trainModel().get());
            assertNotNull(model);
            checkRowToTreeAssignment(model, train, response);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testRowToTreeAssignmentLarge() {
        Scope.enter();
        try {
            String response = "Class";
            Frame train = Scope.track(parseTestFile("bigdata/laptop/creditcardfraud/creditcardfraud.csv"));
            train.toCategoricalCol(response);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._ntrees = 100;
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._sample_rate = 0.6f;
            parms._response_column = response;

            GBMModel model = Scope.track_generic(new GBM(parms).trainModel().get());
            assertNotNull(model);
            checkRowToTreeAssignment(model, train, response);
        } finally {
            Scope.exit();
        }
    }

    /**
     * Run sampling externally and compare it with the output
     *
     */
    private void checkRowToTreeAssignment(GBMModel model, Frame train, String response) {
        Frame rowToTreeAssignment = Scope.track(model.rowToTreeAssignment(train, Key.make("row_to_tree_assignment"), null));

        Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] treeKeys = model._output._treeKeys;
        for (int treeId = 0; treeId < treeKeys.length; treeId++) {
            Key<CompressedTree>[] treeKey = treeKeys[treeId];
            for (Key<CompressedTree> compressedTreeKey : treeKey) {
                if (compressedTreeKey == null)
                    continue;
                CompressedTree ct = DKV.getGet(compressedTreeKey);
                long seed = ct.getSeed();
                Vec[] vs = train.anyVec().makeVolatileInts(new int[]{0});
                new Sample(seed, model._parms._sample_rate, model._parms._sample_rate_per_class, 1, 0).doAll(vs[0], train.vec(response));
                assertVecEquals(rowToTreeAssignment.vec("tree_" + (treeId + 1)), vs[0], 0, 0);
            }
        }
    }
}
