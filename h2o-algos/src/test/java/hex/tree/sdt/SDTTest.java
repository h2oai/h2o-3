package hex.tree.sdt;

import hex.ConfusionMatrix;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import org.junit.*;
import org.junit.runner.RunWith;
import water.Scope;
import water.*;
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
public class SDTTest extends TestUtil {

//    @ClassRule
//    public static EnvironmentVariables environmentVariables = new EnvironmentVariables();
//    
//    @BeforeClass
//    public static void initTest() {
//        final File h2oHomeDir = new File(System.getProperty("user.dir")).getParentFile();
//        environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath());
//    }

    @Test
    public void testBasicData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(2, ard(1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            Scope.track_generic(train);


            SDTModel.SDTParameters p =
                    new SDTModel.SDTParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._max_depth = 5;
            p._limitNumSamplesForSplit = 2;
            p._response_column = "Prediction";

            SDT sdt = new SDT(p);
            SDTModel model = sdt.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(train.numRows(), out.numRows());


//            System.out.println(DKV.getGet(model._output._treeKey));

            Frame test = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withColNames("First", "Second")
                    .build();
            Scope.track_generic(test);

            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output._treeKey)).getNodes()));
            System.out.println(String.join("\n", ((CompressedSDT) DKV.getGet(model._output._treeKey)).getListOfRules()));

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

        SDTModel.SDTParameters p =
                new SDTModel.SDTParameters();
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

        SDTModel.SDTParameters p =
                new SDTModel.SDTParameters();
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
    public void testBigDataSynthetic() {
        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/yuliia/Dataset1_train.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/yuliia/Dataset1_test.csv"));

        SDTModel.SDTParameters p =
                new SDTModel.SDTParameters();
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
    public void testBigDataCreditCard() {
        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/yuliia/creditcard_train.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/yuliia/creditcard_test.csv"));

        SDTModel.SDTParameters p =
                new SDTModel.SDTParameters();
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
    public void testHIGGSDataset() {
        Scope.enter();
        Frame train = Scope.track(parseTestFile("smalldata/yuliia/HIGGS_train_limited1.csv"));
        Frame test = Scope.track(parseTestFile("smalldata/yuliia/HIGGS_test_limited1.csv"));

        SDTModel.SDTParameters p =
                new SDTModel.SDTParameters();
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
    
    public void testDataset(Frame train, Frame test, SDTModel.SDTParameters p, DRFModel.DRFParameters p1, String datasetName) {
        try {

            SDT sdt = new SDT(p);
            long start_training_sdt = System.currentTimeMillis();
            SDTModel model = sdt.trainModel().get();
            long end_training_sdt = System.currentTimeMillis();

            assertNotNull(model);
            Scope.track_generic(model);

            long start_predicting_sdt = System.currentTimeMillis();
            Frame out = model.score(test);
            long end_predicting_sdt = System.currentTimeMillis();

            Scope.track_generic(out);
            assertEquals(test.numRows(), out.numRows());

            writePredictionsToFile("./predictions_" + datasetName + "_sdt.csv", out.vec(0).toCategoricalVec(), p._response_column);

//            System.out.println("Scoring: " + model.testJavaScoring(test, out, 1e-3));
//            System.out.println(test.vec(p._response_column));
            if(out.vec(0).length() < 100000) {
                System.out.println(Arrays.toString(FrameUtils.asInts(out.vec(0).toCategoricalVec())));
                System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p._response_column))));
            }

            ConfusionMatrix cm = ConfusionMatrixUtils.buildCM(
                    test.vec(p._response_column).toCategoricalVec(),
                    out.vec(0).toCategoricalVec());
            System.out.println("SDT:");
            System.out.println("Accuracy: " + cm.accuracy());
//            System.out.println("Precision: " + cm.precision());
//            System.out.println("Recall: " + cm.recall());
//            System.out.println("Specificity: " + cm.specificity());
            System.out.println("F1: " + cm.f1());
//            System.out.println("F2: " + cm.f2());

//            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output.treeKey)).nodes));

            train.toCategoricalCol(p1._response_column);

            DRF drf = new DRF(p1);
            long start_training_drf = System.currentTimeMillis();
            DRFModel model1 = drf.trainModel().get();
            long end_training_drf = System.currentTimeMillis();

            assertNotNull(model1);
            Scope.track_generic(model1);

            long start_predicting_drf = System.currentTimeMillis();
            Frame out1 = model1.score(test);
            long end_predicting_drf = System.currentTimeMillis();
            

            Scope.track_generic(out1);
            assertEquals(test.numRows(), out1.numRows());
            writePredictionsToFile("./predictions_" + datasetName + "_drf.csv", out1.vec(0).toCategoricalVec(), p1._response_column);
            
//            System.out.println("Scoring: " + model1.testJavaScoring(test, out1, 1e-3));
//            System.out.println(test.vec(p._response_column));
//            System.out.println(Arrays.toString(FrameUtils.asInts(out1.vec(0))));
//            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p1._response_column))));

            ConfusionMatrix cm1 = ConfusionMatrixUtils.buildCM(
                    test.vec(p1._response_column).toCategoricalVec(),
                    out1.vec(0).toCategoricalVec());
            System.out.println("DRF:");
            System.out.println("Accuracy: " + cm1.accuracy());
//            System.out.println("Precision: " + cm1.precision());
//            System.out.println("Recall: " + cm1.recall());
//            System.out.println("Specificity: " + cm1.specificity());
            System.out.println("F1: " + cm1.f1());
//            System.out.println("F2: " + cm1.f2());

            
            
//            System.out.println("Training sdt Time in milli seconds: "
//                    + (end_training_sdt - start_training_sdt));
//            System.out.println("Prediction sdt Time in milli seconds: "
//                    + (end_predicting_sdt - start_predicting_sdt));
//
//            System.out.println("Training drf Time in milli seconds: "
//                    + (end_training_drf - start_training_drf));
//            System.out.println("Prediction drf Time in milli seconds: "
//                    + (end_predicting_drf - start_predicting_drf));
            
        } finally {
            Scope.exit();
        }
    }



    @Test
    public void testCategoricalData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("a", "b", "b", "b", "a", "a", "a", "b", "b", "a"))
                    .withDataForCol(2, ard(1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            Scope.track_generic(train);


            SDTModel.SDTParameters p =
                    new SDTModel.SDTParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._max_depth = 5;
            p._limitNumSamplesForSplit = 2;
            p._response_column = "Prediction";

            SDT sdt = new SDT(p);
            SDTModel model = sdt.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(train.numRows(), out.numRows());


            System.out.println(DKV.getGet(model._output._treeKey));

            Frame test = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
//                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(1, ar("b", "b", "b", "b", "a", "a", "a", "a", "a", "a"))
                    .withColNames("First", "Second")
                    .build();
            Scope.track_generic(test);

            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output._treeKey)).getNodes()));
            System.out.println(String.join("\n", ((CompressedSDT) DKV.getGet(model._output._treeKey)).getListOfRules()));

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

}
