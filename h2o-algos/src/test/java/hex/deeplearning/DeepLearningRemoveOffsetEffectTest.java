package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.utils.DistributionFamily;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

/**
 * Flag-ON test for GH-16851 on DeepLearning. DL scores through Model.BigScore, so remove_offset predictions
 * ignore the offset column; with reproducible training and early stopping disabled the fit is identical to a
 * plain model, so restricted predictions equal the plain model scored on a zeroed offset, and the dual
 * (offset-applied) metric view matches the plain model's metric.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class DeepLearningRemoveOffsetEffectTest {

  private DeepLearningModel train(Frame train, boolean removeOffset) {
    DeepLearningParameters parms = new DeepLearningParameters();
    parms._train = train._key;
    parms._response_column = "AGE";
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._distribution = DistributionFamily.gaussian;
    parms._hidden = new int[]{8, 8};
    parms._epochs = 20;
    parms._reproducible = true;
    parms._seed = 42;
    parms._stopping_rounds = 0; // no early stopping -> the fit cannot depend on the metric view
    // best-model selection also uses training metrics (restricted vs offset-applied view would pick
    // different epochs) -> disable so the two fits are identical
    parms._overwrite_with_best_model = false;
    DeepLearningModel m = new DeepLearning(parms).trainModel().get();
    Scope.track_generic(m);
    return m;
  }

  @Test
  public void removeOffsetEffectsScoresAsIfOffsetZero() {
    Scope.enter();
    try {
      Frame train = Scope.track(TestUtil.parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);

      DeepLearningModel ro = train(train, true);
      DeepLearningModel plain = train(train, false);

      Frame roOnOffset = Scope.track(ro.score(train));
      Frame roOnZeroed = Scope.track(ro.score(zeroed));
      Frame plainOnZeroed = Scope.track(plain.score(zeroed));
      Frame plainOnOffset = Scope.track(plain.score(train));

      TestUtil.assertFrameEquals(roOnOffset, roOnZeroed, 0.0);   // predictions ignore the offset column
      TestUtil.assertFrameEquals(roOnOffset, plainOnZeroed, 1e-6); // == identically-fit plain model, zero offset
      double maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxDiff = Math.max(maxDiff, Math.abs(plainOnOffset.vec(0).at(r) - roOnOffset.vec(0).at(r)));
      assertTrue("offset should matter for the plain model", maxDiff > 1e-6);

      // dual view populated and matching the plain model's offset-applied metric
      assertNotNull("unrestricted training metrics should be populated",
              ro._output._training_metrics_unrestricted_model);
      double plainOffsetApplied = hex.ModelMetrics.getFromDKV(plain, train).mse();
      assertEquals("unrestricted metric should equal the offset-applied plain model's metric",
              plainOffsetApplied, ro._output._training_metrics_unrestricted_model.mse(), 1e-6);
      assertNotEquals("restricted vs unrestricted metrics should differ",
              ro._output._training_metrics_unrestricted_model.mse(), ro._output._training_metrics.mse(), 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
