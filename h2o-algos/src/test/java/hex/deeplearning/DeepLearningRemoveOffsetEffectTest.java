package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.utils.DistributionFamily;
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
 * GH-16851 flag-ON test for Deep Learning.
 * <p>
 * DL applies the offset in STANDARDIZED response space and only for strictly positive values
 * ({@code DeepLearningTask.fpropMiniBatch}: {@code if (offset[mb] > 0) a.add(0, (offset - sub) * mul)}),
 * while {@code DeepLearningModel.score0} destandardizes as {@code out/mul + sub}. So:
 * <pre>
 *   offset &gt; 0 : pred = a/mul + offset
 *   offset &le; 0 : pred = a/mul + sub      (sub = mean(response))
 * </pre>
 * The offset therefore REPLACES the response-mean term rather than adding to it. Naively scoring a
 * remove_offset model with offset=0 takes the second branch and biases every prediction by mean(response);
 * the correct offset-free prediction is {@code a/mul}, obtained by applying the same formula with offset=0
 * instead of skipping it.
 * <p>
 * NOTE ON THE OLD TEST: the version of this test deleted in 9ab82b4745 used offsets in [-0.3, +0.3], which are
 * mostly &le; 0. Those rows never entered the offset branch at all, so its oracle
 * ({@code ro.score(f) == plain.score(zeroedOffset)}) compared two equally biased quantities and passed.
 * Every offset here is strictly positive so the branch is actually exercised.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class DeepLearningRemoveOffsetEffectTest {

  private static final String RESPONSE = "AGE";

  private DeepLearningModel train(Frame train, boolean removeOffset) {
    DeepLearningParameters parms = new DeepLearningParameters();
    parms._train = train._key;
    parms._response_column = RESPONSE;
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._distribution = DistributionFamily.gaussian;
    parms._hidden = new int[]{8, 8};
    parms._epochs = 20;
    parms._reproducible = true;
    parms._seed = 42;
    // remove_offset_effects must not change the FIT, so nothing that selects on a metric may be active:
    // early stopping and best-model selection both read the training metrics, which differ between the views.
    parms._stopping_rounds = 0;
    parms._overwrite_with_best_model = false;
    DeepLearningModel m = new DeepLearning(parms).trainModel().get();
    Scope.track_generic(m);
    return m;
  }

  /** prostate + a STRICTLY POSITIVE offset column, so DL's {@code offset > 0} branch is always taken. */
  private Frame makeTrain() {
    Frame train = Scope.track(TestUtil.parseTestFile("./smalldata/prostate/prostate.csv"));
    Vec offset = Scope.track(train.anyVec().makeCon(0));
    for (long i = 0; i < offset.length(); i++) offset.set(i, 1.0 + 0.5 * (i % 7)); // 1.0 .. 4.0
    train.add("offset", offset);
    DKV.put(train);
    return train;
  }

  private Frame zeroOffset(Frame train) {
    Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
    zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
    DKV.put(zeroed);
    return zeroed;
  }

  @Test
  public void removeOffsetPredictionsIgnoreTheOffsetColumn() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      DeepLearningModel ro = train(train, true);
      Frame onOffset = Scope.track(ro.score(train));
      Frame onZeroed = Scope.track(ro.score(zeroOffset(train)));
      TestUtil.assertFrameEquals(onOffset, onZeroed, 0.0);
    } finally {
      Scope.exit();
    }
  }

  /**
   * The load-bearing oracle. remove_offset_effects does not change the fit, so for the same network
   * {@code pred_plain(offset) = a/mul + offset} and {@code pred_removeOffset = a/mul}; the difference must
   * therefore be exactly the offset, row by row.
   */
  @Test
  public void removeOffsetPredictionEqualsPlainPredictionMinusTheOffset() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      DeepLearningModel ro = train(train, true);
      DeepLearningModel plain = train(train, false);

      Frame roPreds = Scope.track(ro.score(train));
      Frame plainPreds = Scope.track(plain.score(train));
      Vec offset = train.vec("offset");

      double maxErr = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxErr = Math.max(maxErr, Math.abs((plainPreds.vec(0).at(r) - roPreds.vec(0).at(r)) - offset.at(r)));
      assertEquals("plain(offset) - removeOffset(row) must equal the offset exactly", 0.0, maxErr, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  /**
   * Pins the actual defect. Scoring a remove_offset model must NOT be the same as scoring the identically-fit
   * plain model on a zeroed offset column: the latter skips DL's offset branch and so carries the
   * response-mean term, i.e. it is biased by mean(response). Before the fix the two agreed and this fails.
   */
  @Test
  public void removeOffsetIsNotTheBiasedZeroOffsetScoring() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      DeepLearningModel ro = train(train, true);
      DeepLearningModel plain = train(train, false);

      Frame roPreds = Scope.track(ro.score(train));
      Frame plainOnZeroed = Scope.track(plain.score(zeroOffset(train)));

      double meanResponse = train.vec(RESPONSE).mean();
      double maxErr = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxErr = Math.max(maxErr, Math.abs((plainOnZeroed.vec(0).at(r) - roPreds.vec(0).at(r)) - meanResponse));
      assertEquals("zero-offset scoring is biased by exactly mean(response) relative to the offset-free view",
              0.0, maxErr, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  /** The offset-applied ("unrestricted") dual view must equal the identically-fit plain model's metric. */
  @Test
  public void dualViewMatchesThePlainModel() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      DeepLearningModel ro = train(train, true);
      DeepLearningModel plain = train(train, false);
      Scope.track(plain.score(train));

      assertNotNull("unrestricted training metrics should be populated",
              ro._output._training_metrics_unrestricted_model);
      assertEquals("unrestricted metric should equal the offset-applied plain model's metric",
              hex.ModelMetrics.getFromDKV(plain, train).mse(),
              ro._output._training_metrics_unrestricted_model.mse(), 1e-6);
      assertNotEquals("restricted vs unrestricted metrics should differ",
              ro._output._training_metrics_unrestricted_model.mse(),
              ro._output._training_metrics.mse(), 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
