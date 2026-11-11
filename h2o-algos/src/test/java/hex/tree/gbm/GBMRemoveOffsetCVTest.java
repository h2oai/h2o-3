package hex.tree.gbm;

import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static water.TestUtil.*;

/**
 * Phase 3 for GH-16851: cross-validation with remove_offset_effects on a non-GLM algo.
 * The per-fold holdout scoring runs through BigScore, so the primary CV metrics are offset-removed;
 * ModelBuilder also aggregates the parallel offset-applied per-fold builders into the unrestricted CV view.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class GBMRemoveOffsetCVTest {

  private GBMModel train(Frame train, boolean removeOffset) {
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = train._key;
    parms._response_column = "AGE";
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._distribution = DistributionFamily.gaussian;
    parms._ntrees = 15;
    parms._max_depth = 4;
    parms._seed = 42;
    parms._nfolds = 3;
    parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo; // deterministic folds
    GBMModel m = new GBM(parms).trainModel().get();
    Scope.track_generic(m);
    return m;
  }

  @Test
  public void cvRestrictedAndUnrestrictedMetrics() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      GBMModel ro = train(train, true);
      GBMModel plain = train(train, false);

      assertNotNull("restricted (offset-removed) CV metrics", ro._output._cross_validation_metrics);
      assertNotNull("unrestricted (offset-applied) CV metrics", ro._output._cross_validation_metrics_unrestricted_model);

      double restricted = ro._output._cross_validation_metrics.mse();
      double unrestricted = ro._output._cross_validation_metrics_unrestricted_model.mse();
      // offset matters, so the two CV views differ
      assertNotEquals("restricted vs unrestricted CV metrics should differ", unrestricted, restricted, 1e-6);
      // ro's fit+folds are identical to the plain model (remove_offset only changes scoring), so ro's
      // offset-applied CV view matches the plain model's CV metric
      assertEquals("unrestricted CV should match the identically-fit plain model's CV",
              plain._output._cross_validation_metrics.mse(), unrestricted, 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
