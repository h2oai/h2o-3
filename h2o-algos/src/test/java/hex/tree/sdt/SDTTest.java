package hex.tree.sdt;

import hex.ConfusionMatrix;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
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
import static water.TestUtil.*;

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
            p.depth = 5;
            p._response_column = "Prediction";

            SDT sdt = new SDT(p);
            SDTModel model = sdt.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(train.numRows(), out.numRows());


            System.out.println(DKV.getGet(model._output.treeKey));

            Frame test = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withColNames("First", "Second")
                    .build();
            Scope.track_generic(test);

            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output.treeKey)).nodes));

            Frame prediction = model.score(test);
            Scope.track_generic(prediction);
            System.out.println(prediction);
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
        try {
            int depth = 20;
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate_train.csv"));
            Frame test = Scope.track(parseTestFile("smalldata/prostate/prostate_test.csv"));
            System.out.println(Arrays.toString(train.names()));
//            train.toCategoricalCol("CAPSULE");
            SDTModel.SDTParameters p =
                    new SDTModel.SDTParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p.depth = depth;
            p._response_column = "CAPSULE";
//             [ID, CAPSULE, AGE, RACE, DPROS, DCAPS, PSA, VOL, GLEASON]


            SDT sdt = new SDT(p);
            SDTModel model = sdt.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(test);
            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(test.numRows(), out.numRows());

            writePredictionsToFile("./predictions_prostate_sdt_depth" + p.depth + "_.csv", out.vec(0).toCategoricalVec(), p._response_column);
            System.out.println("Scoring: " + model.testJavaScoring(test, out, 1e-3));
//            System.out.println(test.vec(p._response_column));
            System.out.println(Arrays.toString(FrameUtils.asInts(out.vec(0))));
            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p._response_column))));

            ConfusionMatrix cm = ConfusionMatrixUtils.buildCM(
                    test.vec(p._response_column).toCategoricalVec(),
                    out.vec(0).toCategoricalVec());
            System.out.println("Accuracy: " + cm.accuracy());
            System.out.println("Precision: " + cm.precision());
            System.out.println("Recall: " + cm.recall());
            System.out.println("Specificity: " + cm.specificity());
            System.out.println("F1: " + cm.f1());
            System.out.println("F2: " + cm.f2());

            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output.treeKey)).nodes));


            train.toCategoricalCol("CAPSULE");
//            test.toCategoricalCol("CAPSULE");
            DRFModel.DRFParameters p1 =
                    new DRFModel.DRFParameters();
            p1._ntrees = 1;
            p1._max_depth = depth;
            p1._response_column = "CAPSULE";
            p1._train = train._key;
            p1._seed = 0xDECAF;

            DRF drf = new DRF(p1);
            DRFModel model1 = drf.trainModel().get();
            Scope.track_generic(model1);
            assertNotNull(model1);

            Frame out1 = model1.score(test);
            Scope.track_generic(out1);
            System.out.println(Arrays.toString(out1.names()));
            assertEquals(test.numRows(), out1.numRows());
            writePredictionsToFile("./predictions_prostate_drf_depth" + p1._max_depth + "_.csv", out1.vec(0).toCategoricalVec(), p1._response_column);


            System.out.println("Scoring: " + model1.testJavaScoring(test, out1, 1e-3));
