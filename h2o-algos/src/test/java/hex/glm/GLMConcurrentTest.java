package hex.glm;

import hex.quantile.Quantile;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;

import java.util.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class GLMConcurrentTest extends TestUtil {

    @Test
    public void testMulticlassGLM() throws Exception {
        final List<Job<GLMModel>> jobs = new LinkedList<>();
        Exception exception = null;
        try {
            Scope.enter();
            Frame train5 = new Frame();
            Frame train10 = new Frame();

            makeRandomTrainingFrames(10, 1000, train5, train10);

            for (int i = 0; i < 8; i++) {
                GLMModel.GLMParameters p = new GLMModel.GLMParameters();
                Frame train = i % 2 == 0 ? train5 : train10;
                p._train = train._key;
                p._response_column = train.lastVecName();
                p._family = GLMModel.GLMParameters.Family.multinomial;
                Job<GLMModel> glmJob = new GLM(p).trainModel();
                jobs.add(glmJob);
            }
        } finally {
            for (Job<GLMModel> job : jobs) {
                try {
                    GLMModel m = job.get();
                    if (m != null) {
                        m.delete();
                    }
                } catch (Exception e) {
                    Log.err(e);
                    exception = e;
                }
            }
            Scope.exit();
        }
        if (exception != null)
            throw exception;
    }

    private void makeRandomTrainingFrames(final int cols, final int rows, Frame train5, Frame train10) {
        Random rng = RandomUtils.getRNG(0xDAD);
        double[][] data = new double[cols][rows];
        double[] coefs = new double[cols];
        for (int c = 0; c < cols; c++) {
            coefs[c] = rng.nextDouble();
        }
        double[] sums = new double[rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                data[c][r] = rng.nextDouble();
                sums[r] += data[c][r] * coefs[c];
            }
        }
        Frame sumsFr = new TestFrameBuilder()
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, sums)
                .build();
        double[] q10 = quantiles(sumsFr, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0);
        String[] resp10 = new String[rows];
        String[] resp5 = new String[rows];
        for (int r = 0; r < rows; r++) {
            int pos = Arrays.binarySearch(q10, sums[r]);
            if (pos < 0)
                pos = -pos - 1;
            resp10[r] = Integer.toString(pos);
            resp5[r] = Integer.toString(pos / 2);
        }
        TestFrameBuilder b = new TestFrameBuilder()
                .withVecTypes(ArrayUtils.constAry(cols, Vec.T_NUM));
        for (int c = 0; c < cols; c++) {
            b.withDataForCol(c, data[c]);
        }
        Frame dataFr = b.build();
        Frame respFr = new TestFrameBuilder()
                .withColNames("r5", "r10")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, resp5)
                .withDataForCol(1, resp10)
                .build();

        train5._key = Key.make("train5");
        train5.add(dataFr);
        train5.add("r5", respFr.vec(0));
        Scope.track(train5);
        DKV.put(train5);

        train10._key = Key.make("train10");
        train10.add(dataFr);
        train10.add("r10", respFr.vec(1));
        DKV.put(train10);
        Scope.track(train10);
    }
    
    private static double[] quantiles(Frame f, double... qs) {
        double[] qv = new double[qs.length];
        for (int i = 0; i < qv.length; i++) {
            qv[i] = Quantile.calcQuantile(f.lastVec(), qs[i]);
        }
        return qv;
    }
    
}
