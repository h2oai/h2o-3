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

import static org.junit.Assert.assertTrue;

/**
 * Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
 *
 * Pins the CURRENT default behavior of an offset-trained XGBoost model that does NOT use remove_offset_effects,
 * so the generic core changes (BigScore offset guard, model descriptor, _useRemoveOffsetEffects toggle)
 * are provably no-ops on the flag=false path.
 *
 * Oracle (no frozen arrays): the offset is added as base_margin at the RAW score (link scale, before link-inverse),
 * so for every family   link(predict(withOffset)) - link(predict(offsetZeroed)) == offset   exactly:
 *   gaussian (identity, reg:squarederror): predWith - predZero == offset
 *   poisson/gamma/tweedie (log): log(predWith) - log(predZero) == offset
 *   bernoulli (logit, binary:logistic):    logit(p1_with) - logit(p1_zero) == offset
 * The offset-zeroed predictions are also the value that remove_offset_effects=true must reproduce in
 * later phases (matches the documented "add a zero offset column" workaround, Model.java:1774).
 *
 * See XGBoostModel.score0(data, preds, offset): with hasOffset() the offset is passed straight into
 * Predictor.predict(row, offset) as base_margin.
 *
 * NOTE on tolerance: XGBoost uses float32 internally, so the link-scale identity is only good to ~1e-5.
 * We use 1e-4 (vs GBM's 1e-6) to stay safely above float32 round-off.
 * DETERMINISM: force JAVA scoring so the re-score equality check is stable (native predict can vary).
 */
@RunWith(Parameterized.class)
public class XGBoostRemoveOffsetBaselineTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  enum Link { identity, log, logit }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> params() {
    return Arrays.asList(new Object[][]{
        {DistributionFamily.gaussian, "AGE",     Link.identity},
        {DistributionFamily.poisson,  "AGE",     Link.log},
        {DistributionFamily.gamma,    "AGE",     Link.log},
        {DistributionFamily.tweedie,  "AGE",     Link.log},
        {DistributionFamily.bernoulli,"CAPSULE", Link.logit},
    });
  }

  @Parameterized.Parameter          public DistributionFamily family;
  @Parameterized.Parameter(1)       public String response;
  @Parameterized.Parameter(2)       public Link link;

  @Test
  public void defaultScoringAppliesOffsetAndIsStable() {
    Scope.enter();
    try {
      // force Java scoring so the re-score determinism check is stable
      System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", "false");

      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (link == Link.logit) train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3); // small, keeps exp() sane
      train.add("offset", offset);
      DKV.put(train);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._response_column = response;
      parms._offset_column = "offset";
      parms._distribution = family;
      if (family == DistributionFamily.tweedie) parms._tweedie_power = 1.5;
      parms._ntrees = 20;
      parms._max_depth = 4;
      parms._seed = 42;
      // NOTE: remove_offset_effects intentionally NOT set (default false)

      XGBoostModel model = new XGBoost(parms).trainModel().get();
      Scope.track_generic(model);

      Frame predsA = Scope.track(model.score(train));
      Frame predsA2 = Scope.track(model.score(train));
      assertFrameEquals(predsA, predsA2, 0.0);            // default (Java) scoring is deterministic

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
      // 1e-4 tolerance: XGBoost is float32 internally (GBM uses 1e-6)
      assertTrue(family + ": link(predWith) - link(predZero) must equal offset, err=" + maxErr, maxErr < 1e-4);
    } finally {
      System.clearProperty("sys.ai.h2o.xgboost.predict.native.enable");
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
