package hex.glm;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.FrameUtils;

import java.util.Arrays;
import static org.junit.Assert.*;
import static water.fvec.Vec.T_NUM;
import static water.fvec.Vec.T_STR;


public class GLMBetaConstraintsEncoderTest extends TestUtil {

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testBetaConstraintsEncoder() {
        Scope.enter();
        try {
            Frame [] beta_constraints = {
                    new TestFrameBuilder()
                    .withColNames("names", "lower_bound", "upper_bound")
                    .withVecTypes(T_STR, T_NUM, T_NUM)
                    .withDataForCol(0, new String[] {"AGE"})
                    .withDataForCol(1, new double [] {1.0})
                    .withDataForCol(2, new double[] {10.0})
                    .build(),
                    new TestFrameBuilder()
                            .withColNames("names", "lower_bound", "upper_bound")
                            .withVecTypes(T_STR, T_NUM, T_NUM)
                            .withDataForCol(0, new String[] {"WEIGHT", "AGE.1", "AGE", "HEIGHT.1"})
                            .withDataForCol(1, new double [] {1.0, 2.0, 3.0, 4.0})
                            .withDataForCol(2, new double[] {10.0, 11.0, 12.0, 13.0})
                            .build()};

            Frame expected_transformed_frame[] = {
                    new TestFrameBuilder()
                            .withColNames("names", "lower_bound", "upper_bound")
                            .withVecTypes(T_STR, T_NUM, T_NUM)
                            .withDataForCol(0, new String[] {"AGE.1", "AGE.2", "AGE.3"})
                            .withDataForCol(1, new double [] {1.0, 1.0, 1.0})
                            .withDataForCol(2, new double[] {10.0, 10.0, 10.0})
                            .build(),
                    new TestFrameBuilder()
                            .withColNames("names", "lower_bound", "upper_bound")
                            .withVecTypes(T_STR, T_NUM, T_NUM)
                            .withDataForCol(0, new String[] {"WEIGHT","AGE.1", "AGE.1", "AGE.2", "AGE.3", "HEIGHT.1"})
                            .withDataForCol(1, new double [] {1.0, 2.0, 3.0,3.0, 3.0, 4.0})
                            .withDataForCol(2, new double[] {10.0,11.0,12.0,12.0,12.0,13.0})
                            .build()};

            String[] coefNames = new String[] {"AGE.1", "AGE.2", "AGE.3", "WEIGHT", "HEIGHT.1", "HEIGHT.2", "HEIGHT.3"};
            String[] coefOrigNames = new String[] {"AGE", "WEIGHT", "HEIGHT"};

            for (int i = 0; i < beta_constraints.length; i++) {
                Frame transformedFrame = FrameUtils.encodeBetaConstraints(null, coefNames, coefOrigNames, beta_constraints[i]);
                testFramesEqual(expected_transformed_frame[i], transformedFrame);
            }
            
        } finally {
            Scope.exit();
        }
    }

    void testFramesEqual(Frame frame1, Frame frame2) {
        BufferedString tmpStr = new BufferedString();
        for (int j = 0; j < frame1.anyVec().length(); j++) {
            assertTrue(frame1.vec(0).atStr(tmpStr, j).equals(frame2.vec(0).atStr(tmpStr, j)));
            assertEquals(frame1.vec(1).at(j), frame2.vec(1).at(j), 1e-1);
            assertEquals(frame1.vec(2).at(j), frame2.vec(2).at(j), 1e-1);
        }
    }

    @Test
    public void testBetaConstraintsEncoderSWFailingCase() {
        Scope.enter();
        try {
//            12-11 12:18:10.841 192.168.1.10:54321    7062       FJ-1-3 DEBUG water.default: Encoding beta constraints...
//            12-11 12:18:10.841 192.168.1.10:54321    7062       FJ-1-3 DEBUG water.default: _dinfo.coefNames() content: [AGE, RACE, DPROS, DCAPS, PSA, VOL, GLEASON]
//            12-11 12:18:10.841 192.168.1.10:54321    7062       FJ-1-3 DEBUG water.default: _dinfo.coefOriginalNames() content: [AGE, RACE, DPROS, DCAPS, PSA, VOL]
//          
//            +-------+------------+------------+----------+---+
//            |names  |lower_bounds|upper_bounds|beta_given|rho|
//            +-------+------------+------------+----------+---+
//            |AGE    |-1000       |1000        |1         |0.2|
//            |RACE   |-1000       |1000        |1         |0.2|
//            |DPROS  |-1000       |1000        |1         |0.2|
//            |DCAPS  |-1000       |1000        |1         |0.2|
//            |PSA    |-1000       |1000        |1         |0.2|
//            |VOL    |-1000       |1000        |1         |0.2|
//            |GLEASON|-1000       |1000        |1         |0.2|
//            +-------+------------+------------+----------+---+            
//
            Frame beta_constraints = new TestFrameBuilder()
                    .withColNames("names", "lower_bounds", "upper_bounds", "beta_given", "rho")
                    .withVecTypes(T_STR, T_NUM, T_NUM, T_NUM, T_NUM)
                    .withDataForCol(0, new String[] {"AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"})
                    .withDataForCol(1, new double [] {-1000.0, -1000.0, -1000.0, -1000.0, -1000.0, -1000.0, -1000.0})
                    .withDataForCol(2, new double[] {1000.0, 1000.0, 1000.0, 1000.0, 1000.0, 1000.0, 1000.0})
                    .withDataForCol(3, new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0})
                    .withDataForCol(4, new double[] {0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2})
                    .build();

            String[] coefNames = new String[] {"AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
            String[] coefOrigNames = new String[] {"AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL"};

            Frame transformedFrame = FrameUtils.encodeBetaConstraints(null, coefNames, coefOrigNames, beta_constraints);
            Vec namesCol = transformedFrame.vec("names");
            assert transformedFrame.vec("names").isString();
            String[] dom = new String[(int) namesCol.length()];
            int[] map = new int[dom.length];
            BufferedString tmpStr = new BufferedString();
            for (int i = 0; i < dom.length; ++i) {
                dom[i] = namesCol.atStr(tmpStr, i).toString();
                map[i] = i;
            }
            // check for duplicates
            String[] sortedDom = dom.clone();
            Arrays.sort(sortedDom);
            for (int i = 1; i < sortedDom.length; ++i)
                assertFalse(sortedDom[i - 1].equals(sortedDom[i]));

        } finally {
            Scope.exit();
        }
    }


}
