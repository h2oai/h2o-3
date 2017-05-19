package hex.klime;

import hex.glm.GLMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.*;

public class KLimeTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testTitanicDefault() throws Exception {
    Scope.enter();
    try {
      Frame fr = loadTitanicData();

      KLimeModel.KLimeParameters p = new KLimeModel.KLimeParameters();
      p._seed = 12345;
      p._ignored_columns = new String[]{"PassengerId", "Survived", "predict", "p0"};
      p._train = fr._key;
      p._response_column = "p1";

      KLimeModel klm = (KLimeModel) Scope.track_generic(new KLime(p).trainModel().get());

      Frame scored = Scope.track(klm.score(fr));

      assertArrayEquals(
              new String[]{"predict_klime", "cluster_klime", "rc_Pclass", "rc_Sex", "rc_Age", "rc_SibSp", "rc_Parch"},
              scored._names
      );

      double[][] actualCenters = klm._output._clustering._output._centers_std_raw;
      double[][] expectedCenters = new double[][]{
              {2.0, 1.0, 0.1735, -0.2466, -0.4059},
              {2.0, 0.0, -0.5876, 0.1605, 1.711},
              {2.0, 1.0, -1.0921, 3.5446, 1.5566}
      };

      for (int i = 0; i < expectedCenters.length; i++)
        assertArrayEquals("Center matches for cluster" + i, expectedCenters[i], actualCenters[i], 0.001);

      // check that prediction can be calculated from reason codes + intercept
      for (long i = 0; i < scored.numRows(); i++) {
        int cluster = (int) scored.vec(1).at8(i);
        GLMModel m = klm._output.getClusterModel(cluster);
        double intercept = m.coefficients().get("Intercept");
        double sum = 0;
        for (int j = 2; j < 7; j++)
          sum += scored.vec(j).at(i);
        assertEquals("Reason codes are correct for row " + i, scored.vec(0).at(i), sum + intercept, 0.0001);
      }

      assertTrue(klm._output._training_metrics instanceof KLimeModel.ModelMetricsKLime);
      KLimeModel.ModelMetricsKLime tm = (KLimeModel.ModelMetricsKLime) klm._output._training_metrics;
      assertArrayEquals(new boolean[]{false, false, false}, tm._usesGlobalModel);
      assertEquals(3, tm._clusterMetrics.length);
    } finally {
      Scope.exit();
    }
  }

  private static Frame loadTitanicData() {
    Key<Frame> titanic = Key.<Frame>make("titanic");
    Frame fr = Scope.track(parse_test_file(titanic, "smalldata/klime_test/titanic_input.csv"));
    fr.replace(0, fr.vec(0).toCategoricalVec());
    DKV.put(fr);
    return fr;
  }

}