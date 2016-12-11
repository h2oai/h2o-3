package hex.deeplearning;

import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import water.Key;
import water.util.Log;

import java.util.Arrays;

import static water.H2O.technote;

/**
 * Simple version of DLM.
 */
public abstract class SimpleDLM<
    M extends Model<M, P, O>, 
    P extends Model.Parameters,
    O extends Model.Output> 
    extends Model<M, P, O> {
  protected final String unstable_msg = technote(4,
      "\n\nTrying to predict with an unstable model." +
          "\nJob was aborted due to observed numerical instability (exponential growth)."
          + "\nEither the weights or the bias values are unreasonably large or lead to large activation values."
          + "\nTry a different initial distribution, a bounded activation function (Tanh), adding regularization"
          + "\n(via max_w2, l1, l2, dropout) or learning rate (either enable adaptive_rate or use a smaller learning rate or faster annealing).");

  /**
   * Full constructor
   *
   * @param selfKey
   * @param parms
   * @param output
   */
  public SimpleDLM(Key<M> selfKey, P parms, O output) {
    super(selfKey, parms, output);
  }

  public double scoreSample(double[] sample) throws IllegalArgumentException {
    System.out.println("Scoring Sample-1");
    int nouts = numOutputColumns();
    System.out.println("Scoring Sample-2");
    
    double[] score = score0(sample, new double[nouts]);
    System.out.println("Scoring Sample-3");
    correctProbabilities(sample, score);
    System.out.println("Got score " + Arrays.toString(score));
    return score[0];
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return score0(data, preds, 1, 0);
  }
  
  abstract public P get_params();
  
  public DeepLearningModelInfo model_info() { return model_info; }

  volatile DeepLearningModelInfo model_info;

  protected double[] finalizePredictions(double[] preds, double[] out) {
    if (get_params()._distribution == DistributionFamily.modified_huber) {
      preds[0] = -1;
      preds[2] = _dist.linkInv(out[0]);
      preds[1] = 1-preds[2];
      return preds;
    } else if (_output.isClassifier()) {
      assert (preds.length == out.length + 1);
      for (int i = 0; i < preds.length - 1; ++i) {
        preds[i + 1] = out[i];
        if (Double.isNaN(preds[i + 1])) throw new RuntimeException("Predicted class probability NaN!");
      }
      // label assignment happens later - explicitly mark it as invalid here
      preds[0] = -1;
    } else {
      if (model_info().data_info()._normRespMul != null) //either both are null or none
        preds[0] = (out[0] / model_info().data_info()._normRespMul[0] + model_info().data_info()._normRespSub[0]);
      else
        preds[0] = out[0];
      // transform prediction to response space
      preds[0] = _dist.linkInv(preds[0]);
      if (Double.isNaN(preds[0]))
        throw new RuntimeException("Predicted regression target NaN!");
    }
    return preds;
  }

  /**
   * Predict from raw double values representing the data
   * @param data raw array containing categorical values (horizontalized to 1,0,0,1,0,0 etc.) and numerical values (0.35,1.24,5.3234,etc), both can contain NaNs
   * @param preds predicted label and per-class probabilities (for classification), predicted target (regression), can contain NaNs
   * @return preds, can contain NaNs
   */
  @Override
  public double[] score0(double[] data, double[] preds, double weight, double offset) {
    int mb=0;
    int n=1;

    if (model_info().isUnstable()) {
      Log.err(unstable_msg);
      throw new UnsupportedOperationException(unstable_msg);
    }
    Neurons[] neurons = Neurons.forTesting(model_info);
    ((Neurons.Input)neurons[0]).setInput(-1, data, mb);
/*
long seed, Neurons[] neurons, DeepLearningModelInfo minfo,
                                    DeepLearningModelInfo consensus_minfo, boolean training, double[] responses, double[] offset, int n
 */
    Neurons.fpropMiniBatch(
        /*seed*/-1, 
        neurons, 
        /*minfo*/model_info, 
        /*consensus_minfo*/null, 
        /*training*/false, 
        /*responses*/null, 
        new double[]{offset}, 
        n);
    double[] out = neurons[neurons.length - 1]._a[mb].raw();

    return finalizePredictions(preds, out);
  }
}
