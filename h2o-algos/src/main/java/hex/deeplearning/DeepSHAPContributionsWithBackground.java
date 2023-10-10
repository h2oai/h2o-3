package hex.deeplearning;

import hex.ContributionsWithBackgroundFrameTask;
import hex.DataInfo;
import water.H2O;
import water.Key;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;
import water.util.fp.Function;

import java.util.Arrays;

class DeepSHAPContributionsWithBackground extends ContributionsWithBackgroundFrameTask<DeepSHAPContributionsWithBackground> {

  private final DeepLearningModel deepLearningModel;
  transient Function<Double, Double> _activation;
  transient Function<Double, Double> _activationDiff;
  final int[] _origIndices;
  int _hiddenLayerMultiplier;
  final boolean _outputSpace;

  public DeepSHAPContributionsWithBackground(DeepLearningModel deepLearningModel, Key<Frame> frKey, Key<Frame> backgroundFrameKey, boolean perReference, int[] origIndices, boolean outputSpace) {
    super(frKey, backgroundFrameKey, perReference);
    this.deepLearningModel = deepLearningModel;

    _origIndices = origIndices;
    _outputSpace = outputSpace;
  }

  @Override
  protected void setupLocal() {
    super.setupLocal();
    switch (deepLearningModel._parms._activation) {
      case Tanh:
      case TanhWithDropout:
        _activation = this::tanhActivation;
        // differentials are used only in cases when delta_y/delta_x could be numerically unstable (abs(delta_x)<1e-6)
        _activationDiff = this::tanhActivationDiff;
        _hiddenLayerMultiplier = 1;
        break;
      case Rectifier:
      case RectifierWithDropout:
        _activation = this::rectifierActivation;
        _activationDiff = this::rectifierActivationDiff;
        _hiddenLayerMultiplier = 1;
        break;
      case Maxout: // will use different logic 
      case MaxoutWithDropout:
        _activation = this::identity;
        _activationDiff = this::identity;
        _hiddenLayerMultiplier = 2;
        break;
      default:
        // All currently supported activations in training are supported in DeepSHAP. This is here just in case 
        // somebody adds new activation function or finishes the ExpRectifier that is partially implemented.
        H2O.unimpl("Activation " + deepLearningModel._parms._activation + " is not supported in DeepSHAP.");
    }
  }

  protected double identity(double v) {
    return v;
  }

  protected double tanhActivation(double v) {
    return 1 - 2.0 / (1 + Math.exp(2. * v));
  }

  protected double tanhActivationDiff(double v) {
    return 1 - Math.pow(1 - 2.0 / (1 + Math.exp(2. * v)), 2);
  }

  protected double rectifierActivation(double v) {
    return 0.5 * (v + Math.abs(v));
  }

  protected double rectifierActivationDiff(double v) {
    return v > 0 ? 1 : 0;
  }

  protected double div(double a, double b) {
    if (Math.abs(b) < 1e-10) return 0;
    return a / b;
  }

  protected double linearPred(Storage.DenseRowMatrix weights, Storage.DenseVector bias, double[] input, int index, boolean outputLayer) {
    double tmp = bias.get(index);
    if (outputLayer) {
      for (int i = 0; i < input.length; i++) {
        tmp += weights.get(index, i) * input[i];
      }
    } else {
      for (int i = 0; i < input.length; i++) {
        tmp += getWeight(weights, index, i) * input[i];
      }
    }
    return tmp;
  }

  protected void softMax(double[] x) {
    final double max = ArrayUtils.maxValue(x);
    double scaling = 0;
    for (int i = 0; i < x.length; i++) {
      x[i] = Math.exp(x[i] - max);
      scaling += x[i];
    }
    for (int i = 0; i < x.length; i++) {
      x[i] /= scaling;
    }
  }

