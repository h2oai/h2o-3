package hex.genmodel.algos.deeplearning;

import java.util.Arrays;
import java.util.List;

import static hex.genmodel.algos.deeplearning.ActivationUtils.*;

public class NeuralNetwork {  // represent one layer of neural network
  public String _activation;    // string that describe the activation function
  double _drop_out_ratio;              // drop_out_ratio for that layer
  public DeeplearningMojoModel.StoreWeightsBias _weights;  // store layer weight
  public DeeplearningMojoModel.StoreWeightsBias _bias;      // store layer bias
  public double[] _inputs;     // store input to layer
  public double[] _outputs;    // layer output
  public int _outSize;         // number of nodes in this layer
  public int _inSize;         // number of inputs to this layer
  public int _maxK=1;
  List<String> _validActivation = Arrays.asList("Linear", "Softmax", "ExpRectifierWithDropout", "ExpRectifier",
          "Rectifier", "RectifierWithDropout", "MaxoutWithDropout", "Maxout", "TanhWithDropout", "Tanh");

  public NeuralNetwork(String activation, double drop_out_ratio, DeeplearningMojoModel.StoreWeightsBias weights,
                       DeeplearningMojoModel.StoreWeightsBias bias, double[] inputs, int outSize) {
    validateInputs(activation, drop_out_ratio, weights._wOrBValues.length, bias._wOrBValues.length, inputs.length,
            outSize);
    _activation=activation;
    _drop_out_ratio=drop_out_ratio;
    _weights=weights;
    _bias=bias;
    _inputs=inputs;
    _outSize=outSize;
    _inSize=_inputs.length;
    _outputs = new double[_outSize];
    if ("Maxout".equals(_activation) || "MaxoutWithDropout".equals(_activation)) {
      _maxK = bias._wOrBValues.length/outSize;
    }
  }

  public double[] fprop1Layer() {
    double[] input2ActFun = formNNInputs();
    ActivationFunctions createActivations = createActFuns(_activation); // choose activation function
    return createActivations.eval(input2ActFun, _drop_out_ratio, _maxK); // apply activation function to form NN outputs
  }

  public double[] formNNInputs() {
    double[] input2ActFun = new double[_outSize*_maxK];

    for (int k = 0; k < _maxK; k++) {
      for (int row = 0; row < _outSize; row++) {
        int countInd = _maxK*row+k;
        input2ActFun[countInd] = _bias._wOrBValues[countInd];  //
        for (int col = 0; col < _inSize; col++) {
          input2ActFun[countInd] += _inputs[col] * _weights._wOrBValues[_maxK*(row*_inSize+col)+k];
        }
      }
    }
    return input2ActFun;
  }

  public void validateInputs(String activation, double drop_out_ratio, int weightLen, int biasLen, int inSize,
                             int outSize) {
    assert (_validActivation.contains(activation)) : "activation must be one of \"Linear\", \"Softmax\", " +
            "\"ExpRectifierWithDropout\", \"ExpRectifier\", \"Rectifier\", \"RectifierWithDropout\", \"MaxoutWithDropout\", " +
            "\"Maxout\", \"TanhWithDropout\", \"Tanh\"";
    // use mod to take care of Maxout networks
    assert (weightLen % (inSize * outSize) == 0) : "Your neural network layer number of input * number " +
            "of outputs should equal length of your weight vector";
    assert ((biasLen % outSize) == 0) : "Number of bias should equal number of nodes in your nerual network" +
            " layer.";
    assert (drop_out_ratio >= 0 && drop_out_ratio < 1) : "drop_out_ratio must be >=0 and < 1.";
    assert (outSize > 0) : "number of nodes in neural network must exceed 0.";

  }

  public ActivationFunctions createActFuns(String activation) {
    switch (activation) {
      case "Linear":
        return new LinearOut();
      case "Softmax":
        return new SoftmaxOut();
      case "ExpRectifierWithDropout":
        return new ExpRectifierDropoutOut();
      case "ExpRectifier":
        return new ExpRectifierOut();
      case "Rectifier":
        return new RectifierOut();
      case "RectifierWithDropout":
        return new RectifierDropoutOut();
      case "MaxoutWithDropout":
        return new MaxoutDropoutOut();
      case "Maxout":
        return new MaxoutOut();
      case "TanhWithDropout":
        return new TanhDropoutOut();
      case "Tanh":
        return new TanhOut();
      default:
        throw new UnsupportedOperationException("Unexpected activation function: " + activation);
    }
  }
}
