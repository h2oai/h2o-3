package hex.coxph;

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
 * Pins the CURRENT default behavior of an offset-trained CoxPH model that does NOT use
 * remove_offset_effects, so the generic core changes are provably no-ops on the flag=false path.
 *
 * CoxPH does not have the per-family link oracle used by GBM. Its prediction is a clean linear
 * predictor ("lp") and the offset enters the linear predictor with coefficient 1.0, so the exact
 * oracle   lp(withOffset) - lp(offsetZeroed) == offset   holds. We also assert the two weaker
 * baseline properties: default scoring is deterministic and the offset genuinely moves predictions.
 * remove_offset_effects intentionally NOT set (default false); no frozen arrays.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class CoxPHRemoveOffsetBaselineTest {

  @Test
  public void defaultScoringAppliesOffsetAndIsStable() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/coxph_test/heart.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train = train._key;
      parms._start_column = "start";
      parms._stop_column = "stop";
      parms._response_column = "event";
      parms._offset_column = "offset";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
      // NOTE: remove_offset_effects intentionally NOT set (default false)

      CoxPHModel model = new CoxPH(parms).trainModel().get();
      Scope.track_generic(model);

      // "lp" is the only prediction column (col 0)
      Frame predsA = Scope.track(model.score(train));
      Frame predsA2 = Scope.track(model.score(train));
      assertFrameEquals(predsA, predsA2, 0.0);            // default scoring is deterministic

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);
      Frame predsZero = Scope.track(model.score(zeroed));

      double maxErr = 0, maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++) {
        double lpWith = predsA.vec(0).at(r), lpZero = predsZero.vec(0).at(r);
        maxErr = Math.max(maxErr, Math.abs((lpWith - lpZero) - offset.at(r)));
        maxDiff = Math.max(maxDiff, Math.abs(lpWith - lpZero));
      }
      assertTrue("default scoring must apply the offset (predictions should differ)", maxDiff > 1e-6);
      assertTrue("lp(withOffset) - lp(offsetZeroed) must equal offset, err=" + maxErr, maxErr < 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
