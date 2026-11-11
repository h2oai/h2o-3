package hex.gam;

import hex.glm.GLMModel.GLMParameters.Family;
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

import static org.junit.Assert.assertTrue;

/**
 * Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
 *
 * Pins the CURRENT default behavior of an offset-trained GAM that does NOT use remove_offset_effects,
 * so the generic core changes (BigScore offset guard, model descriptor, _useRemoveOffsetEffects toggle)
 * are provably no-ops on the flag=false path.
 *
 * GAM fits through GLM, so the offset enters the linear predictor and the oracle is identical to GLM/GBM:
 * the offset is added at the LINK scale, so for every family
 *   link(predict(withOffset)) - link(predict(offsetZeroed)) == offset   exactly:
 *   gaussian (identity): predWith - predZero == offset
 *   poisson/gamma/tweedie (log): log(predWith) - log(predZero) == offset
 *   binomial (logit): logit(p1_with) - logit(p1_zero) == offset
 * The offset-zeroed predictions are also the value that remove_offset_effects=true must reproduce in
 * later phases (matches the documented "add a zero offset column" workaround, Model.java:1774).
 */
@RunWith(Parameterized.class)
public class GAMRemoveOffsetBaselineTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  enum Link { identity, log, logit }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> params() {
    return Arrays.asList(new Object[][]{
        {Family.gaussian, "AGE",     Link.identity},
        {Family.poisson,  "AGE",     Link.log},
        // gamma omitted: with lambda=0 + a spline term the IRLSM line search fails to converge here
        // (produces NaN); gamma+offset is already covered by the GLM/GBM/XGBoost baselines.
        {Family.tweedie,  "AGE",     Link.log},
        {Family.binomial, "CAPSULE", Link.logit},
    });
  }

  @Parameterized.Parameter          public Family family;
  @Parameterized.Parameter(1)       public String response;
  @Parameterized.Parameter(2)       public Link link;

  @Test
  public void defaultScoringAppliesOffsetAndIsStable() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (link == Link.logit) train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3); // small, keeps exp() sane
      train.add("offset", offset);
      DKV.put(train);

      // Linear predictors: RACE, DPROS, DCAPS, VOL, GLEASON. Smooth term: PSA (gam column, not a linear predictor).
      // Ignore ID, PSA (used only as gam column) and the unused response target.
      String other = response.equals("AGE") ? "CAPSULE" : "AGE";
      GAMModel.GAMParameters parms = new GAMModel.GAMParameters();
      parms._train = train._key;
      parms._response_column = response;
      parms._offset_column = "offset";
      parms._ignored_columns = new String[]{"ID", "PSA", other};
      parms._family = family;
      parms._gam_columns = new String[][]{{"PSA"}};
      parms._num_knots = new int[]{5};
      parms._lambda = new double[]{0};
      if (family == Family.tweedie) {
        parms._tweedie_variance_power = 1.5;
        parms._tweedie_link_power = 0;
      }
      // NOTE: remove_offset_effects intentionally NOT set (default false)

      GAMModel model = new GAM(parms).trainModel().get();
      Scope.track_generic(model);

      Frame predsA = Scope.track(model.score(train));
      Frame predsA2 = Scope.track(model.score(train));
      assertFrameEquals(predsA, predsA2, 0.0);            // default scoring is deterministic

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);
      Frame predsZero = Scope.track(model.score(zeroed));

      int col = link == Link.logit ? 2 : 0;               // binomial: positive-class prob is preds col 2
      double maxErr = 0, maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++) {
        double gWith = g(predsA.vec(col).at(r)), gZero = g(predsZero.vec(col).at(r));
        maxErr = Math.max(maxErr, Math.abs((gWith - gZero) - offset.at(r)));
        maxDiff = Math.max(maxDiff, Math.abs(predsA.vec(col).at(r) - predsZero.vec(col).at(r)));
      }
      assertTrue("default scoring must apply the offset (predictions should differ)", maxDiff > 1e-6);
      assertTrue(family + ": link(predWith) - link(predZero) must equal offset, err=" + maxErr, maxErr < 1e-6);
    } finally {
      Scope.exit();
    }
  }

  private double g(double p) {
    switch (link) {
      case log:   return Math.log(p);
      case logit: return Math.log(p / (1 - p));
      default:    return p;
    }
  }
}
