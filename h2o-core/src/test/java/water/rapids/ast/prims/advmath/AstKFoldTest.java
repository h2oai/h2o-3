package water.rapids.ast.prims.advmath;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.TwoDimTable;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class AstKFoldTest extends TestUtil {
    @BeforeClass
    static public void setup() { stall_till_cloudsize(1); }

    private Frame fr = null;

    @Test public void basicKFoldTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3, 4, 5))
                .build();

        int numberOfFolds = 5;
        int randomSeed = new Random().nextInt();
        String tree = String.format("(kfold_column testFrame %d %d )", numberOfFolds, randomSeed);
        Val val = Rapids.exec(tree);
        Frame results = val.getFrame();

        fr = fr.add(results);

        assertTrue(fr.vec(1).at(0) < 5);
        assertTrue(fr.vec(1).at(1) < 5);
        assertTrue(fr.vec(1).at(2) < 5);
        assertTrue(fr.vec(1).at(3) < 5);
        assertTrue(fr.vec(1).at(4) < 5);

        results.delete();
    }

    @After
    public void afterEach() {
        fr.delete();
    }
}
