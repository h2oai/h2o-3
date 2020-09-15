package hex.genmodel.algos.deeplearning;

import hex.ModelCategory;
import hex.genmodel.CategoricalEncoding;
import hex.genmodel.DefaultCategoricalEncoding;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.utils.DistributionFamily;
import java.io.Serializable;

public class DeeplearningMojoModel extends MojoModel {
  public int _mini_batch_size;
  public int _nums; // number of numerical columns
  public int _cats; // number of categorical columns
  public int[] _catoffsets;
  public double[] _normmul;
  public double[] _normsub;
  public double[] _normrespmul;
  public double[] _normrespsub;
  public boolean _use_all_factor_levels;
  public String _activation;
  public String[] _allActivations;  // store activation function of all layers
  public boolean _imputeMeans;
  public int[] _units;  // size of neural network, input, hidden layers and output layer
  public double[] _all_drop_out_ratios; // input layer and hidden layers
  public StoreWeightsBias[] _weightsAndBias; // stores weights of different layers
  public int[] _catNAFill; // if mean imputation is true, mode imputation for categorical columns
  public int _numLayers;    // number of neural network layers.
  public DistributionFamily _family;
  protected String _genmodel_encoding;
  protected String[] _orig_names;
  protected String[][] _orig_domain_values;
  protected double[] _orig_projection_array;

  /***
   * Should set up the neuron network frame work here
   * @param columns
   * @param domains
   */
  DeeplearningMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  public void init() {
    _numLayers = _units.length-1;
    _allActivations = new String[_numLayers];
    int inputLayers = _numLayers-1;
    for (int index=0; index < (inputLayers); index++)
      _allActivations[index]=_activation;

    _allActivations[inputLayers] = this.isAutoEncoder()?_activation:(this.isClassifier()?"Softmax":"Linear");
  }

  /***
   * This method will be derived from the scoring/prediction function of deeplearning model itself.  However,
   * we followed closely what is being done in deepwater mojo.  The variable offset is not used.
   * @param dataRow
   * @param offset
   * @param preds
   * @return
   */
  @Override
  public final double[] score0(double[] dataRow, double offset, double[] preds) {
    assert(dataRow != null) : "doubles are null"; // check to make sure data is not null
    double[] neuronsInput = new double[_units[0]]; // store inputs into the neural network
    double[] neuronsOutput;  // save output from a neural network layer
    double[] _numsA = new double[_nums];
    int[] _catsA = new int[_cats];

    // transform inputs: NAs in categoricals are always set to new extra level.
    setInput(dataRow, neuronsInput, _numsA, _catsA, _nums, _cats, _catoffsets, _normmul, _normsub, _use_all_factor_levels, true);

    // proprogate inputs through neural network
    for (int layer=0; layer < _numLayers; layer++) {
      NeuralNetwork oneLayer = new NeuralNetwork(_allActivations[layer], _all_drop_out_ratios[layer],
              _weightsAndBias[layer], neuronsInput, _units[layer + 1]);
      neuronsOutput = oneLayer.fprop1Layer();
      neuronsInput = neuronsOutput;
    }
    if (!this.isAutoEncoder())
      assert (_nclasses == neuronsInput.length) : "nclasses " + _nclasses + " neuronsOutput.length " + neuronsInput.length;
    // Correction for classification or standardize outputs
    return modifyOutputs(neuronsInput, preds, dataRow);
  }

