package hex.deeplearning;

import hex.BigScore;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import water.H2O;
import water.Job;
import water.Key;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.Log;

import java.util.Arrays;

import static water.H2O.technote;

/**
 * Simple version of DLM.
 */
public abstract class SimpleDLM<
    M extends Model<M, DeepLearningParameters, O>, 
    O extends Model.Output> 
    extends Model<M, DeepLearningParameters, O> {
  protected final String unstable_msg = technote(4,
      "\n\nTrying to predict with an unstable model." +
          "\nJob was aborted due to observed numerical instability (exponential growth)."
          + "\nEither the weights or the bias values are unreasonably large or lead to large activation values."
          + "\nTry a different initial distribution, a bounded activation function (Tanh), adding regularization"
          + "\n(via max_w2, l1, l2, dropout) or learning rate (either enable adaptive_rate or use a smaller learning rate or faster annealing).");

  public SimpleDLM(Key<M> selfKey, DeepLearningParameters parms, O output) {
    super(selfKey, parms, output);
  }

  public boolean isUnstable() { return model_info().isUnstable(); }
  
  public int scoreSample(double[] sample) throws IllegalArgumentException {
    double[] out = feedOneRow(sample);

    System.out.println("Got score " + Arrays.toString(out));
    return out[0] > .5 ? 0 : 1;
  }

  protected double[] score0(double[] data) {
    double[] out = feedOneRow(data);
    return finalizePredictions(out);
  }
  
  @Override
  public double[] score0(Chunk chks[], double weight, double offset, int row_in_chunk, double[] tmp, double[] preds ) {
//    Thread.dumpStack();
    int rowIdx = (int)(chks[0].start() + row_in_chunk);
    return scoreRow(rowIdx);
  }

  public double[] scoreRow(int rowIdx) {
    double[] sample = model_info.parameters.trainData.row(rowIdx);
    double[] score = score0(sample);
    if(isSupervised()) {
      correctProbabilities(sample, score);
    }
    return score;
  }


  void assertStable() {
    if (isUnstable()) {
      Log.err(unstable_msg);
      throw new UnsupportedOperationException(unstable_msg);
    }
  }

  private double[] feedOneRow(double[] data) {
    assertStable();
    Neurons[] neurons = model_info.neuronsForTesting();
    final Neurons.Input neuron = (Neurons.Input) neurons[0];
    neuron.setInput(-1, data, 0);

    Neurons.runTestBatch(neurons, model_info);
    return neurons[neurons.length - 1]._a[0].raw();
  }

  abstract public DeepLearningParameters get_params();
  
  public DeepLearningModelInfo model_info() { return model_info; }

  volatile DeepLearningModelInfo model_info;

  protected double[] finalizePredictions(double[] out) {
    int nouts = numOutputColumns();
    double[] preds = new double[nouts];
    if (get_params()._distribution == DistributionFamily.modified_huber) {
      preds[0] = -1;
      preds[2] = _dist.linkInv(out[0]);
      preds[1] = 1-preds[2];
    } else if (_output.isClassifier()) {
      assert (preds.length == out.length + 1);
      for (int i = 0; i < preds.length - 1; ++i) {
        preds[i + 1] = out[i];
        if (Double.isNaN(preds[i + 1])) throw new RuntimeException("Predicted class probability NaN!");
      }
      // label assignment happens later - explicitly mark it as invalid here
      preds[0] = -1;
    } else {
      double normalized = model_info().data_info().normalize(out[0]);
      // transform prediction to response space
      preds[0] = _dist.linkInv(normalized);
      if (Double.isNaN(preds[0]))
        throw new RuntimeException("Predicted regression target NaN!");
    }
//    System.out.println("Predictions " + Arrays.toString(preds));
    return preds;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    throw H2O.unimpl("How did you get here?");
  }

  @Override protected BigScore buildBigScore(Frame adaptFrm, int nNames, boolean computeMetrics, String[] domain, boolean makePreds, Job j) {
    return new DLScore(this, domain,nNames,adaptFrm.means(),_output.hasWeights() && adaptFrm.find(_output.weightsName()) >= 0,computeMetrics, makePreds, j);
  }

}
