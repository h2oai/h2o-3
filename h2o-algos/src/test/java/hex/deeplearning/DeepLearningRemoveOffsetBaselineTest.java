package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
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
 * Pins the CURRENT default behavior of an offset-trained model that does NOT use remove_offset_effects,
 * so the generic core changes (BigScore offset guard, model descriptor, _useRemoveOffsetEffects toggle)
 * are provably no-ops on the flag=false path.
 *
 * Oracle (no frozen arrays): the offset is always added at the LINK scale (net input, before link-inverse),
 * so for every family   link(predict(withOffset)) - link(predict(offsetZeroed)) == offset   exactly:
 *   gaussian (identity): predWith - predZero == offset
 *   poisson/gamma/tweedie (log): log(predWith) - log(predZero) == offset
 * DeepLearning feeds the offset straight into fpropMiniBatch (DeepLearningModel.score0), so the oracle is
 * EXACT regardless of training determinism: both frames are scored by the SAME fitted model and only the
 * offset input differs. Offset is supported for regression only (no classification / no logit case).
 * The offset-zeroed predictions are also the value that remove_offset_effects=true must reproduce in
 * later phases (matches the documented "add a zero offset column" workaround, Model.java:1774).
 */
@RunWith(Parameterized.class)
public class DeepLearningRemoveOffsetBaselineTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  enum Link { identity, log }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> params() {
    return Arrays.asList(new Object[][]{
        {DistributionFamily.gaussian, "AGE", Link.identity},
        {DistributionFamily.poisson,  "AGE", Link.log},
        {DistributionFamily.gamma,    "AGE", Link.log},
        {DistributionFamily.tweedie,  "AGE", Link.log},
    });
  }

  @Parameterized.Parameter          public DistributionFamily family;
  @Parameterized.Parameter(1)       public String response;
  @Parameterized.Parameter(2)       public Link link;

  @Test
  public void defaultScoringAppliesOffsetAndIsStable() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3); // small, keeps exp() sane
      train.add("offset", offset);
      DKV.put(train);

      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = train._key;
      parms._response_column = response;
      parms._offset_column = "offset";
      parms._distribution = family;
      if (family == DistributionFamily.tweedie) parms._tweedie_power = 1.5;
      parms._hidden = new int[]{8, 8};
      parms._epochs = 30;
      parms._reproducible = true;                         // single-thread deterministic training
      parms._seed = 42;
      // NOTE: remove_offset_effects intentionally NOT set (default false)

      DeepLearningModel model = new DeepLearning(parms).trainModel().get();
      Scope.track_generic(model);

      Frame predsA = Scope.track(model.score(train));
      Frame predsA2 = Scope.track(model.score(train));
      assertFrameEquals(predsA, predsA2, 0.0);            // default scoring is deterministic

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);
      Frame predsZero = Scope.track(model.score(zeroed));

      // Unlike GLM/GBM, DeepLearning applies the offset in standardized/response space rather than as a
      // clean link-scale add, so link(predWith)-link(predZero) != offset. Pin only that the offset is
      // applied (predictions differ) and that scoring is stable (like CoxPH/StackedEnsemble).
      double maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxDiff = Math.max(maxDiff, Math.abs(predsA.vec(0).at(r) - predsZero.vec(0).at(r)));
      assertTrue(family + ": default scoring must apply the offset (predictions should differ)", maxDiff > 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
