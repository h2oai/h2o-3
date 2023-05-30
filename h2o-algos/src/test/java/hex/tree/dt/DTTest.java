package hex.tree.dt;

import hex.ConfusionMatrix;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import org.junit.*;
import org.junit.runner.RunWith;
import water.Scope;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.util.ConfusionMatrixUtils;
import water.util.FrameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class DTTest extends TestUtil {


    @Test
    public void testBasicData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(2, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            Scope.track_generic(train);


            DTModel.DTParameters p =
                    new DTModel.DTParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._max_depth = 5;
            p._min_rows = 2;
            p._response_column = "Prediction";

            DT dt = new DT(p);
            DTModel model = dt.trainModel().get();
            assert model != null;
            Scope.track_generic(model);
            Frame out = model.score(train);
            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(train.numRows(), out.numRows());


            System.out.println(DKV.getGet(model._output._treeKey));

            Frame test = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withColNames("First", "Second")
                    .build();
            Scope.track_generic(test);

            System.out.println(Arrays.deepToString(((CompressedDT) DKV.getGet(model._output._treeKey)).getNodes()));
            System.out.println(String.join("\n", ((CompressedDT) DKV.getGet(model._output._treeKey)).getListOfRules()));

            Frame prediction = model.score(test);
            Scope.track_generic(prediction);
            System.out.println(Arrays.toString(FrameUtils.asInts(prediction.vec(0))));
            assertEquals(1, prediction.vec(0).at(0), 0.1);
            assertEquals(1, prediction.vec(0).at(1), 0.1);
            assertEquals(0, prediction.vec(0).at(2), 0.1);
            assertEquals(1, prediction.vec(0).at(3), 0.1);
            assertEquals(0, prediction.vec(0).at(4), 0.1);
            assertEquals(1, prediction.vec(0).at(5), 0.1);
            assertEquals(0, prediction.vec(0).at(6), 0.1);
            assertEquals(1, prediction.vec(0).at(7), 0.1);
            assertEquals(1, prediction.vec(0).at(8), 0.1);
            assertEquals(1, prediction.vec(0).at(9), 0.1);

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCategoricalFeaturesChecks() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withDataForCol(2, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            Scope.track_generic(train);
            DTModel.DTParameters p =
                    new DTModel.DTParameters();
            p._train = train._key;
            p._response_column = "Prediction";
            DT dt = new DT(p);

            // validation occurs when starting training
            dt.trainModel().get();
            fail("should have thrown validation error");
        } catch (H2OModelBuilderIllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Categorical features are not supported yet"));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testNaNsChecks() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(Double.NaN, Double.POSITIVE_INFINITY, 
                            0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(2, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            Scope.track_generic(train);
            DTModel.DTParameters p =
                    new DTModel.DTParameters();
            p._train = train._key;
            p._response_column = "Prediction";
            DT dt = new DT(p);

            // validation occurs when starting training
            dt.trainModel().get();
            fail("should have thrown validation error");
        } catch (H2OModelBuilderIllegalArgumentException e) {
            assertTrue(e.getMessage().contains("NaNs are not supported yet"));
            assertTrue(e.getMessage().contains("Infs are not supported"));
        } finally {
            Scope.exit();
        }
    }


    @Test
    public void testPredictionColumnChecks() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(2, ard(1, 2, 2, 1, 0, 1, 0, 1, 1, 1))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            Scope.track_generic(train);
            DTModel.DTParameters p =
                    new DTModel.DTParameters();
            p._train = train._key;
            p._response_column = "Prediction";
            DT dt = new DT(p);

            // validation occurs when starting training
            dt.trainModel().get();
            fail("should have thrown validation error");
        } catch (H2OModelBuilderIllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Only categorical response is supported"));
            assertTrue(e.getMessage().contains("Only binary response is supported"));
        } finally {
            Scope.exit();
        }
    }

    private void writePredictionsToFile(String path, Vec predictions, String prediction_column) {
        File csvOutputFile = new File(path);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            pw.println(prediction_column);
            IntStream.range(0, (int) predictions.length()).mapToDouble(predictions::at)
                    .mapToLong(Math::round).forEach(pw::println);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        assertTrue(csvOutputFile.exists());
    }

    @Test
    public void testProstateSmallData() {

        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/testng/prostate_train.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/testng/prostate_test.csv"));
        train.replace(0, train.vec(0).toCategoricalVec()).remove();
        test.replace(0, test.vec(0).toCategoricalVec()).remove();
        DKV.put(train);
        DKV.put(test);

        DTModel.DTParameters p =
                new DTModel.DTParameters();
        p._train = train._key;
        p._seed = 0xDECAF;
        p._max_depth = 12;
        p._response_column = "CAPSULE";

        DRFModel.DRFParameters p1 =
                new DRFModel.DRFParameters();
        p1._ntrees = 1;
        p1._max_depth = 12;
        p1._response_column = "CAPSULE";
        p1._train = train._key;
        p1._seed = 0xDECAF;

        testDataset(train, test, p, p1, "prostate");
        
    }

    @Test
    public void testAirlinesSmallData() {
        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/testng/airlines_train_preprocessed.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/testng/airlines_test_preprocessed.csv"));
        train.replace(0, train.vec(0).toCategoricalVec()).remove();
        test.replace(0, test.vec(0).toCategoricalVec()).remove();
        DKV.put(train);
        DKV.put(test);

        DTModel.DTParameters p =
                new DTModel.DTParameters();
        p._train = train._key;
        p._seed = 0xDECAF;
        p._max_depth = 12;
        p._response_column = "IsDepDelayed";

        DRFModel.DRFParameters p1 =
                new DRFModel.DRFParameters();
        p1._ntrees = 1;
        p1._max_depth = 12;
        p1._response_column = "IsDepDelayed";
        p1._train = train._key;
        p1._seed = 0xDECAF;

        testDataset(train, test, p, p1, "airlines");
        
    }
    

    @Test
    @Ignore
    public void testBigDataSynthetic() {
        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/yuliia/Dataset1_train.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/yuliia/Dataset1_test.csv"));

        DTModel.DTParameters p =
                new DTModel.DTParameters();
        p._train = train._key;
        p._seed = 0xDECAF;
        p._max_depth = 3;
        p._response_column = "label";

        DRFModel.DRFParameters p1 =
                new DRFModel.DRFParameters();
        p1._ntrees = 1;
        p1._max_depth = 3;
        p1._response_column = "label";
        p1._train = train._key;
        p1._seed = 0xDECAF;

        testDataset(train, test, p, p1, "BigSynthetic");
    }

    @Test
    @Ignore
    public void testBigDataCreditCard() {
        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/yuliia/creditcard_train.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/yuliia/creditcard_test.csv"));

        DTModel.DTParameters p =
                new DTModel.DTParameters();
        p._train = train._key;
        p._seed = 0xDECAF;
        p._max_depth = 5;
        p._response_column = "Class";

        DRFModel.DRFParameters p1 =
                new DRFModel.DRFParameters();
        p1._ntrees = 1;
        p1._max_depth = 5;
        p1._response_column = "Class";
        p1._train = train._key;
        p1._seed = 0xDECAF;

        testDataset(train, test, p, p1, "CreditCard");
    }

    @Test
    @Ignore
    public void testHIGGSDataset() {
        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/yuliia/HIGGS_train_limited1.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/yuliia/HIGGS_test_limited1.csv"));

        DTModel.DTParameters p =
                new DTModel.DTParameters();
        p._train = train._key;
        p._seed = 0xDECAF;
        p._max_depth = 5;
        p._response_column = "label";

        DRFModel.DRFParameters p1 =
                new DRFModel.DRFParameters();
        p1._ntrees = 1;
        p1._max_depth = 5;
        p1._response_column = "label";
        p1._train = train._key;
        p1._seed = 0xDECAF;
        
        testDataset(train, test, p, p1, "HIGGS");
        
    }
    
    public void testDataset(Frame train, Frame test, DTModel.DTParameters p, DRFModel.DRFParameters p1, String datasetName) {
        try {

            DT dt = new DT(p);
            DTModel model = dt.trainModel().get();

            assertNotNull(model);
            Scope.track_generic(model);

            Frame out = model.score(test);

            Scope.track_generic(out);
            assertEquals(test.numRows(), out.numRows());

            ConfusionMatrix cm = ConfusionMatrixUtils.buildCM(
                    test.vec(p._response_column).toCategoricalVec(),
                    out.vec(0).toCategoricalVec());
            System.out.println("DT:");
            System.out.println("Accuracy: " + cm.accuracy());
            System.out.println("F1: " + cm.f1());
            
            // check for model metrics
            assertNotNull(model._output._training_metrics);
            assertNotEquals(0, model._output._training_metrics._MSE);
            assertNotEquals(0, model._output._training_metrics.auc_obj()._auc);
            if (p._valid != null) {
                assertNotNull(model._output._validation_metrics);
                assertNotEquals(0, model._output._validation_metrics._MSE);
                assertNotEquals(0, model._output._validation_metrics.auc_obj()._auc);
            }

//            train.toCategoricalCol(p1._response_column);
//
//            DRF drf = new DRF(p1);
//            DRFModel model1 = drf.trainModel().get();
//
//            assertNotNull(model1);
//            Scope.track_generic(model1);
//
//            Frame out1 = model1.score(test);
//
//            Scope.track_generic(out1);
//            assertEquals(test.numRows(), out1.numRows());
//
//            ConfusionMatrix cm1 = ConfusionMatrixUtils.buildCM(
//                    test.vec(p1._response_column).toCategoricalVec(),
//                    out1.vec(0).toCategoricalVec());
//            System.out.println("DRF:");
//            System.out.println("Accuracy: " + cm1.accuracy());
//            System.out.println("F1: " + cm1.f1());
        } finally {
            Scope.exit();
        }
    }
}
