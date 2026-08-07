package hex.tree.xgboost;

import hex.genmodel.utils.DistributionFamily;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Flag-ON test for GH-16851 on XGBoost, exercised on BOTH scoring backends (Java predictor and native
 * booster). XGBoost's bulk scoring reads the offset itself (base margin), so this verifies the
 * applyOffsetAtScoreTime() guard threaded through setupBigScorePredict: remove_offset predictions are
 * invariant to the offset column, equal the identically-fit plain model on a zeroed offset, and both the
 * primary (restricted) and dual (unrestricted) metrics carry the advertised semantics.
 */
@RunWith(Parameterized.class)
public class XGBoostRemoveOffsetEffectTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Parameterized.Parameters(name = "javaScoring={0}, distribution={1}")
  public static Collection<Object[]> params() {
    // cover a log-link family (poisson) as well as gaussian: the zero-base-margin path is subtlest for
    // count/gamma objectives where the booster would otherwise re-add base_score
    return Arrays.asList(new Object[][]{
            {true, DistributionFamily.gaussian}, {false, DistributionFamily.gaussian},
            {true, DistributionFamily.poisson}, {false, DistributionFamily.poisson},
    });
  }

  @Parameterized.Parameter(0) public boolean javaScoring;
  @Parameterized.Parameter(1) public DistributionFamily distribution;

  private XGBoostModel train(Frame train, boolean removeOffset) {
    XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
    parms._train = train._key;
    parms._response_column = "AGE";
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._distribution = distribution;
    parms._ntrees = 10;
    parms._max_depth = 4;
    parms._seed = 42;
    XGBoostModel m = new XGBoost(parms).trainModel().get();
    Scope.track_generic(m);
    return m;
  }

  @Test
  public void removeOffsetEffectsScoresAsIfOffsetZero() {
    String prev = System.getProperty("sys.ai.h2o.xgboost.predict.native.enable");
    System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", String.valueOf(!javaScoring));
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);

      XGBoostModel ro = train(train, true);
      XGBoostModel plain = train(train, false);

      Frame roOnOffset = Scope.track(ro.score(train));
      Frame roOnZeroed = Scope.track(ro.score(zeroed));
      Frame plainOnZeroed = Scope.track(plain.score(zeroed));
      Frame plainOnOffset = Scope.track(plain.score(train));

      // remove_offset predictions ignore the offset column and equal the identically-fit plain model
      // scored with a zero offset (float32 tolerance)
      assertFrameEquals(roOnOffset, roOnZeroed, 1e-5);
      assertFrameEquals(roOnOffset, plainOnZeroed, 1e-4);
      double maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxDiff = Math.max(maxDiff, Math.abs(plainOnOffset.vec(0).at(r) - roOnOffset.vec(0).at(r)));
      assertTrue("offset should matter for the plain model", maxDiff > 1e-6);

      // primary training metric is offset-REMOVED: matches a fresh restricted scoring pass
      double roFreshRestricted = hex.ModelMetrics.getFromDKV(ro, train).mse();
      assertEquals("primary training metric should be offset-removed",
              roFreshRestricted, ro._output._training_metrics.mse(), 1e-4);
      // dual view: offset-applied metrics populated and match the plain model's metric
      assertNotNull("unrestricted training metrics should be populated",
              ro._output._training_metrics_unrestricted_model);
      double plainOffsetApplied = hex.ModelMetrics.getFromDKV(plain, train).mse();
      assertEquals("unrestricted metric should equal the offset-applied plain model's metric",
              plainOffsetApplied, ro._output._training_metrics_unrestricted_model.mse(), 1e-4);
      assertNotEquals("restricted vs unrestricted metrics should differ",
              ro._output._training_metrics_unrestricted_model.mse(), ro._output._training_metrics.mse(), 1e-4);
    } finally {
      if (prev == null) System.clearProperty("sys.ai.h2o.xgboost.predict.native.enable");
      else System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", prev);
      Scope.exit();
    }
  }
}