//            System.out.println(test.vec(p._response_column));
            System.out.println(Arrays.toString(FrameUtils.asInts(out1.vec(0))));
            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p1._response_column))));

            ConfusionMatrix cm1 = ConfusionMatrixUtils.buildCM(
                    test.vec(p1._response_column).toCategoricalVec(),
                    out1.vec(0).toCategoricalVec());
            System.out.println("Accuracy: " + cm1.accuracy());
            System.out.println("Precision: " + cm1.precision());
            System.out.println("Recall: " + cm1.recall());
            System.out.println("Specificity: " + cm1.specificity());
            System.out.println("F1: " + cm1.f1());
            System.out.println("F2: " + cm1.f2());

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testAirlinesSmallData() {
//        H2O.ARGS.nthreads = 2;
        int depth = 5;
        int size = 27112;
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/testng/airlines_train_" + size + ".csv"));
            Frame test = Scope.track(parseTestFile("smalldata/testng/airlines_test_" + size + ".csv"));
//            Frame train = Scope.track(parseTestFile("smalldata/testng/airlines_train_preprocessed.csv"));
//            Frame test = Scope.track(parseTestFile("smalldata/testng/airlines_test_preprocessed.csv"));
            System.out.println(Arrays.toString(train.names()));
//            train.toCategoricalCol("IsDepDelayed");
//            test.toCategoricalCol("IsDepDelayed");
//            


            SDTModel.SDTParameters p =
                    new SDTModel.SDTParameters();
//            DRFModel.DRFParameters p =
//                    new DRFModel.DRFParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
//            p._ntrees = 10;
            p.depth = depth;
            p._response_column = "IsDepDelayed";
//             IsDepDelayed,fYear,fMonth,fDayofMonth,fDayOfWeek,UniqueCarrier,Origin,Dest,Distance


            SDT sdt = new SDT(p);
//            DRF sdt = new DRF(p);
            long start_training_sdt = System.currentTimeMillis();
            SDTModel model = sdt.trainModel().get();
            long end_training_sdt = System.currentTimeMillis();
            
//            DRFModel model = sdt.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            long start_predicting_sdt = System.currentTimeMillis();
            Frame out = model.score(test);
            long end_predicting_sdt = System.currentTimeMillis();

            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(test.numRows(), out.numRows());

//            writePredictionsToFile("./predictions_airlines_sdt_depth" + p.depth + ".csv", out.vec(0).toCategoricalVec(), p._response_column);
            writePredictionsToFile("./predictions_airlines_sdt_size" + size + ".csv", out.vec(0).toCategoricalVec(), p._response_column);

            System.out.println("Scoring: " + model.testJavaScoring(test, out, 1e-3));
//            System.out.println(test.vec(p._response_column));
            System.out.println(Arrays.toString(FrameUtils.asInts(out.vec(0).toCategoricalVec())));
            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p._response_column))));

            ConfusionMatrix cm = ConfusionMatrixUtils.buildCM(
                    test.vec(p._response_column).toCategoricalVec(),
                    out.vec(0).toCategoricalVec());
            System.out.println("SDT:");
            System.out.println("Accuracy: " + cm.accuracy());
            System.out.println("Precision: " + cm.precision());
            System.out.println("Recall: " + cm.recall());
            System.out.println("Specificity: " + cm.specificity());
            System.out.println("F1: " + cm.f1());
            System.out.println("F2: " + cm.f2());

//            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output.treeKey)).nodes));



            train.toCategoricalCol("IsDepDelayed");
//            test.toCategoricalCol("CAPSULE");
            DRFModel.DRFParameters p1 =
                    new DRFModel.DRFParameters();
            p1._ntrees = 1;
            p1._max_depth = depth;
            p1._response_column = "IsDepDelayed";
            p1._train = train._key;
            p1._seed = 0xDECAF;

            DRF drf = new DRF(p1);
            long start_training_drf = System.currentTimeMillis();
            DRFModel model1 = drf.trainModel().get();
            long end_training_drf = System.currentTimeMillis();
            
            assertNotNull(model1);
            Scope.track_generic(model1);
            
            long start_predicting_drf = System.currentTimeMillis();
            Frame out1 = model1.score(test);
            long end_predicting_drf = System.currentTimeMillis();

            System.out.println("Training sdt Time in milli seconds for size " + size + ": "
                    + (end_training_sdt - start_training_sdt));
            System.out.println("Prediction sdt Time in milli seconds for size " + size + ": "
                    + (end_predicting_sdt - start_predicting_sdt));

            System.out.println("Training drf Time in milli seconds for size " + size + ": "
                    + (end_training_drf - start_training_drf));
            System.out.println("Prediction drf Time in milli seconds for size " + size + ": "
                    + (end_predicting_drf - start_predicting_drf));
            
            
            Scope.track_generic(out1);
            System.out.println(Arrays.toString(out1.names()));
            assertEquals(test.numRows(), out1.numRows());
//            writePredictionsToFile("./predictions_airlines_drf_depth" + p1._max_depth + ".csv", out1.vec(0).toCategoricalVec(), p1._response_column);
            writePredictionsToFile("./predictions_airlines_drf_size" + size + ".csv", out1.vec(0).toCategoricalVec(), p1._response_column);


            System.out.println("Scoring: " + model1.testJavaScoring(test, out1, 1e-3));