  public double[] modifyOutputs(double[] out, double[] preds, double[] dataRow) {
    if (this.isAutoEncoder()) { // only perform unscale numerical value if need
      if (_normmul != null && _normmul.length > 0) { // undo the standardization on output
        int nodeSize = out.length - _nums;
        for (int k = 0; k < nodeSize; k++) {
          preds[k] = out[k];
        }

        for (int k = 0; k < _nums; k++) {
          int offset = nodeSize + k;
          preds[offset] = out[offset] / _normmul[k] + _normsub[k];
        }
      } else {
        for (int k = 0; k < out.length; k++) {
          preds[k] = out[k];
        }
      }
    } else {
      if (_family == DistributionFamily.modified_huber) {
        preds[0] = -1;
        preds[2] = linkInv(_family, preds[0]);
        preds[1] = 1 - preds[2];
      } else if (this.isClassifier()) {
        assert (preds.length == out.length + 1);
        for (int i = 0; i < preds.length - 1; ++i) {
          preds[i + 1] = out[i];
          if (Double.isNaN(preds[i + 1])) throw new RuntimeException("Predicted class probability NaN!");
        }

        if (_balanceClasses)
          GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
        preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, dataRow, _defaultThreshold);
      } else {
        if (_normrespmul != null) //either both are null or none
          preds[0] = (out[0] / _normrespmul[0] + _normrespsub[0]);
        else
          preds[0] = out[0];
        // transform prediction to response space
        preds[0] = linkInv(_family, preds[0]);
        if (Double.isNaN(preds[0]))
          throw new RuntimeException("Predicted regression target NaN!");
      }
    }
    return preds;
  }

  
  /**
   * Calculate inverse link depends on distribution type
   * Be careful if you are changing code here - you have to change it in hex.LinkFunction too
   * @param distribution
   * @param f raw prediction
   * @return calculated inverse link value
   */
  private double linkInv(DistributionFamily distribution, double f){
    switch (distribution) {
      case bernoulli:
      case quasibinomial:
      case modified_huber:
      case ordinal:
        return 1/(1+Math.min(1e19, Math.exp(-f)));
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return Math.min(1e19, Math.exp(f));
      default:
        return f;
    }
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  public int getPredsSize(ModelCategory mc) {
    return (mc == ModelCategory.AutoEncoder)? _units[0]: (isClassifier()?nclasses()+1 :2);
  }

  /**
   * Calculates average reconstruction error (MSE).
   * Uses a normalization defined for the numerical features of the trained model.
   * @return average reconstruction error = ||original - reconstructed||^2 / length(original)
   */
  public double calculateReconstructionErrorPerRowData(double [] original, double [] reconstructed){
    assert (original != null && original.length > 0) && (reconstructed != null && reconstructed.length > 0);
    assert original.length == reconstructed.length;
    int numStartIndex = original.length - this._nums;
    double norm;
    double l2 = 0;
    for (int i = 0; i < original.length; i++) {
      norm = (this._normmul != null && this._normmul.length > 0 && this._nums > 0 && i >= numStartIndex) ? this._normmul[i-numStartIndex] : 1;
      l2 += Math.pow((reconstructed[i] - original[i]) * norm, 2);
    }
    return  l2 / original.length;
  }

  // class to store weight or bias for one neuron layer
  public static class StoreWeightsBias implements Serializable {
    float[] _wValues; // store weight or bias arrays
    double[] _bValues;

    StoreWeightsBias(float[] wvalues, double[] bvalues) {
      _wValues = wvalues;
      _bValues = bvalues;
    }
  }
  
  @Override
  public CategoricalEncoding getCategoricalEncoding() {
    switch (_genmodel_encoding) {
      case "AUTO":
      case "SortByResponse":
      case "OneHotInternal":
        return DefaultCategoricalEncoding.AUTO;
      case "Binary":
        return DefaultCategoricalEncoding.Binary;
      case "Eigen":
        return DefaultCategoricalEncoding.Eigen;
      case "LabelEncoder":
        return DefaultCategoricalEncoding.LabelEncoder;
      default:
        return null;
    }
  }
  
  @Override
  public String[] getOrigNames() {
    return _orig_names;
  }
  
  @Override
  public double[] getOrigProjectionArray() {
    return _orig_projection_array;
  }
  
  @Override
  public String[][] getOrigDomainValues() {
    return _orig_domain_values;
  }
}
