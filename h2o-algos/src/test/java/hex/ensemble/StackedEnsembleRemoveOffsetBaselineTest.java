package hex.ensemble;

import hex.Model;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertTrue;
import static water.TestUtil.*;

/**
 * Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
 *
 * Pins the CURRENT default behavior of an offset-trained StackedEnsemble that does NOT use
 * remove_offset_effects, so the generic core changes are provably no-ops on the flag=false path.
 *
 * NOTE: StackedEnsemble does NOT support remove_offset_effects (no supportsRemoveOffsetEffects()
 * override). This is a base-model baseline, not an SE feature test — it only confirms the generic
 * core changes leave the default (flag=false) SE offset path untouched.
 *
 * StackedEnsemble has no single-family link oracle (base models + metalearner), so we use the
 * simpler baseline oracle: default scoring is deterministic (re-score same SE twice -> identical)
 * and the offset genuinely changes predictions (predict(withOffset) != predict(offsetZeroed)).
 * remove_offset_effects intentionally NOT set (default false); no frozen arrays.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class StackedEnsembleRemoveOffsetBaselineTest {

  @Test
  public void gaussianOffsetAppliedAndStable() {
    checkOffsetBaseline(DistributionFamily.gaussian, "AGE", "CAPSULE", 0);
  }

  @Test
  public void binomialOffsetAppliedAndStable() {
    checkOffsetBaseline(DistributionFamily.bernoulli, "CAPSULE", "AGE", 2);
  }

  /**
   * @param family          distribution of the base models + SE
   * @param response        response column
   * @param ignoredExtra    the non-x, non-response numeric column to ignore (predictors are always
   *                        RACE,DPROS,DCAPS,PSA,VOL,GLEASON)
   * @param col             prediction column to compare (0 for gaussian, 2 = positive-class prob for binomial)
   */
  private void checkOffsetBaseline(DistributionFamily family, String response, String ignoredExtra, int col) {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (family == DistributionFamily.bernoulli)
        train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      String[] ignored = {"ID", ignoredExtra};

      GBMModel m1 = trainBaseGbm(train, response, family, ignored, 42);
      GBMModel m2 = trainBaseGbm(train, response, family, ignored, 7);

      StackedEnsembleParameters seParms = new StackedEnsembleParameters();
      seParms._train = train._key;
      seParms._response_column = response;
      seParms._offset_column = "offset";
      seParms._ignored_columns = ignored;
      seParms._base_models = new Key[]{m1._key, m2._key};
      seParms._seed = 42;

      StackedEnsembleModel se = new StackedEnsemble(seParms).trainModel().get();
      Scope.track_generic(se);

      Frame predsA = Scope.track(se.score(train));
      Frame predsA2 = Scope.track(se.score(train));
      assertFrameEquals(predsA, predsA2, 0.0);            // default scoring is deterministic

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);
      Frame predsZero = Scope.track(se.score(zeroed));

      double maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxDiff = Math.max(maxDiff, Math.abs(predsA.vec(col).at(r) - predsZero.vec(col).at(r)));
      assertTrue("default scoring must apply the offset (predictions should differ)", maxDiff > 1e-6);
    } finally {
      Scope.exit();
    }
  }

  private GBMModel trainBaseGbm(Frame train, String response, DistributionFamily family, String[] ignored, long seed) {
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = train._key;
    parms._response_column = response;
    parms._offset_column = "offset";
    parms._ignored_columns = ignored;
    parms._distribution = family;
    parms._nfolds = 3;
    parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
    parms._keep_cross_validation_predictions = true;
    parms._ntrees = 10;
    parms._max_depth = 3;
    parms._seed = seed;
    GBMModel model = new GBM(parms).trainModel().get();
    Scope.track_generic(model);
    return model;
  }
}