//            System.out.println(test.vec(p._response_column));
            System.out.println(Arrays.toString(FrameUtils.asInts(out1.vec(0))));
            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p1._response_column))));

            ConfusionMatrix cm1 = ConfusionMatrixUtils.buildCM(
                    test.vec(p1._response_column).toCategoricalVec(),
                    out1.vec(0).toCategoricalVec());
            System.out.println("DRF:");
            System.out.println("Accuracy: " + cm1.accuracy());
            System.out.println("Precision: " + cm1.precision());
            System.out.println("Recall: " + cm1.recall());
            System.out.println("Specificity: " + cm1.specificity());
            System.out.println("F1: " + cm1.f1());
            System.out.println("F2: " + cm1.f2());
            
            
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBigDataSynthetic() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/yuliia/Dataset1_train.csv"));
            Frame test = Scope.track(parseTestFile("smalldata/yuliia/Dataset1_test.csv"));
            System.out.println(Arrays.toString(train.names()));
//            train.toCategoricalCol("IsDepDelayed");
//            test.toCategoricalCol("IsDepDelayed");
//            


            SDTModel.SDTParameters p =
                    new SDTModel.SDTParameters();
//            DRFModel.DRFParameters p =
//                    new DRFModel.DRFParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
//            p._ntrees = 10;
            p.depth = 5;
            p._response_column = "label";
//             IsDepDelayed,fYear,fMonth,fDayofMonth,fDayOfWeek,UniqueCarrier,Origin,Dest,Distance


            SDT sdt = new SDT(p);
//            DRF sdt = new DRF(p);
            long start_training_sdt = System.currentTimeMillis();
            SDTModel model = sdt.trainModel().get();
            long end_training_sdt = System.currentTimeMillis();

//            DRFModel model = sdt.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            long start_predicting_sdt = System.currentTimeMillis();
            Frame out = model.score(test);
            long end_predicting_sdt = System.currentTimeMillis();

            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(test.numRows(), out.numRows());

//            writePredictionsToFile("./predictions_airlines_sdt_depth" + p.depth + ".csv", out.vec(0).toCategoricalVec(), p._response_column);
//            writePredictionsToFile("./predictions_airlines_sdt_size" + size + ".csv", out.vec(0).toCategoricalVec(), p._response_column);

            System.out.println("Scoring: " + model.testJavaScoring(test, out, 1e-3));
//            System.out.println(test.vec(p._response_column));
//            System.out.println(Arrays.toString(FrameUtils.asInts(out.vec(0).toCategoricalVec())));
//            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p._response_column))));

            ConfusionMatrix cm = ConfusionMatrixUtils.buildCM(
                    test.vec(p._response_column).toCategoricalVec(),
                    out.vec(0).toCategoricalVec());
            System.out.println("SDT:");
            System.out.println("Accuracy: " + cm.accuracy());
            System.out.println("Precision: " + cm.precision());
            System.out.println("Recall: " + cm.recall());
            System.out.println("Specificity: " + cm.specificity());
            System.out.println("F1: " + cm.f1());
            System.out.println("F2: " + cm.f2());

//            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output.treeKey)).nodes));



            train.toCategoricalCol("label");
//            test.toCategoricalCol("CAPSULE");
            DRFModel.DRFParameters p1 =
                    new DRFModel.DRFParameters();
            p1._ntrees = 1;
            p1._max_depth = 5;
            p1._response_column = "label";
            p1._train = train._key;
            p1._seed = 0xDECAF;

            DRF drf = new DRF(p1);
            long start_training_drf = System.currentTimeMillis();
            DRFModel model1 = drf.trainModel().get();
            long end_training_drf = System.currentTimeMillis();

            assertNotNull(model1);
            Scope.track_generic(model1);

            long start_predicting_drf = System.currentTimeMillis();
            Frame out1 = model1.score(test);
            long end_predicting_drf = System.currentTimeMillis();

            System.out.println("Training sdt Time in milli seconds: "
                    + (end_training_sdt - start_training_sdt));
            System.out.println("Prediction sdt Time in milli seconds: "
                    + (end_predicting_sdt - start_predicting_sdt));

            System.out.println("Training drf Time in milli seconds: "
                    + (end_training_drf - start_training_drf));
            System.out.println("Prediction drf Time in milli seconds: "
                    + (end_predicting_drf - start_predicting_drf));


            Scope.track_generic(out1);
            System.out.println(Arrays.toString(out1.names()));
            assertEquals(test.numRows(), out1.numRows());
//            writePredictionsToFile("./predictions_airlines_drf_depth" + p1._max_depth + ".csv", out1.vec(0).toCategoricalVec(), p1._response_column);
//            writePredictionsToFile("./predictions_airlines_drf_size" + size + ".csv", out1.vec(0).toCategoricalVec(), p1._response_column);


            System.out.println("Scoring: " + model1.testJavaScoring(test, out1, 1e-3));
