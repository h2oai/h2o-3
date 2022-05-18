package hex.tree.sdt;

import hex.ConfusionMatrix;
import org.junit.Ignore;
import org.junit.Test;
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

import java.util.Arrays;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class SDTTest {


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
            p.depth = 3;
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

    @Test
    public void testSmallData() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate_train.csv"));
            Frame test = Scope.track(parseTestFile("smalldata/prostate/prostate_test.csv"));
            System.out.println(Arrays.toString(train.names()));

            
            SDTModel.SDTParameters p =
                    new SDTModel.SDTParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p.depth = 10;
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

            System.out.println("Scoring: " + model.testJavaScoring(test, out, 1e-3));
//            System.out.println(test.vec(p._response_column));
            // todo - some evaluation 
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
