package hex.genmodel.algos.deeplearning;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;

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
  public StoreWeightsBias[] _weights; // stores weights of different layers
  public StoreWeightsBias[] _bias;    // store bias of different layers
  public int[] _catNAFill; // if mean imputation is true, mode imputation for categorical columns
  public int _numLayers;    // number of neural network layers.

  /***
   * Should set up the neuron network frame work here
   * @param columns
   * @param domains
   */
  DeeplearningMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  public void init() {
    _numLayers = _units.length-1;
    _allActivations = new String[_numLayers];
    int inputLayers = _numLayers-1;
    for (int index=0; index < (inputLayers); index++)
      _allActivations[index]=_activation;
    _allActivations[inputLayers] = this.isClassifier()?"Softmax":"Linear";
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
    float[] input2Neurons = new float[_units[0]]; // store inputs into the neural network
    double[] neuronsOutput;  // save output from a neural network layer
    double[] neuronsInput;    // store input to neural network layer

    // transform inputs: NAs in categoricals are always set to new extra level.
    setInput(dataRow, input2Neurons, _nums, _cats, _catoffsets, _normmul, _normsub, _use_all_factor_levels, true);
    neuronsInput = convertFloat2Double(input2Neurons);


    // proprogate inputs through neural network
    for (int layer=0; layer < _numLayers; layer++) {
      NeuralNetwork oneLayer = new NeuralNetwork(_allActivations[layer], _all_drop_out_ratios[layer], _weights[layer],
              _bias[layer], neuronsInput, _units[layer+1]);
      neuronsOutput = oneLayer.fprop1Layer();
      neuronsInput = neuronsOutput;
    }
    assert(_nclasses == neuronsInput.length) : "nclasses " + _nclasses + " neuronsOutput.length " + neuronsInput.length;
    // Correction for classification or standardize outputs
    if (this.isClassifier()) {
    for (int i = 0; i < neuronsInput.length; ++i)
      preds[1 + i] = neuronsInput[i];
    if (_balanceClasses)
      GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
    preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, dataRow, _defaultThreshold);
  } else {
    if (_normrespmul!=null && _normrespsub!=null)
      preds[0] = neuronsInput[0] / _normrespmul[0] + _normrespsub[0];
    else
      preds[0] = neuronsInput[0];
  }
    return preds;
  }

  /***
   * replace the missing numerical columns with column mean.
   * @param input
   * @return
   */
  private double[] imputeMissingWithMeans(float[] input) {
    double[] out = new double[input.length];
    int catNum = input.length-_nums;

    for (int index = 0; index < catNum; index++) {
      out[index] = (double) input[index];
    }
    if (_normsub != null) {
      for (int index = catNum; index < input.length; index++) {
        if (Double.isNaN(input[index]))
          out[index] = _normsub[index-catNum];
        else
          out[index] = (double) input[index];
      }
    } else {
      for (int index = catNum; index < input.length; index++) {
        if (Double.isNaN(input[index]))
          out[index] = 0.0;
        else
          out[index] = (double) input[index];
      }
    }
    return out;
  }

  public static double[] convertFloat2Double(float[] input) {
    int arraySize = input.length;
    double[] output = new double[arraySize];
    for (int index=0; index<arraySize; index++)
      output[index] = (double) input[index];
    return output;
  }

  public double[] fprop(float[] input2Neurons) {

    double[] outputs = new double[_units[-1]];  // initiate neural network outputs


    return outputs;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  // class to store weight or bias for one neuron layer
  public static class StoreWeightsBias {
    double[] _wOrBValues; // store weight or bias arrays

    StoreWeightsBias(double[] values) {
      _wOrBValues = values;
    }
  }
}