//            System.out.println(test.vec(p._response_column));
//            System.out.println(Arrays.toString(FrameUtils.asInts(out1.vec(0))));
//            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p1._response_column))));

            ConfusionMatrix cm1 = ConfusionMatrixUtils.buildCM(
                    test.vec(p1._response_column).toCategoricalVec(),
                    out1.vec(0).toCategoricalVec());
            System.out.println("DRF:");
            System.out.println("Accuracy: " + cm1.accuracy());
            System.out.println("Precision: " + cm1.precision());
            System.out.println("Recall: " + cm1.recall());
            System.out.println("Specificity: " + cm1.specificity());
            System.out.println("F1: " + cm1.f1());
            System.out.println("F2: " + cm1.f2());


        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBigDataCreditCard() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/yuliia/creditcard_train.csv"));
            Frame test = Scope.track(parseTestFile("smalldata/yuliia/creditcard_test.csv"));
            System.out.println(Arrays.toString(train.names()));
//            train.toCategoricalCol("IsDepDelayed");
//            test.toCategoricalCol("IsDepDelayed");
//            


            SDTModel.SDTParameters p =
                    new SDTModel.SDTParameters();
//            DRFModel.DRFParameters p =
//                    new DRFModel.DRFParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
//            p._ntrees = 10;
            p.depth = 5;
            p._response_column = "Class";
//             IsDepDelayed,fYear,fMonth,fDayofMonth,fDayOfWeek,UniqueCarrier,Origin,Dest,Distance


            SDT sdt = new SDT(p);
//            DRF sdt = new DRF(p);
            long start_training_sdt = System.currentTimeMillis();
            SDTModel model = sdt.trainModel().get();
            long end_training_sdt = System.currentTimeMillis();

//            DRFModel model = sdt.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            long start_predicting_sdt = System.currentTimeMillis();
            Frame out = model.score(test);
            long end_predicting_sdt = System.currentTimeMillis();

            Scope.track_generic(out);
            System.out.println(Arrays.toString(out.names()));
            assertEquals(test.numRows(), out.numRows());

//            writePredictionsToFile("./predictions_airlines_sdt_depth" + p.depth + ".csv", out.vec(0).toCategoricalVec(), p._response_column);
//            writePredictionsToFile("./predictions_airlines_sdt_size" + size + ".csv", out.vec(0).toCategoricalVec(), p._response_column);

            System.out.println("Scoring: " + model.testJavaScoring(test, out, 1e-3));
//            System.out.println(test.vec(p._response_column));
//            System.out.println(Arrays.toString(FrameUtils.asInts(out.vec(0).toCategoricalVec())));
//            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p._response_column))));

            ConfusionMatrix cm = ConfusionMatrixUtils.buildCM(
                    test.vec(p._response_column).toCategoricalVec(),
                    out.vec(0).toCategoricalVec());
            System.out.println("SDT:");
            System.out.println("Accuracy: " + cm.accuracy());
            System.out.println("Precision: " + cm.precision());
            System.out.println("Recall: " + cm.recall());
            System.out.println("Specificity: " + cm.specificity());
            System.out.println("F1: " + cm.f1());
            System.out.println("F2: " + cm.f2());

//            System.out.println(Arrays.deepToString(((CompressedSDT) DKV.getGet(model._output.treeKey)).nodes));



            train.toCategoricalCol("Class");
//            test.toCategoricalCol("CAPSULE");
            DRFModel.DRFParameters p1 =
                    new DRFModel.DRFParameters();
            p1._ntrees = 1;
            p1._max_depth = 5;
            p1._response_column = "Class";
            p1._train = train._key;
            p1._seed = 0xDECAF;

            DRF drf = new DRF(p1);
            long start_training_drf = System.currentTimeMillis();
            DRFModel model1 = drf.trainModel().get();
            long end_training_drf = System.currentTimeMillis();

            assertNotNull(model1);
            Scope.track_generic(model1);

            long start_predicting_drf = System.currentTimeMillis();
            Frame out1 = model1.score(test);
            long end_predicting_drf = System.currentTimeMillis();

            System.out.println("Training sdt Time in milli seconds: "
                    + (end_training_sdt - start_training_sdt));
            System.out.println("Prediction sdt Time in milli seconds: "
                    + (end_predicting_sdt - start_predicting_sdt));

            System.out.println("Training drf Time in milli seconds: "
                    + (end_training_drf - start_training_drf));
            System.out.println("Prediction drf Time in milli seconds: "
                    + (end_predicting_drf - start_predicting_drf));


            Scope.track_generic(out1);
            System.out.println(Arrays.toString(out1.names()));
            assertEquals(test.numRows(), out1.numRows());
