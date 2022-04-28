package hex.tree.sdt;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.fvec.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static water.TestUtil.*;
import static water.util.FileUtils.getFile;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class Output {


    @Test
    public void testMapReduceWithOutput() {
        // Each test should start with empty DKV and finish with empty DKV. We have a Scope class to help with that.
        Scope.enter();

        // Define testing frame
        Frame train = new TestFrameBuilder()
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                .withDataForCol(2, ard(1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
                .withColNames("First", "Second", "Prediction")
                .build();

        // Track the frame stored in DKV
        Scope.track(train);
        SDT sdt = new SDT(train, 2, 3);
        sdt.train();


        assertEquals(1, sdt.getRoot().getFeature().intValue());
        System.out.println("root threshold: " + sdt.getRoot().getThreshold());
        System.out.println(sdt.getRoot().getLeft().getDecisionValue());
        System.out.println(sdt.getRoot().getRight().getDecisionValue());
//        System.out.println(sdt.getRoot().getLeft().getFeature());
//
        Frame test = new TestFrameBuilder()
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                .withColNames("First", "Second")
                .build();

        Vec prediction = sdt.predict(test);
        System.out.println(prediction);
        assertEquals(1, prediction.at(0), 0.1);
        assertEquals(1, prediction.at(1), 0.1);
        assertEquals(0, prediction.at(2), 0.1);
        assertEquals(1, prediction.at(3), 0.1);
        assertEquals(0, prediction.at(4), 0.1);
        assertEquals(1, prediction.at(5), 0.1);
        assertEquals(0, prediction.at(6), 0.1);
        assertEquals(1, prediction.at(7), 0.1);
        assertEquals(1, prediction.at(8), 0.1);
        assertEquals(1, prediction.at(9), 0.1);

        test = new TestFrameBuilder()
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                .withDataForCol(1, ard(1.5, 1.5, 0.1, 100, -3, 1.5, 0, 12, 8.0, 0.0))
                .withColNames("First", "Second")
                .build();

        prediction = sdt.predict(test);
        System.out.println(prediction);
        assertEquals(1, prediction.at(0), 0.1);
        assertEquals(1, prediction.at(1), 0.1);
        assertEquals(0, prediction.at(2), 0.1);
        assertEquals(1, prediction.at(3), 0.1);
        assertEquals(0, prediction.at(4), 0.1);
        assertEquals(1, prediction.at(5), 0.1);
        assertEquals(0, prediction.at(6), 0.1);
        assertEquals(1, prediction.at(7), 0.1);
        assertEquals(1, prediction.at(8), 0.1);
        assertEquals(0, prediction.at(9), 0.1);

//        try {
//            File f = getFile("smalldata/prostate/prostate.csv");
//            NFSFileVec nfs = NFSFileVec.make(f);
//            Frame prostateData = water.parser.ParseDataset.parse(Key.make("prostateData"), nfs._key);
//            
//            prostateData
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

 
 
        
    }


//    @Test
//    public void testMapReduceWithOutput() {
//        try {
//            // Each test should start with empty DKV and finish with empty DKV. We have a Scope class to help with that.
//            Scope.enter();
//
//            // Define testing frame
//            Frame train = new TestFrameBuilder()
//                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
//                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
//                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
//                    .withColNames("First", "Second")
//                    .build();
//
//            // Track the frame stored in DKV
//            Scope.track(train);
//
////            assertEquals(1, train.vec(1).at8(0));
//
//            byte[] outputTypes = new byte[]{Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM};
////            Object[] outputTypes = Arrays.stream(train.names()).map(n -> Vec.T_NUM).collect(Collectors.toList()).;
//            Key<Frame> outputKey = Key.make("result_frame");
////            String[] outputColNames = (String[]) (Stream.concat(Arrays.stream(train.names()).map(n -> n + "Left"),
////                    Arrays.stream(train.names()).map(n -> n + "Right")).toArray()); //new String[]{"FirstLeft", "SecondLeft"};
//            String[] outputColNames = (Stream.concat(Arrays.stream(train.names()).map(n -> n + "Left"),
//                    Arrays.stream(train.names()).map(n -> n + "Right")).toArray(String[]::new)); //new String[]{"FirstLeft", "SecondLeft"};
//            String[] outputColNamesLeft = Arrays.stream(train.names()).map(n -> n + "Left").toArray(String[]::new);
//            String[] outputColNamesRight = Arrays.stream(train.names()).map(n -> n + "Right").toArray(String[]::new);
//            String[][] outputDomains = new String[][]{null, null, null, null};
//
//
//            // Define task
//            SDT.SplitFrameMRTask task = new SDT.SplitFrameMRTask(0, 3.0);
//
//            // Run task
//            task.doAll(outputTypes, train);
//
//            Frame result = task.outputFrame(
//                    null, // The output Frame will be stored in DKV and you can access it with this Key, can be null, in case you don't wanted in DKV
//                    outputColNames,
//                    outputDomains // Categorical columns need domain, pass null for Numerical and String columns
//            );
//
//            // Get result
//            Frame resultLeftSplit = result.subframe(outputColNamesLeft);
//            Frame resultRightSplit = result.subframe(outputColNamesRight);
////
////            Frame resultRightSplit = task.outputFrame(
////                    null, // The output Frame will be stored in DKV and you can access it with this Key, can be null, in case you don't wanted in DKV
////                    outputColNamesRight,
////                    outputDomains // Categorical columns need domain, pass null for Numerical and String columns
////            );
//            Scope.track(resultLeftSplit);
//            Scope.track(resultRightSplit);
//
//            // Expected result
//            Frame resultExpected = new TestFrameBuilder()
//                    .withColNames("First", "Second")
//                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
//                    .withDataForCol(0, ard(0.0, 1.0, 3.0, 7.0))
//                    .withDataForCol(1, ard(1.88, 1.5, 1.5, 1.5))
//                    .build();
//            Scope.track(resultExpected);
//
//            assertFrameEquals(resultExpected, resultLeftSplit, 1e-3);
//
////            // Get result form DKV manually
////            result = null;
////            result = DKV.get(outputKey).get();
////            // Key of result is already tracked not need to add Scope.track(result); here.
////            assertFrameEquals(resultExpected, result, 1e-3);
////
////            // Put result to the DKV manually
////            result = task.outputFrame(
////                    null,
////                    outputColNames,
////                    outputDomains // Categorical columns need domain, pass null for Numerical and String columns
////            );
////            result._key = Key.make();
////            DKV.put(result);
////
////            // Remove something from DKV manually
////            DKV.remove(result._key);
//////            DKV.remove(train._key);
//        }
//        finally {
//            // Delete all tracked keys in DKV, if DKV is empty -> test passed
//            Scope.exit();
//        }
//    }
}