  protected float getWeight(Storage.DenseRowMatrix w, int row, int col) {
    if (_hiddenLayerMultiplier != 1) {
      assert _hiddenLayerMultiplier == 2;
      return w.raw()[2 * (row / 2 * w.cols() + col) + row % 2];
    }
    return w.get(row, col);
  }

  protected void forwardPass(DataInfo.Row row, double[][] forwardPassActivations) {
    // Zero out the activations
    for (int i = 0; i < forwardPassActivations.length; i++) {
      Arrays.fill(forwardPassActivations[i], 0);
    }
    // Go through the network
    // input layers
    Storage.DenseRowMatrix w = deepLearningModel.model_info().get_weights(0);
    Storage.DenseVector b = deepLearningModel.model_info().get_biases(0);
    for (int l = 0; l < w.rows(); l++) {
      for (int m = 0; m < w.cols(); m++) {
        forwardPassActivations[0][l] += row.get(m) * getWeight(w, l, m);
      }
      forwardPassActivations[0][l] += b.get(l);
    }

    for (int l = 0; l < forwardPassActivations[1].length; l++) {
      if (_hiddenLayerMultiplier == 1) { // not maxout
        forwardPassActivations[1][l] = _activation.apply(forwardPassActivations[0][l]);
      } else {
        forwardPassActivations[1][l] = Math.max(forwardPassActivations[0][2 * l], forwardPassActivations[0][2 * l + 1]);
      }
      if (null != deepLearningModel.model_info().get_params()._hidden_dropout_ratios)
        forwardPassActivations[1][l] *= 1 - deepLearningModel.model_info().get_params()._hidden_dropout_ratios[0];
    }

    // hidden layers
    for (int i = 1; i < deepLearningModel._parms._hidden.length; i++) {
      w = deepLearningModel.model_info().get_weights(i);
      b = deepLearningModel.model_info().get_biases(i);
      for (int l = 0; l < w.rows(); l++) {
        forwardPassActivations[2 * i][l] = linearPred(w, b, forwardPassActivations[2 * i - 1], l, false);
      }
      for (int l = 0; l < forwardPassActivations[2 * i + 1].length; l++) {
        if (_hiddenLayerMultiplier == 1) { // not maxout
          forwardPassActivations[2 * i + 1][l] = _activation.apply(forwardPassActivations[2 * i][l]);
        } else {
          forwardPassActivations[2 * i + 1][l] = Math.max(forwardPassActivations[2 * i][2 * l], forwardPassActivations[2 * i][2 * l + 1]);
        }
        if (null != deepLearningModel.model_info().get_params()._hidden_dropout_ratios)
          forwardPassActivations[2 * i + 1][l] *= 1 - deepLearningModel.model_info().get_params()._hidden_dropout_ratios[i];
      }
    }
    // output layer
    final int i = deepLearningModel._parms._hidden.length;
    w = deepLearningModel.model_info().get_weights(i);
    b = deepLearningModel.model_info().get_biases(i);
    for (int l = 0; l < w.rows(); l++) {
      forwardPassActivations[2 * i][l] = linearPred(w, b, forwardPassActivations[2 * i - 1], l, true);
      forwardPassActivations[2 * i + 1][l] = forwardPassActivations[2 * i][l];
      if (w.rows() == 1) {
        if (deepLearningModel.model_info().data_info()._normRespMul != null)
          forwardPassActivations[2 * i + 1][l] = (forwardPassActivations[2 * i + 1][l] / deepLearningModel.model_info().data_info()._normRespMul[0] + deepLearningModel.model_info().data_info()._normRespSub[0]);

        // transform prediction to response space
        forwardPassActivations[2 * i + 1][l] = deepLearningModel._dist.linkInv(forwardPassActivations[2 * i + 1][l]);
      }
    }
    if (w.rows() == 2) // binomial classification
      softMax(forwardPassActivations[2 * i + 1]);
  }