//            writePredictionsToFile("./predictions_airlines_drf_depth" + p1._max_depth + ".csv", out1.vec(0).toCategoricalVec(), p1._response_column);
//            writePredictionsToFile("./predictions_airlines_drf_size" + size + ".csv", out1.vec(0).toCategoricalVec(), p1._response_column);


            System.out.println("Scoring: " + model1.testJavaScoring(test, out1, 1e-3));
//            System.out.println(test.vec(p._response_column));
//            System.out.println(Arrays.toString(FrameUtils.asInts(out1.vec(0))));
//            System.out.println(Arrays.toString(FrameUtils.asInts(test.vec(p1._response_column))));

            ConfusionMatrix cm1 = ConfusionMatrixUtils.buildCM(
                    test.vec(p1._response_column).toCategoricalVec(),
                    out1.vec(0).toCategoricalVec());
            System.out.println("DRF:");
            System.out.println("Accuracy: " + cm1.accuracy());
            System.out.println("Precision: " + cm1.precision());
            System.out.println("Recall: " + cm1.recall());
            System.out.println("Specificity: " + cm1.specificity());
            System.out.println("F1: " + cm1.f1());
            System.out.println("F2: " + cm1.f2());


        } finally {
            Scope.exit();
        }
    }


//    @Test
//    @Ignore("Old test without integration")
//    public void testMapReduceWithOutput() {
//        // Each test should start with empty DKV and finish with empty DKV. We have a Scope class to help with that.
//        Scope.enter();
//
//        // Define testing frame
//        Frame train = new TestFrameBuilder()
//                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
//                .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
//                .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
//                .withDataForCol(2, ard(1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
//                .withColNames("First", "Second", "Prediction")
//                .build();
//
//        // Track the frame stored in DKV
//        Scope.track(train);
//        SDT sdt = new SDT(train, 3);
//        sdt.trainModelImpl();
//
//
//        assertEquals(1, sdt.getRoot().getFeature().intValue());
//        System.out.println("root threshold: " + sdt.getRoot().getThreshold());
//        System.out.println(sdt.getRoot().getLeft().getDecisionValue());
//        System.out.println(sdt.getRoot().getRight().getDecisionValue());
////        System.out.println(sdt.getRoot().getLeft().getFeature());
////
//        Frame test = new TestFrameBuilder()
//                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
//                .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
//                .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
//                .withColNames("First", "Second")
//                .build();
//
//        Vec prediction = sdt.predict(test);
//        System.out.println(prediction);
//        assertEquals(1, prediction.at(0), 0.1);
//        assertEquals(1, prediction.at(1), 0.1);
//        assertEquals(0, prediction.at(2), 0.1);
//        assertEquals(1, prediction.at(3), 0.1);
//        assertEquals(0, prediction.at(4), 0.1);
//        assertEquals(1, prediction.at(5), 0.1);
//        assertEquals(0, prediction.at(6), 0.1);
//        assertEquals(1, prediction.at(7), 0.1);
//        assertEquals(1, prediction.at(8), 0.1);
//        assertEquals(1, prediction.at(9), 0.1);
//
//        test = new TestFrameBuilder()
//                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
//                .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
//                .withDataForCol(1, ard(1.5, 1.5, 0.1, 100, -3, 1.5, 0, 12, 8.0, 0.0))
//                .withColNames("First", "Second")
//                .build();
//
//        prediction = sdt.predict(test);
//        System.out.println(prediction);
//        assertEquals(1, prediction.at(0), 0.1);
//        assertEquals(1, prediction.at(1), 0.1);
//        assertEquals(0, prediction.at(2), 0.1);
//        assertEquals(1, prediction.at(3), 0.1);
//        assertEquals(0, prediction.at(4), 0.1);
//        assertEquals(1, prediction.at(5), 0.1);
//        assertEquals(0, prediction.at(6), 0.1);
//        assertEquals(1, prediction.at(7), 0.1);
//        assertEquals(1, prediction.at(8), 0.1);
//        assertEquals(0, prediction.at(9), 0.1);
//        assertEquals(0, prediction.at(100), 0.1); // fail
//        System.out.println("Asserts success");
//        
//    }

}
