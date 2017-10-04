package hex.genmodel.algos.deeplearning;

import java.util.Arrays;
import java.util.List;

import static hex.genmodel.algos.deeplearning.ActivationUtils.*;

public class NeuralNetwork {  // represent one layer of neural network
  public String _activation;    // string that describe the activation function
  double _drop_out_ratio;              // drop_out_ratio for that layer
  public DeeplearningMojoModel.StoreWeightsBias _weightsAndBias;  // store layer weight
  public double[] _inputs;     // store input to layer
  public double[] _outputs;    // layer output
  public int _outSize;         // number of nodes in this layer
  public int _inSize;         // number of inputs to this layer
  public int _maxK=1;
  List<String> _validActivation = Arrays.asList("Linear", "Softmax", "ExpRectifierWithDropout", "ExpRectifier",
          "Rectifier", "RectifierWithDropout", "MaxoutWithDropout", "Maxout", "TanhWithDropout", "Tanh");

  public NeuralNetwork(String activation, double drop_out_ratio, DeeplearningMojoModel.StoreWeightsBias weightsAndBias,
                       double[] inputs, int outSize) {
    validateInputs(activation, drop_out_ratio, weightsAndBias._wValues.length, weightsAndBias._bValues.length,
            inputs.length, outSize);
    _activation=activation;
    _drop_out_ratio=drop_out_ratio;
    _weightsAndBias=weightsAndBias;
    _inputs=inputs;
    _outSize=outSize;
    _inSize=_inputs.length;
    _outputs = new double[_outSize];
    if ("Maxout".equals(_activation) || "MaxoutWithDropout".equals(_activation)) {
      _maxK = weightsAndBias._bValues.length/outSize;
    }
  }

  public double[] fprop1Layer() {
    double[] input2ActFun = _maxK==1?formNNInputs():formNNInputsMaxOut();
    ActivationFunctions createActivations = createActFuns(_activation); // choose activation function
    return createActivations.eval(input2ActFun, _drop_out_ratio, _maxK); // apply activation function to form NN outputs
  }

  /*
  This method matches the exact operation of gemv_row_optimized in order to match all the bits
   */
  public double[] formNNInputs() {
    double[] input2ActFun = new double[_outSize];
    int cols = _inputs.length;
    int rows = input2ActFun.length;
    int extra=cols-cols%8;
    int multiple = (cols/8)*8-1;
    int idx = 0;
    for (int row = 0; row < rows; row++) {
      double psum0 = 0, psum1 = 0, psum2 = 0, psum3 = 0, psum4 = 0, psum5 = 0, psum6 = 0, psum7 = 0;

      for (int col=0; col < multiple; col+=8) {
        int off=idx+col;
        psum0 += _weightsAndBias._wValues[off    ] * _inputs[col    ];
        psum1 += _weightsAndBias._wValues[off + 1] * _inputs[col + 1];
        psum2 += _weightsAndBias._wValues[off + 2] * _inputs[col + 2];
        psum3 += _weightsAndBias._wValues[off + 3] * _inputs[col + 3];
        psum4 += _weightsAndBias._wValues[off + 4] * _inputs[col + 4];
        psum5 += _weightsAndBias._wValues[off + 5] * _inputs[col + 5];
        psum6 += _weightsAndBias._wValues[off + 6] * _inputs[col + 6];
        psum7 += _weightsAndBias._wValues[off + 7] * _inputs[col + 7];
      }
      input2ActFun[row] += psum0+psum1+psum2+psum3;
      input2ActFun[row] += psum4+psum5+psum6+psum7;

      for (int col = extra; col<cols;col++) {
        input2ActFun[row] += _weightsAndBias._wValues[idx+col]*_inputs[col];
      }
      input2ActFun[row] += _weightsAndBias._bValues[row];
      idx += cols;
    }
    return input2ActFun;
  }

  public double[] formNNInputsMaxOut() {
    double[] input2ActFun = new double[_outSize*_maxK];

    for (int k = 0; k < _maxK; k++) {
      for (int row = 0; row < _outSize; row++) {
        int countInd = _maxK*row+k;
        for (int col = 0; col < _inSize; col++) {
          input2ActFun[countInd] += _inputs[col] * _weightsAndBias._wValues[_maxK*(row*_inSize+col)+k];
        }
        input2ActFun[countInd] += _weightsAndBias._bValues[countInd];  //
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