  protected void maxSHAP(double[] x, double[] bg, float[] contributions, int i, int j) {
    // for more dimensional exact maxSHAP see supplementary material[0] for "A Unified Approach to Interpreting Model Predictions"
    // by Scott M. Lundberg, Su-In Lee
    // [0] currently at https://papers.nips.cc/paper_files/paper/2017/file/8a20a8621978632d76c43dfd28b67767-Supplemental.zip

    final double maxBB = Math.max(bg[i], bg[j]);
    final double maxBX = Math.max(bg[i], x[j]);
    final double maxXB = Math.max(x[i], bg[j]);
    final double maxXX = Math.max(x[i], x[j]);
    final double maxXXmBB = maxXX - maxBB;
    final double maxXBmBX = maxXB - maxBX;

    contributions[0] = (float) (0.5 * (maxXXmBB + maxXBmBX));
    contributions[1] = (float) (0.5 * (maxXXmBB - maxXBmBX));
  }

  protected void linearSHAP(Storage.DenseRowMatrix weights,
                            double[] contributions, int index) {
    for (int i = 0; i < contributions.length; i++) {
      // LinearSHAP
      // phi = w_i *(x -bg)
      // rescale => phi / (x-bg) => w_i
      contributions[i] = weights.get(index, i);// * (input[i] - inputBg[i]) / (input[i] - inputBg[i]);
    }
  }

  protected void nonLinearActivationSHAP(Storage.DenseRowMatrix weights,
                                         double[][] forwardPass, double[][] forwardBgPass,
                                         int currLayer, Storage.DenseRowMatrix contributions) {

//        How this works?
//        ---------------
//        Let denote act(x) as a one dimensional activation function (e.g., tanh, max(x, 0),...) and linear function lin(X).
//        For lin(X), we can solve interventional SHAP like this:
//        lin(X) = wX + b
//        phi_i = w_i * (X_i - BG_i), where BG is background sample
//
//        For one dimensional activation function we can solve contributions easily - all contributions are from the only
//        dimension we have so we just need to calculate it so it satisfies the "sum to the delta" property: 
//        act(x) - act(bg) = phi
//
//
//         To calculate the contributions of act(lin(X)) we need to rescale the intermediate values and use the chain rule:
//         m_{i} = phi/(X_i - BG_i)
//         => m_i(lin) = w_i
//         => m_i(act) = (act(x)-act(bg))/(x - bg)
//         
//         Chain rule:
//         m_i(act(lin(X)) = m_i(lin) * m_i(act) = w_i * ((act(wX+b) - act(wBG+b))/(wX + b - wBG-b)) = w_i * delta_out/delta_in
//        
//        MaxOut is different from the rest of the activation functions as it takes multiple inputs - H2O suports just 2
//        inputs. MaxOut can be calculated exactly faster than with a naive approach by sorting and then creating a decision
//        tree (exploiting the fact that we know where the maximum is on the sorted input). But since we support just 2 inputs
//        the naive approach requires just 4 calls to Math.max and simple arithmetic, so it's probably faster (but I didn't 
//        benchmark it).
//        
//        Then we use the chain rule:
//        m_i(maxout(lin_a, lin_b)) = sum_j (m_i(maxout)_j * m_i(lin_j))

    if (_hiddenLayerMultiplier > 1 && forwardPass.length > 2 * currLayer + 2) { // Is MaxOut and not the last layer (last layer is SoftMax here (regression uses linear combination))
      final double dropoutRatio = null == deepLearningModel.model_info().get_params()._hidden_dropout_ratios
              ? 1
              : 1 - deepLearningModel.model_info().get_params()._hidden_dropout_ratios[currLayer];
      for (int row = 0; row < contributions.rows(); row++) {
        final float[] deltaIn = new float[]{
                (float) (forwardPass[2 * currLayer][2 * row] - forwardBgPass[2 * currLayer][2 * row]),
                (float) (forwardPass[2 * currLayer][2 * row + 1] - forwardBgPass[2 * currLayer][2 * row + 1]),
        };
        float[] maxOutContr = new float[2];
        maxSHAP(forwardPass[2 * currLayer], forwardBgPass[2 * currLayer], maxOutContr, 2 * row, 2 * row + 1);
        for (int col = 0; col < contributions.cols(); col++) {
          contributions.set(row, col,
                  (float) (dropoutRatio * (div(getWeight(weights, 2 * row, col) * maxOutContr[0], deltaIn[0]) +
                          div(getWeight(weights, 2 * row + 1, col) * maxOutContr[1], deltaIn[1])))
          );
        }
      }
    } else {
      for (int row = 0; row < contributions.rows(); row++) {
        final double deltaOut = forwardPass[2 * currLayer + 1][row] - forwardBgPass[2 * currLayer + 1][row];
        final double deltaIn = forwardPass[2 * currLayer][row] - forwardBgPass[2 * currLayer][row];
        final float ratio = (float) (Math.abs(deltaIn) > 1e-6 ? div(deltaOut, deltaIn) : _activationDiff.apply(forwardPass[2 * currLayer][row]));
        for (int col = 0; col < contributions.cols(); col++)
          contributions.set(row, col, weights.get(row, col) * ratio);
      }
    }
  }

