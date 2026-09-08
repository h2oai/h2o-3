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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.*;

/**
 * Phase 6 for GH-16851: CoxPH is the one offset algo that bypasses Model.BigScore (it uses CoxPHScore),
 * so its remove_offset support lives in CoxPHScore (the appended offset coefficient is 0 instead of 1).
 *
 * remove_offset_effects only affects scoring, not the fit, so a remove_offset model and a plain model
 * trained identically share the same coefficients. Therefore the remove_offset lp is invariant to the
 * offset column and equals the plain model's lp scored with a zeroed offset.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class CoxPHRemoveOffsetEffectTest {

  private CoxPHModel train(Frame train, boolean removeOffset) {
    CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
    parms._train = train._key;
    parms._start_column = "start";
    parms._stop_column = "stop";
    parms._response_column = "event";
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
    parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
    CoxPHModel m = new CoxPH(parms).trainModel().get();
    Scope.track_generic(m);
    return m;
  }

  @Test
  public void removeOffsetEffectsScoresAsIfOffsetZero() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/coxph_test/heart.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);

      CoxPHModel ro = train(train, true);
      CoxPHModel plain = train(train, false);

      Frame roOnOffset = Scope.track(ro.score(train));
      Frame roOnZeroed = Scope.track(ro.score(zeroed));
      Frame plainOnZeroed = Scope.track(plain.score(zeroed));
      Frame plainOnOffset = Scope.track(plain.score(train));

      assertFrameEquals(roOnOffset, roOnZeroed, 0.0);        // remove_offset lp ignores the offset column
      assertFrameEquals(roOnOffset, plainOnZeroed, 1e-8);    // == identically-fit plain model with zero offset
      double maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxDiff = Math.max(maxDiff, Math.abs(plainOnOffset.vec(0).at(r) - roOnOffset.vec(0).at(r)));
      assertTrue("offset should matter for the plain model", maxDiff > 1e-6);

      // dual view: the offset-applied ("unrestricted") pass goes through CoxPHScore with _scoreWithOffset,
      // so the field must be populated (CoxPH bypasses BigScore — this exercises the CoxPH-specific guard)
      assertNotNull("unrestricted training metrics should be populated",
              ro._output._training_metrics_unrestricted_model);
    } finally {
      Scope.exit();
    }
  }
}
