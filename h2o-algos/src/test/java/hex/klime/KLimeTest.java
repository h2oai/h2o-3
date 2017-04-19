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
      Frame expected = Scope.track(parse_test_file("smalldata/klime_test/titanic_default_expected.csv"));

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
      // check predicted_klime is correct
      assertVecEquals(expected.vec(0), scored.vec(0), 0.0001);

      // check the reason codes
      for (long i = 0; i < scored.numRows(); i++) {
        int cluster = (int) scored.vec(1).at8(i);
        GLMModel m = klm._output.getClusterModel(cluster);
        double intercept = m.coefficients().get("Intercept");
        double sum = 0;
        for (int j = 2; j < 7; j++)
          sum += scored.vec(j).at(i);
        assertEquals("Reason codes are correct for row " + i, scored.vec(0).at(i), sum + intercept, 0.0001);
      }
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