  protected void combineMultiplicators(Storage.DenseRowMatrix m, double[][] contributions, int currentLayer) {
    final int prevLayer = currentLayer + 1; // Contains multiplicators with respect to the output
    Arrays.fill(contributions[currentLayer], 0);
    for (int i = 0; i < m.rows(); i++) {
      for (int j = 0; j < m.cols(); j++)
        contributions[currentLayer][j] += m.get(i, j) * contributions[prevLayer][i];
    }
  }

  protected void backwardPass(double[][] forwardPass, double[][] forwardBgPass, double[][] backwardPass, DataInfo.Row row, DataInfo.Row bgRow) {
    for (int i = 0; i < backwardPass.length; i++) {
      Arrays.fill(backwardPass[i], 0);
    }
    int i = backwardPass.length - 1;
    final int backwardPassOffset = _origIndices == null ? 0 : 1;
    final int outputNeuron = deepLearningModel.model_info().get_weights(backwardPass.length - 1 - backwardPassOffset).rows() - 1; // in regression we have one output and in binom. class we care only about P(y==1).
    if (outputNeuron == 0) {
      float[] outWeight = new float[backwardPass[i].length];
      for (int j = 0; j < outWeight.length; j++) {
        if (deepLearningModel.model_info().data_info._normRespMul != null) {
          outWeight[j] = (float) (deepLearningModel.model_info().get_weights(i - backwardPassOffset).get(outputNeuron, j) / deepLearningModel.model_info().data_info._normRespMul[outputNeuron]);
        } else {
          outWeight[j] = deepLearningModel.model_info().get_weights(i - backwardPassOffset).get(outputNeuron, j);
        }
      }
      linearSHAP(
              new Storage.DenseRowMatrix(outWeight, 1, backwardPass[i].length),
              backwardPass[i],
              0
      );
    } else {
      Storage.DenseRowMatrix m = new Storage.DenseRowMatrix(2, backwardPass[i].length);
      nonLinearActivationSHAP(
              deepLearningModel.model_info().get_weights(i - backwardPassOffset),
              forwardPass,
              forwardBgPass,
              i - backwardPassOffset,
              m
      );
      for (int j = 0; j < m.cols(); j++) {
        backwardPass[i][j] = m.get(outputNeuron, j);
      }
    }

    for (i = backwardPass.length - 2; i >= backwardPassOffset; i--) {
      Storage.DenseRowMatrix m = new Storage.DenseRowMatrix(backwardPass[i + 1].length, backwardPass[i].length);
      nonLinearActivationSHAP(
              deepLearningModel.model_info().get_weights(i - backwardPassOffset),
              forwardPass,
              forwardBgPass,
              i - backwardPassOffset,
              m
      );
      combineMultiplicators(m, backwardPass, i);
    }

    if (null != _origIndices) {
      Arrays.fill(backwardPass[0], 0.0);
      for (i = 0; i < _origIndices.length; i++)
        backwardPass[0][_origIndices[i]] += (backwardPass[1][i]) * (row.get(i) - bgRow.get(i));
    } else {
      for (i = 0; i < backwardPass[0].length; i++)
        backwardPass[0][i] *= (row.get(i) - bgRow.get(i));
    }
  }

