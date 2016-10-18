package hex;

import water.DKV;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.util.Log;

import java.util.Arrays;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsembleModel extends Model<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {

  public StackedEnsembleModel(Key selfKey, StackedEnsembleParameters parms) {
    super(selfKey, parms, new StackedEnsembleOutput());
  }
  
  public static class StackedEnsembleParameters extends Model.Parameters {
    public String algoName() { return "StackedEnsemble"; }
    public String fullName() { return "Stacked Ensemble"; }
    public String javaName() { return StackedEnsembleModel.class.getName(); }
    @Override public long progressUnits() { return 1; }  // TODO

    public static enum SelectionStrategy { choose_all }

    // TODO: make _selection_strategy an object:
    /** How do we choose which models to stack? */
    public SelectionStrategy _selection_strategy;

    /** Which models can we choose from? */
    public Key<Model> _base_models[];
  }

  public static class StackedEnsembleOutput extends Model.Output {
    public StackedEnsembleOutput() { super(); }
    public StackedEnsembleOutput(Job job) { _job = job; }
  }

  /**
   * @see Model#score0(double[], double[])
   */
  protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    throw new UnsupportedOperationException();
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw new UnsupportedOperationException();
  }


  public void checkAndInheritModelProperties() {
    if (null == _parms._base_models || 0 == _parms._base_models.length)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; found 0.");

    Model aModel = null;
    boolean beenHere = false;
    for (Key<Model> k : _parms._base_models) {
      aModel = DKV.getGet(k);
      if (null == aModel) {
        Log.info("Failed to find base model; skipping: " + k);
        continue;
      }


      if (beenHere) {
        // check that the base models are all consistent
        if (_output._isSupervised ^ aModel.isSupervised())
          throw new H2OIllegalArgumentException("Base models are inconsistent: there is a mix of supervised and unsupervised models: " + Arrays.toString(_parms._base_models));

        if (_output._domains.length != aModel._output._domains.length)
          throw new H2OIllegalArgumentException("Base models are inconsistent: there is a mix of different numbers of domains (categorical levels): " + Arrays.toString(_parms._base_models));

      } else {
        _output._isSupervised = aModel.isSupervised();
        _output._domains = Arrays.copyOf(aModel._output._domains, aModel._output._domains.length);
        beenHere = true;
      }

    }

    if (null == aModel)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; " + _parms._base_models.length + " were specified but none of those were found: " + Arrays.toString(_parms._base_models));


    if (null == aModel)
      throw new H2OIllegalArgumentException("Null base model. . .");

  }

}

