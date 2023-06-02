package hex.isotonic;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.RandomUtils;

import java.util.Random;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PoolAdjacentViolatorsDriverTest extends TestUtil {

    @Test
    public void testPAV() {
        final int N = 10_000;
        final Random r = RandomUtils.getRNG(42);
        try {
            Scope.enter();

            final double[] ys = new double[N];
            final double[] xs = new double[N];
            final double[] ws = new double[N];
            for (int i = 0; i < N; i++) {
                ys[i] = r.nextDouble() - 0.5;
                xs[i] = (r.nextDouble() / 10) + ys[i];
                ws[i] = i % 10 == 0 ? 0 : r.nextDouble();
            }

            Frame fr = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ys)
                    .withDataForCol(1, xs)
                    .withDataForCol(2, ws)
                    .build();
            fr = ensureDistributed(fr);
            Scope.track(fr);

            final Frame thresholds = PoolAdjacentViolatorsDriver.runPAV(fr);
            Scope.track(thresholds);

            assertTrue(thresholds.numRows() > 10);
            
            double prevY = -1, prevX = -1;
            for (long i = 0; i < fr.numRows(); i++) {
                double currY = fr.vec(0).at(i);
                double currX = fr.vec(1).at(i);
                assertTrue("y, row " + i, currY >= prevY);
                assertTrue("x, row " + i, currX >= prevX);
            }
        } finally {
            Scope.exit();
        }
    }

}