  @Override
  protected void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] ncs) {
    double[][] forwardPass = new double[2 * (deepLearningModel._parms._hidden.length + 1)][];
    double[][] forwardBgPass = new double[2 * (deepLearningModel._parms._hidden.length + 1)][];
    final int backwardPassOffset = _origIndices == null ? 1 : 2;
    double[][] backwardPass = new double[deepLearningModel._parms._hidden.length + backwardPassOffset][];

    backwardPass[0] = MemoryManager.malloc8d(ncs.length - 1);

    if (backwardPassOffset > 1)
      backwardPass[1] = MemoryManager.malloc8d(deepLearningModel.model_info().get_weights(0).cols());
    for (int i = 0; i < deepLearningModel._parms._hidden.length; i++) {
      forwardPass[2 * i] = MemoryManager.malloc8d(_hiddenLayerMultiplier * deepLearningModel._parms._hidden[i]);
      forwardBgPass[2 * i] = MemoryManager.malloc8d(_hiddenLayerMultiplier * deepLearningModel._parms._hidden[i]);
      forwardPass[2 * i + 1] = MemoryManager.malloc8d(deepLearningModel._parms._hidden[i]);
      forwardBgPass[2 * i + 1] = MemoryManager.malloc8d(deepLearningModel._parms._hidden[i]);
      backwardPass[i + backwardPassOffset] = MemoryManager.malloc8d(deepLearningModel._parms._hidden[i]);
    }
    forwardPass[2 * deepLearningModel._parms._hidden.length] = new double[deepLearningModel.model_info().get_weights(deepLearningModel._parms._hidden.length).rows()];
    forwardBgPass[2 * deepLearningModel._parms._hidden.length] = new double[deepLearningModel.model_info().get_weights(deepLearningModel._parms._hidden.length).rows()];
    forwardPass[2 * deepLearningModel._parms._hidden.length + 1] = new double[deepLearningModel.model_info().get_weights(deepLearningModel._parms._hidden.length).rows()];
    forwardBgPass[2 * deepLearningModel._parms._hidden.length + 1] = new double[deepLearningModel.model_info().get_weights(deepLearningModel._parms._hidden.length).rows()];

    DataInfo.Row row = deepLearningModel.model_info().data_info.newDenseRow();
    DataInfo.Row bgRow = deepLearningModel.model_info().data_info.newDenseRow();
    for (int j = 0; j < cs[0]._len; j++) {
      deepLearningModel.model_info().data_info.extractDenseRow(cs, j, row);
      forwardPass(row, forwardPass);
      for (int k = 0; k < bgCs[0]._len; k++) {
        deepLearningModel.model_info().data_info.extractDenseRow(bgCs, k, bgRow);
        forwardPass(bgRow, forwardBgPass);
        ncs[ncs.length - 1].addNum(forwardBgPass[forwardBgPass.length - 1][forwardBgPass[forwardBgPass.length - 1].length - 1]);

        backwardPass(forwardPass, forwardBgPass, backwardPass, row, bgRow);
        final double multiplier = _outputSpace && forwardPass[forwardPass.length - 1].length == 1
                ? div((forwardPass[forwardPass.length - 1][0] - forwardBgPass[forwardBgPass.length - 1][0]), Arrays.stream(backwardPass[0]).sum())
                : 1;
        for (int i = 0; i < backwardPass[0].length; i++) {
          ncs[i].addNum(multiplier * backwardPass[0][i]);
        }
      }
    }
  }
}
