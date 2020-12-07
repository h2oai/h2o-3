package water.test.util;

import hex.ConfusionMatrix;
import org.junit.Assert;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

public class ConfusionMatrixUtils {

    /**
     * Build the CM data from the actuals and predictions, using the default
     * threshold.  Print to Log.info if the number of classes is below the
     * print_threshold.  Actuals might have extra levels not trained on (hence
     * never predicted).  Actuals with NAs are not scored, and their predictions
     * ignored.
     */
    public static ConfusionMatrix buildCM(Vec actuals, Vec predictions) {
        if (!actuals.isCategorical()) throw new IllegalArgumentException("actuals must be categorical.");
        if (!predictions.isCategorical()) throw new IllegalArgumentException("predictions must be categorical.");
        Scope.enter();
        try {
            Vec adapted = predictions.adaptTo(actuals.domain());
            int len = actuals.domain().length;
            Frame fr = new Frame(actuals);
            fr.add("C2", adapted);
            CMBuilder cm = new CMBuilder(len).doAll(fr);
            return new ConfusionMatrix(cm._arr, actuals.domain());
        } finally {
            Scope.exit();
        }
    }

    public static void assertCMEqual(String[] expectedDomain, double[][] expectedCM, ConfusionMatrix actualCM) {
      Assert.assertArrayEquals("Expected domain differs",     expectedDomain,        actualCM._domain);
      double[][] acm = actualCM._cm;
      Assert.assertEquals("CM dimension differs", expectedCM.length, acm.length);
      for (int i=0; i < acm.length; i++) Assert.assertArrayEquals("CM row " +i+" differs!", expectedCM[i], acm[i],1e-10);
    }

    private static class CMBuilder extends MRTask<CMBuilder> {
        final int _len;
        double _arr[/*actuals*/][/*predicted*/];

        CMBuilder(int len) {
            _len = len;
        }

        @Override
        public void map(Chunk ca, Chunk cp) {
            // After adapting frames, the Actuals have all the levels in the
            // prediction results, plus any extras the model was never trained on.
            // i.e., Actual levels are at least as big as the predicted levels.
            _arr = new double[_len][_len];
            for (int i = 0; i < ca._len; i++)
                if (!ca.isNA(i))
                    _arr[(int) ca.at8(i)][(int) cp.at8(i)]++;
        }

        @Override
        public void reduce(CMBuilder cm) {
            ArrayUtils.add(_arr, cm._arr);
        }
    }
}
