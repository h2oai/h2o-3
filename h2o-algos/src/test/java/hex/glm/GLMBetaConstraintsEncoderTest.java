package hex.glm;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.parser.BufferedString;
import water.util.FrameUtils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
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
                FrameUtils.BetaConstraintsEncoder constraintsEncoder = new FrameUtils.BetaConstraintsEncoder(coefNames, coefOrigNames);
                Frame transformedFrame =  constraintsEncoder.doAll( beta_constraints[i].types(), beta_constraints[i]).outputFrame();
                transformedFrame.setNames(beta_constraints[i]._names);
                
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
    
}
