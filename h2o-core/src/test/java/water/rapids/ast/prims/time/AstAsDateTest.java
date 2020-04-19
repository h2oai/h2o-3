package water.rapids.ast.prims.time;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;

import static org.junit.Assert.*;

public class AstAsDateTest extends TestUtil {
    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void recognizeSingleDigitDay() {
        Frame frame = null;
        Frame convertedFrame = null;
        try {
            Scope.enter();
            frame = Scope.track(new TestFrameBuilder().withName("SingleDigitDayFrame")
                    .withColNames("C1")
                    .withVecTypes(Vec.T_STR)
                    .withDataForCol(0, new String[]{"2013/11/05", "2013/11/5"})
                    .build());

            convertedFrame = Rapids.exec("(as.Date SingleDigitDayFrame '%Y/%m/%d')").getFrame();
            assertNotNull(convertedFrame);

            assertEquals(Vec.T_NUM, convertedFrame.vec(0).get_type());
            assertEquals(2,convertedFrame.numRows());
            assertEquals(convertedFrame.vec(0).at8(0), convertedFrame.vec(0).at8(1));

        } finally {
            Scope.exit();
            if (frame != null) frame.remove();
            if (convertedFrame != null) convertedFrame.remove();
        }
    }


    @Test
    public void recognizeSingleDigitMonth() {
        Frame frame = null;
        Frame convertedFrame = null;
        try {
            Scope.enter();
            frame = Scope.track(new TestFrameBuilder().withName("SingleDigitDayFrame")
                    .withColNames("C1")
                    .withVecTypes(Vec.T_STR)
                    .withDataForCol(0, new String[]{"2013/01/05", "2013/1/05"})
                    .build());

            convertedFrame = Rapids.exec("(as.Date SingleDigitDayFrame '%Y/%m/%d')").getFrame();
            assertNotNull(convertedFrame);

            assertEquals(Vec.T_NUM, convertedFrame.vec(0).get_type());
            assertEquals(2,convertedFrame.numRows());
            assertEquals(convertedFrame.vec(0).at8(0), convertedFrame.vec(0).at8(1));
        } finally {
            Scope.exit();
            if (frame != null) frame.remove();
            if (convertedFrame != null) convertedFrame.remove();
        }
    }

}
