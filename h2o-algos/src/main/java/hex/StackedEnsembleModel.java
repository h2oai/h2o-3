package hex;

import water.DKV;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;

import java.util.Arrays;

import static hex.Model.Parameters.FoldAssignmentScheme.Modulo;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsembleModel extends Model<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {

  public ModelCategory modelCategory;
  public Frame commonTrainingFrame = null;
  public String responseColumn = null;
  public int nfolds = -1;

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

        if (modelCategory != aModel._output.getModelCategory())
          throw new H2OIllegalArgumentException("Base models are inconsistent: there is a mix of different categories of models: " + Arrays.toString(_parms._base_models));

        if (_output._domains.length != aModel._output._domains.length)
          throw new H2OIllegalArgumentException("Base models are inconsistent: there is a mix of different numbers of domains (categorical levels): " + Arrays.toString(_parms._base_models));

        Frame aTrainingFrame = aModel._parms.train();
        if (! commonTrainingFrame._key.equals(aTrainingFrame._key))
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different training frames.  Found: " + commonTrainingFrame._key + " and: " + aTrainingFrame._key + ".");

        if (! responseColumn.equals(aModel._parms._response_column))
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different response columns.  Found: " + responseColumn + " and: " + aModel._parms._response_column + ".");

        if (nfolds != aModel._parms._nfolds)
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different values for nfolds.");

        if (aModel._parms._nfolds < 2)
          throw new H2OIllegalArgumentException("Base model does not use cross-validation: " + aModel._parms._nfolds);

        if (aModel._parms._fold_assignment != Modulo)
          throw new H2OIllegalArgumentException("Base model does not use Modulo for cross-validation: " + aModel._parms._nfolds);

        if (! aModel._parms._keep_cross_validation_predictions)
          throw new H2OIllegalArgumentException("Base model does not keep cross-validation predictions: " + aModel._parms._nfolds);

        if (_parms._distribution != aModel._parms._distribution)
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different distributions.");
      } else {
        _output._isSupervised = aModel.isSupervised();
        this.modelCategory = aModel._output.getModelCategory();
        _output._domains = Arrays.copyOf(aModel._output._domains, aModel._output._domains.length);
        commonTrainingFrame = aModel._parms.train();
        responseColumn = aModel._parms._response_column;
        nfolds = aModel._parms._nfolds;
        _parms._distribution = aModel._parms._distribution;
        beenHere = true;
      }

    } // for all base_models

    if (null == aModel)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; " + _parms._base_models.length + " were specified but none of those were found: " + Arrays.toString(_parms._base_models));

  }

}

