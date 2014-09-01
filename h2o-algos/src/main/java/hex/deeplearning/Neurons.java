package hex.deeplearning;

import hex.FrameTask;
import water.Iced;
import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.MathUtils;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This class implements the concept of a Neuron layer in a Neural Network
 * During training, every MRTask2 F/J thread is expected to create these neurons for every map call (Cheap to make).
 * These Neurons are NOT sent over the wire.
 * The weights connecting the neurons are in a separate class (DeepLearningModel.DeepLearningModelInfo), and will be shared per node.
 */
public abstract class Neurons {
  protected int units;

  /**
   * Constructor of a Neuron Layer
   * @param units How many neurons are in this layer?
   */
  Neurons(int units) {
    this.units = units;
  }

  /**
   * Print the status of this neuron layer
   * @return populated String
   */
  @Override
  public String toString() {
    String s = this.getClass().getSimpleName();
    s += "\nNumber of Neurons: " + units;
    s += "\nParameters:\n" + params.toString();
    if (_dropout != null) s += "\nDropout:\n" + _dropout.toString();
    return s;
  }

  /**
   * Parameters (deep-cloned() from the user input, can be modified here, e.g. learning rate decay)
   */
  protected transient DeepLearningModel.DeepLearningParameters params;
  protected transient int _index; //which hidden layer it is

  /**
   * Layer state (one per neuron): activity, error
   */
  public transient Vector _a; //can be sparse for input layer
  public transient DenseVector _e;

  /**
   * References for feed-forward connectivity
   */
  public Neurons _previous;
  public Neurons _input;
  DeepLearningModel.DeepLearningModelInfo _minfo; //reference to shared model info
  public Matrix _w;
  public DenseVector _b;

  /**
   * References for momentum training
   */
  Matrix _wm;
  DenseVector _bm;

  /**
   * References for ADADELTA
   */
  Matrix _ada_dx_g;
  DenseVector _bias_ada_dx_g;

  /**
   * For Dropout training
   */
  protected Dropout _dropout;

  /**
   * Helper to shortcut bprop
   */
  private boolean _shortcut = false;

  public DenseVector _avg_a;

  public static final int missing_int_value = Integer.MAX_VALUE; //encode missing label
  public static final Float missing_real_value = Float.NaN; //encode missing regression target

  /**
   * Helper to check sanity of Neuron layers
   * @param training whether training or testing is done
   */
  void sanityCheck(boolean training) {
    if (this instanceof Input) {
      assert(_previous == null);
      assert (!training || _dropout != null);
    } else {
      assert(_previous != null);
      if (_minfo.has_momenta()) {
        assert(_wm != null);
        assert(_bm != null);
        assert(_ada_dx_g == null);
      }
      if (_minfo.adaDelta()) {
        if (params.rho == 0) throw new IllegalArgumentException("rho must be > 0 if epsilon is >0.");
        if (params.epsilon == 0) throw new IllegalArgumentException("epsilon must be > 0 if rho is >0.");
        assert(_minfo.adaDelta());
        assert(_bias_ada_dx_g != null);
        assert(_wm == null);
        assert(_bm == null);
      }
      if (this instanceof MaxoutDropout || this instanceof TanhDropout || this instanceof RectifierDropout) {
        assert (!training || _dropout != null);
      }
    }
  }

  /**
   * Initialization of the parameters and connectivity of a Neuron layer
   * @param neurons Array of all neuron layers, to establish feed-forward connectivity
   * @param index Which layer am I?
   * @param p User-given parameters (Job parental object hierarchy is not used)
   * @param minfo Model information (weights/biases and their momenta)
   * @param training Whether training is done or just testing (no need for dropout)
   */
  public final void init(Neurons[] neurons, int index, DeepLearningModel.DeepLearningParameters p, final DeepLearningModel.DeepLearningModelInfo minfo, boolean training) {
    _index = index-1;
    params = (DeepLearningModel.DeepLearningParameters)p.clone();
    params.rate *= Math.pow(params.rate_decay, index-1);
    _a = new DenseVector(units);
    if (!(this instanceof Output) && !(this instanceof Input)) {
      _e = new DenseVector(units);
    }
    if (training && (this instanceof MaxoutDropout || this instanceof TanhDropout
            || this instanceof RectifierDropout || this instanceof Input) ) {
      _dropout = this instanceof Input ? new Dropout(units, params.input_dropout_ratio) : new Dropout(units, params.hidden_dropout_ratios[_index]);
    }
    if (!(this instanceof Input)) {
      _previous = neurons[_index]; //incoming neurons
      _minfo = minfo;
      _w = minfo.get_weights(_index); //incoming weights
      _b = minfo.get_biases(_index); //bias for this layer (starting at hidden layer)
      if(params.autoencoder && params.sparsity_beta > 0 && _index < params.hidden.length) {
        _avg_a = minfo.get_avg_activations(_index);
      }
      if (minfo.has_momenta()) {
        _wm = minfo.get_weights_momenta(_index); //incoming weights
        _bm = minfo.get_biases_momenta(_index); //bias for this layer (starting at hidden layer)
      }
      if (minfo.adaDelta()) {
        _ada_dx_g = minfo.get_ada_dx_g(_index);
        _bias_ada_dx_g = minfo.get_biases_ada_dx_g(_index);
      }
      _shortcut = (params.fast_mode || (
              // not doing fast mode, but also don't have anything else to update (neither momentum nor ADADELTA history), and no L1/L2
              !params.adaptive_rate && !_minfo.has_momenta() && params.l1 == 0.0 && params.l2 == 0.0));
    }
    sanityCheck(training);
  }

  /**
   * Forward propagation
   * @param seed For seeding the RNG inside (for dropout)
   * @param training Whether training is done or just testing (no need for dropout)
   */
  protected abstract void fprop(long seed, boolean training);

  /**
   *  Back propagation
   */
  protected abstract void bprop();

  void bprop_sparse(float r, float m) {
    SparseVector prev_a = (SparseVector) _previous._a;
    int start = prev_a.begin()._idx;
    int end = prev_a.end()._idx;
    for (int it = start; it < end; ++it) {
      final int col = prev_a._indices[it];
      final float previous_a = prev_a._values[it];
      bprop_col(col, previous_a, r, m);
    }
    final int rows = _a.size();
    final float max_w2 = params.max_w2;
    for (int row = 0; row < rows; row++) {
      if (max_w2 != Float.POSITIVE_INFINITY)
        rescale_weights(_w, row, max_w2);
    }
  }

  /**
   * Backpropagation: w -= rate * dE/dw, where dE/dw = dE/dy * dy/dnet * dnet/dw
   * This method adds the dnet/dw = activation term per unit
   * @param row row index (update weights feeding to this neuron)
   * @param partial_grad partial derivative dE/dnet = dE/dy * dy/net
   * @param rate learning rate
   * @param momentum momentum factor (needed only if ADADELTA isn't used)
   */
  final void bprop(final int row, final float partial_grad, final float rate, final float momentum) {
    // only correct weights if the gradient is large enough
    if (_shortcut && partial_grad == 0f) return;

    if (_w instanceof DenseRowMatrix && _previous._a instanceof DenseVector)
      bprop_dense_row_dense(
              (DenseRowMatrix) _w, (DenseRowMatrix) _wm, (DenseRowMatrix) _ada_dx_g,
              (DenseVector) _previous._a, _previous._e, _b, _bm, row, partial_grad, rate, momentum);
    else if (_w instanceof DenseRowMatrix && _previous._a instanceof SparseVector)
      bprop_dense_row_sparse(
              (DenseRowMatrix)_w, (DenseRowMatrix)_wm, (DenseRowMatrix)_ada_dx_g,
              (SparseVector)_previous._a, _previous._e, _b, _bm, row, partial_grad, rate, momentum);
    else
      throw new UnsupportedOperationException("bprop for types not yet implemented.");
  }

  final void bprop_col(final int col, final float previous_a, final float rate, final float momentum) {
    if (_w instanceof DenseColMatrix && _previous._a instanceof SparseVector)
      bprop_dense_col_sparse(
              (DenseColMatrix)_w, (DenseColMatrix)_wm, (DenseColMatrix)_ada_dx_g,
              (SparseVector)_previous._a, _previous._e, _b, _bm, col, previous_a, rate, momentum);
    else
      throw new UnsupportedOperationException("bprop_col for types not yet implemented.");
  }

  /**
   * Specialization of backpropagation for DenseRowMatrices and DenseVectors
   * @param _w weight matrix
   * @param _wm weight momentum matrix
   * @param adaxg ADADELTA matrix (2 floats per weight)
   * @param prev_a activation of previous layer
   * @param prev_e error of previous layer
   * @param _b bias vector
   * @param _bm bias momentum vector
   * @param row index of the neuron for which we back-propagate
   * @param partial_grad partial derivative dE/dnet = dE/dy * dy/net
   * @param rate learning rate
   * @param momentum momentum factor (needed only if ADADELTA isn't used)
   */
  private void bprop_dense_row_dense(
          final DenseRowMatrix _w, final DenseRowMatrix _wm, final DenseRowMatrix adaxg,
          final DenseVector prev_a, final DenseVector prev_e, final DenseVector _b, final DenseVector _bm,
          final int row, final float partial_grad, float rate, final float momentum)
  {
    final float rho = (float)params.rho;
    final float eps = (float)params.epsilon;
    final float l1 = (float)params.l1;
    final float l2 = (float)params.l2;
    final float max_w2 = params.max_w2;
    final boolean have_momenta = _minfo.has_momenta();
    final boolean have_ada = _minfo.adaDelta();
    final boolean nesterov = params.nesterov_accelerated_gradient;
    final boolean update_prev = prev_e != null;
    final boolean fast_mode = params.fast_mode;
    final int cols = prev_a.size();
    final int idx = row * cols;

    float avg_grad2 = 0;
    for( int col = 0; col < cols; col++ ) {
      final float weight = _w.get(row,col);
      if( update_prev ) prev_e.add(col, partial_grad * weight); // propagate the error dE/dnet to the previous layer, via connecting weights
      final float previous_a = prev_a.get(col);
      if (fast_mode && previous_a == 0) continue;

      //this is the actual gradient dE/dw
      final float grad = partial_grad * previous_a - Math.signum(weight) * l1 - weight * l2;
      final int w = idx + col;

      if (have_ada) {
        assert(!have_momenta);
        final float grad2 = grad*grad;
        avg_grad2 += grad2;
        float brate = computeAdaDeltaRateForWeight(grad, w, adaxg, rho, eps);
        _w.raw()[w] += brate * grad;
      } else {
        if (!nesterov) {
          final float delta = rate * grad;
          _w.raw()[w] += delta;
          if( have_momenta ) {
            _w.raw()[w] += momentum * _wm.raw()[w];
            _wm.raw()[w] = delta;
          }
        } else {
          float tmp = grad;
          if( have_momenta ) {
            _wm.raw()[w] *= momentum;
            _wm.raw()[w] += tmp;
            tmp = _wm.raw()[w];
          }
          _w.raw()[w] += rate * tmp;
        }
      }
    }
    if (max_w2 != Float.POSITIVE_INFINITY)
      rescale_weights(_w, row, max_w2);
    if (have_ada) avg_grad2 /= cols;
    update_bias(_b, _bm, row, partial_grad, avg_grad2, rate, momentum);
  }

  /**
   * Specialization of backpropagation for DenseColMatrices and SparseVector for previous layer's activation and DenseVector for everything else
   * @param w Weight matrix
   * @param wm Momentum matrix
   * @param adaxg ADADELTA matrix (2 floats per weight)
   * @param prev_a sparse activation of previous layer
   * @param prev_e error of previous layer
   * @param b bias
   * @param bm bias momentum
   * @param rate learning rate
   * @param momentum momentum factor (needed only if ADADELTA isn't used)
   */
  private void bprop_dense_col_sparse(
          final DenseColMatrix w, final DenseColMatrix wm, final DenseColMatrix adaxg,
          final SparseVector prev_a, final DenseVector prev_e, final DenseVector b, final DenseVector bm,
          final int col, final float previous_a, float rate, final float momentum)
  {
    final float rho = (float)params.rho;
    final float eps = (float)params.epsilon;
    final float l1 = (float)params.l1;
    final float l2 = (float)params.l2;
    final boolean have_momenta = _minfo.has_momenta();
    final boolean have_ada = _minfo.adaDelta();
    final boolean nesterov = params.nesterov_accelerated_gradient;
    final boolean update_prev = prev_e != null;
    final int cols = prev_a.size();

    final int rows = _a.size();
    for (int row = 0; row < rows; row++) {
      final float partial_grad = _e.get(row) * (1f - _a.get(row) * _a.get(row));
      final float weight = w.get(row,col);
      if( update_prev ) prev_e.add(col, partial_grad * weight); // propagate the error dE/dnet to the previous layer, via connecting weights
      assert (previous_a != 0); //only iterate over non-zeros!

      if (_shortcut && partial_grad == 0f) continue;

      //this is the actual gradient dE/dw
      final float grad = partial_grad * previous_a - Math.signum(weight) * l1 - weight * l2;
      if (have_ada) {
        assert(!have_momenta);
        float brate = computeAdaDeltaRateForWeight(grad, row, col, adaxg, rho, eps);
        w.add(row,col, brate * grad);
      } else {
        if (!nesterov) {
          final float delta = rate * grad;
          w.add(row, col, delta);
//          Log.info("for row = " + row + ", col = " + col + ", partial_grad = " + partial_grad + ", grad = " + grad);
          if( have_momenta ) {
            w.add(row, col, momentum * wm.get(row, col));
            wm.set(row, col, delta);
          }
        } else {
          float tmp = grad;
          if( have_momenta ) {
            float val = wm.get(row, col);
            val *= momentum;
            val += tmp;
            tmp = val;
            wm.set(row, col, val);
          }
          w.add(row, col, rate * tmp);
        }
      }
      //this is called cols times, so we divide the (repeated) contribution by 1/cols
      update_bias(b, bm, row, partial_grad/cols, grad*grad/cols, rate, momentum);
    }
  }

 /**
   * Specialization of backpropagation for DenseRowMatrices and SparseVector for previous layer's activation and DenseVector for everything else
   * @param _w weight matrix
   * @param _wm weight momentum matrix
   * @param adaxg ADADELTA matrix (2 floats per weight)
   * @param prev_a sparse activation of previous layer
   * @param prev_e error of previous layer
   * @param _b bias vector
   * @param _bm bias momentum vector
   * @param row index of the neuron for which we back-propagate
   * @param partial_grad partial derivative dE/dnet = dE/dy * dy/net
   * @param rate learning rate
   * @param momentum momentum factor (needed only if ADADELTA isn't used)
   */
  private void bprop_dense_row_sparse(
          final DenseRowMatrix _w, final DenseRowMatrix _wm, final DenseRowMatrix adaxg,
          final SparseVector prev_a, final DenseVector prev_e, final DenseVector _b, final DenseVector _bm,
          final int row, final float partial_grad, float rate, final float momentum)
  {
    final float rho = (float)params.rho;
    final float eps = (float)params.epsilon;
    final float l1 = (float)params.l1;
    final float l2 = (float)params.l2;
    final float max_w2 = params.max_w2;
    final boolean have_momenta = _minfo.has_momenta();
    final boolean have_ada = _minfo.adaDelta();
    final boolean nesterov = params.nesterov_accelerated_gradient;
    final boolean update_prev = prev_e != null;
    final int cols = prev_a.size();
    final int idx = row * cols;

    float avg_grad2 = 0;
    int start = prev_a.begin()._idx;
    int end = prev_a.end()._idx;
    for (int it = start; it < end; ++it) {
      final int col = prev_a._indices[it];
      final float weight = _w.get(row,col);
      if( update_prev ) prev_e.add(col, partial_grad * weight); // propagate the error dE/dnet to the previous layer, via connecting weights
      final float previous_a = prev_a._values[it];
      assert (previous_a != 0); //only iterate over non-zeros!

      //this is the actual gradient dE/dw
      final float grad = partial_grad * previous_a - Math.signum(weight) * l1 - weight * l2;
      final int w = idx + col;

      if (have_ada) {
        assert(!have_momenta);
        final float grad2 = grad*grad;
        avg_grad2 += grad2;
        float brate = computeAdaDeltaRateForWeight(grad, w, adaxg, rho, eps);
        _w.raw()[w] += brate * grad;
      } else {
        if (!nesterov) {
          final float delta = rate * grad;
          _w.raw()[w] += delta;
          if( have_momenta ) {
            _w.raw()[w] += momentum * _wm.raw()[w];
            _wm.raw()[w] = delta;
          }
        } else {
          float tmp = grad;
          if( have_momenta ) {
            _wm.raw()[w] *= momentum;
            _wm.raw()[w] += tmp;
            tmp = _wm.raw()[w];
          }
          _w.raw()[w] += rate * tmp;
        }
      }
    }
    if (max_w2 != Float.POSITIVE_INFINITY)
      rescale_weights(_w, row, max_w2);
    if (have_ada) avg_grad2 /= prev_a.nnz();
    update_bias(_b, _bm, row, partial_grad, avg_grad2, rate, momentum);
  }

  /**
   * Helper to scale down incoming weights if their squared sum exceeds a given value (by a factor of 10 -> to avoid doing costly rescaling too often)
   * C.f. Improving neural networks by preventing co-adaptation of feature detectors
   * @param row index of the neuron for which to scale the weights
   */
  private static void rescale_weights(final Matrix w, final int row, final float max_w2) {
    final int cols = w.cols();
    if (w instanceof DenseRowMatrix) {
      rescale_weights((DenseRowMatrix)w, row, max_w2);
    } else if (w instanceof DenseColMatrix) {
      float r2 = 0;
      for (int col=0; col<cols;++col)
        r2 += w.get(row,col)*w.get(row,col);
      if( r2 > max_w2) {
        final float scale = MathUtils.approxSqrt(max_w2 / r2);
        for( int col=0; col < cols; col++ ) w.set(row, col, w.get(row,col) * scale);
      }
    }
    else throw new UnsupportedOperationException("rescale weights for " + w.getClass().getSimpleName() + " not yet implemented.");
  }

  // Specialization for DenseRowMatrix
  private static void rescale_weights(final DenseRowMatrix w, final int row, final float max_w2) {
    final int cols = w.cols();
    final int idx = row * cols;
    float r2 = MathUtils.sumSquares(w.raw(), idx, idx + cols);
//    float r2 = MathUtils.approxSumSquares(w.raw(), idx, idx + cols);
    if( r2 > max_w2) {
      final float scale = MathUtils.approxSqrt(max_w2 / r2);
      for( int c = 0; c < cols; c++ ) w.raw()[idx + c] *= scale;
    }
  }

  /**
   * Helper to compute the reconstruction error for auto-encoders (part of the gradient computation)
   * @param row neuron index
   * @return difference between the output (auto-encoder output layer activation) and the target (input layer activation)
   */
  protected float autoEncoderError(int row) {
    assert (_minfo.get_params().autoencoder && _index == _minfo.get_params().hidden.length);
    assert (params.loss == DeepLearningModel.DeepLearningParameters.Loss.MeanSquare);
    return (_input._a.get(row) - _a.get(row));
  }

  /**
   * Compute learning rate with AdaDelta
   * http://www.matthewzeiler.com/pubs/googleTR2012/googleTR2012.pdf
   * @param grad gradient
   * @param row which neuron is to be updated
   * @param col weight from which incoming neuron
   * @param ada_dx_g Matrix holding helper values (2 floats per weight)
   * @param rho hyper-parameter #1
   * @param eps hyper-parameter #2
   * @return learning rate
   */
  private static float computeAdaDeltaRateForWeight(final float grad, final int row, final int col,
                                                  final DenseColMatrix ada_dx_g,
                                                  final float rho, final float eps) {
    ada_dx_g.set(2*row+1, col, rho * ada_dx_g.get(2*row+1, col) + (1f - rho) * grad * grad);
    final float rate = MathUtils.approxSqrt((ada_dx_g.get(2*row, col) + eps)/(ada_dx_g.get(2*row+1, col) + eps));
    ada_dx_g.set(2*row,   col, rho * ada_dx_g.get(2*row, col)   + (1f - rho) * rate * rate * grad * grad);
    return rate;
  }

  /**
   * Compute learning rate with AdaDelta, specialized for DenseRowMatrix
   * @param grad gradient
   * @param w neuron index
   * @param ada_dx_g Matrix holding helper values (2 floats per weight)
   * @param rho hyper-parameter #1
   * @param eps hyper-parameter #2
   * @return learning rate
   */
  private static float computeAdaDeltaRateForWeight(final float grad, final int w,
                                                  final DenseRowMatrix ada_dx_g,
                                                  final float rho, final float eps) {
    ada_dx_g.raw()[2*w+1] = rho * ada_dx_g.raw()[2*w+1] + (1f - rho) * grad * grad;
    final float rate = MathUtils.approxSqrt((ada_dx_g.raw()[2*w] + eps)/(ada_dx_g.raw()[2*w+1] + eps));
    ada_dx_g.raw()[2*w]   = rho * ada_dx_g.raw()[2*w]   + (1f - rho) * rate * rate * grad * grad;
    return rate;
  }

  /**
   * Compute learning rate with AdaDelta, specialized for DenseVector (Bias)
   * @param grad2 squared gradient
   * @param row neuron index
   * @param bias_ada_dx_g Matrix holding helper values (2 floats per weight)
   * @param rho hyper-parameter #1
   * @param eps hyper-parameter #2
   * @return learning rate
   */
  private static float computeAdaDeltaRateForBias(final float grad2, final int row,
                                                  final DenseVector bias_ada_dx_g,
                                                  final float rho, final float eps) {
    bias_ada_dx_g.raw()[2*row+1] = rho * bias_ada_dx_g.raw()[2*row+1] + (1f - rho) * grad2;
    final float rate = MathUtils.approxSqrt((bias_ada_dx_g.raw()[2*row  ] + eps)/(bias_ada_dx_g.raw()[2*row+1] + eps));
    bias_ada_dx_g.raw()[2*row]   = rho * bias_ada_dx_g.raw()[2*row  ] + (1f - rho) * rate * rate * grad2;
    return rate;
  }

  /**
   * Helper to enforce learning rule to satisfy sparsity constraint:
   * Computes the (rolling) average activation for each (hidden) neuron.
   */
  void compute_sparsity() {
    if (_avg_a != null) {
      for (int row = 0; row < _avg_a.size(); row++) {
        _avg_a.set(row, (float) 0.999 * (_avg_a.get(row)) + (float) 0.001 * (_a.get(row)));
      }
    }
  }

  /**
   * Helper to update the bias values
   * @param _b bias vector
   * @param _bm bias momentum vector
   * @param row index of the neuron for which we back-propagate
   * @param partial_grad partial derivative dE/dnet = dE/dy * dy/net
   * @param avg_grad2 average squared gradient for this neuron's incoming weights (only for ADADELTA)
   * @param rate learning rate
   * @param momentum momentum factor (needed only if ADADELTA isn't used)
   */
  void update_bias(final DenseVector _b, final DenseVector _bm, final int row,
                   float partial_grad, final float avg_grad2, float rate, final float momentum) {
    final boolean have_momenta = _minfo.has_momenta();
    final boolean have_ada = _minfo.adaDelta();
    final float l1 = (float)params.l1;
    final float l2 = (float)params.l2;
    final float bias = _b.get(row);
    partial_grad -= Math.signum(bias) * l1 + bias * l2;

    if (have_ada) {
      final float rho = (float)params.rho;
      final float eps = (float)params.epsilon;
      rate = computeAdaDeltaRateForBias(avg_grad2, row, _bias_ada_dx_g, rho, eps);
    }
    if (!params.nesterov_accelerated_gradient) {
      final float delta = rate * partial_grad;
      _b.add(row, delta);
      if (have_momenta) {
        _b.add(row, momentum * _bm.get(row));
        _bm.set(row, delta);
      }
    } else {
      float d = partial_grad;
      if (have_momenta) {
        _bm.set(row, _bm.get(row) * momentum);
        _bm.add(row, d);
        d = _bm.get(row);
      }
      _b.add(row, rate * d);
    }
    //update for sparsity constraint
    if (params.autoencoder && params.sparsity_beta > 0 && !(this instanceof Output) && !(this instanceof Input) && (_index != params.hidden.length)) {
      _b.add(row, -(float) (rate * params.sparsity_beta * (_avg_a._data[row] - params.average_activation)));
    }
    if (Float.isInfinite(_b.get(row))) _minfo.set_unstable();
  }


  /**
   * The learning rate
   * @param n The number of training samples seen so far (for rate_annealing greater than 0)
   * @return Learning rate
   */
  public float rate(long n) {
    return (float)(params.rate / (1 + params.rate_annealing * n));
  }

  protected float momentum() {
    return momentum(-1);
  }
  /**
   * The momentum - real number in [0, 1)
   * Can be a linear ramp from momentum_start to momentum_stable, over momentum_ramp training samples
   * @param n The number of training samples seen so far
   * @return momentum
   */
  public float momentum(long n) {
    double m = params.momentum_start;
    if( params.momentum_ramp > 0 ) {
      final long num = n != -1 ? _minfo.get_processed_total() : n;
      if( num >= params.momentum_ramp )
        m = params.momentum_stable;
      else
        m += (params.momentum_stable - params.momentum_start) * num / params.momentum_ramp;
    }
    return (float)m;
  }

  /**
   * Input layer of the Neural Network
   * This layer is different from other layers as it has no incoming weights,
   * but instead gets its activation values from the training points.
   */
  public static class Input extends Neurons {

    private FrameTask.DataInfo _dinfo; //training data
    SparseVector _svec;
    DenseVector _dvec;

    Input(int units, final FrameTask.DataInfo d) {
      super(units);
      _dinfo = d;
      _a = new DenseVector(units);
      _dvec = (DenseVector)_a;
    }

    @Override protected void bprop() { throw new UnsupportedOperationException(); }
    @Override protected void fprop(long seed, boolean training) { throw new UnsupportedOperationException(); }

    /**
     * One of two methods to set layer input values. This one is for raw double data, e.g. for scoring
     * @param seed For seeding the RNG inside (for input dropout)
     * @param data Data (training columns and responses) to extract the training columns
     *             from to be mapped into the input neuron layer
     */
    public void setInput(long seed, final double[] data) {
//      Log.info("Data: " + ArrayUtils.toString(data));
      assert(_dinfo != null);
      double [] nums = MemoryManager.malloc8d(_dinfo._nums); // a bit wasteful - reallocated each time
      int    [] cats = MemoryManager.malloc4(_dinfo._cats); // a bit wasteful - reallocated each time
      int i = 0, ncats = 0;
      for(; i < _dinfo._cats; ++i){
        // This can occur when testing data has categorical levels that are not part of training (or if there's a missing value)
        if (Double.isNaN(data[i])) {
          if (_dinfo._catMissing[i]!=0) cats[ncats++] = (_dinfo._catOffsets[i+1]-1); //use the extra level made during training
          else {
            if (!_dinfo._useAllFactorLevels)
              throw new IllegalArgumentException("Model was built without missing categorical factors in column "
                      + _dinfo.coefNames()[i] + ", but found unknown (or missing) categorical factors during scoring."
                      + "\nThe model needs to be built with use_all_factor_levels=true for this to work.");
            // else just leave all activations at 0, and since all factor levels were enabled,
            // this is OK (missing or new categorical doesn't activate any levels seen during training)
          }
        } else {
          int c = (int)data[i];
          if (_dinfo._useAllFactorLevels)
            cats[ncats++] = c + _dinfo._catOffsets[i];
          else if (c!=0)
            cats[ncats++] = c + _dinfo._catOffsets[i] - 1;
        }
      }
      final int n = data.length; // data contains only input features - no response is included
      for(;i < n;++i){
        double d = data[i];
        if(_dinfo._normMul != null) d = (d - _dinfo._normSub[i-_dinfo._cats])*_dinfo._normMul[i-_dinfo._cats];
        nums[i-_dinfo._cats] = d; //can be NaN for missing numerical data
      }
      setInput(seed, nums, ncats, cats);
    }

    /**
     * The second method used to set input layer values. This one is used directly by FrameTask.processRow() and by the method above.
     * @param seed For seeding the RNG inside (for input dropout)
     * @param nums Array containing numerical values, can be NaN
     * @param numcat Number of horizontalized categorical non-zero values (i.e., those not being the first factor of a class)
     * @param cats Array of indices, the first numcat values are the input layer unit (==column) indices for the non-zero categorical values
     *             (This allows this array to be re-usable by the caller, without re-allocating each time)
     */
    public void setInput(long seed, final double[] nums, final int numcat, final int[] cats) {
      _a = _dvec;
      Arrays.fill(_a.raw(), 0f);
      for (int i=0; i<numcat; ++i) _a.set(cats[i], 1f);
      for (int i=0; i<nums.length; ++i) _a.set(_dinfo.numStart() + i, Double.isNaN(nums[i]) ? 0f /*Always do MeanImputation during scoring*/ : (float) nums[i]);
//      Log.info("Input Layer: " + ArrayUtils.toString(_a.raw()));

      // Input Dropout
      if (_dropout == null) return;
      seed += params.seed + 0x1337B4BE;
      _dropout.randomlySparsifyActivation(_a, seed);

      if (params.sparse) {
        _svec = new SparseVector(_dvec);
        _a = _svec;
      }
    }

  }

  /**
   * Tanh neurons - most common, most stable
   */
  public static class Tanh extends Neurons {
    public Tanh(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      gemv((DenseVector)_a, _w, _previous._a, _b, _dropout != null ? _dropout.bits() : null);
      final int rows = _a.size();
      for( int row = 0; row < rows; row++ )
        _a.set(row, 1f - 2f / (1f + (float)Math.exp(2*_a.get(row)))); //evals faster than tanh(x), but is slightly less numerically stable - OK
      compute_sparsity();
    }
    // Computing partial derivative g = dE/dnet = dE/dy * dy/dnet, where dE/dy is the backpropagated error
    // dy/dnet = (1 - a^2) for y(net) = tanh(net)
    @Override protected void bprop() {
      float m = momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      if (_w instanceof DenseRowMatrix) {
        final int rows = _a.size();
        for (int row = 0; row < rows; row++) {
          if (_minfo.get_params().autoencoder && _index == _minfo.get_params().hidden.length)
            _e.set(row, autoEncoderError(row));
          float g = _e.get(row) * (1f - _a.get(row) * _a.get(row));
          bprop(row, g, r, m);
        }
      }
      else {
        bprop_sparse(r, m);
      }
    }
  }

  /**
   * Tanh neurons with dropout
   */
  public static class TanhDropout extends Tanh {
    public TanhDropout(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      if (training) {
        seed += params.seed + 0xDA7A6000;
        _dropout.fillBytes(seed);
        super.fprop(seed, true);
      }
      else {
        super.fprop(seed, false);
        ArrayUtils.mult(_a.raw(), (float)params.hidden_dropout_ratios[_index]);
      }
    }
  }

  /**
   * Maxout neurons
   */
  public static class Maxout extends Neurons {
    public Maxout(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      float max = 0;
      final int rows = _a.size();
      if (_previous._a instanceof DenseVector) {
        for( int row = 0; row < rows; row++ ) {
          _a.set(row, 0);
          if( !training || _dropout == null || _dropout.unit_active(row) ) {
            _a.set(row, Float.NEGATIVE_INFINITY);
            for( int i = 0; i < _previous._a.size(); i++ )
              _a.set(row, Math.max(_a.get(row), _w.get(row, i) * _previous._a.get(i)));
            if (Float.isInfinite(-_a.get(row))) _a.set(row, 0); //catch the case where there is dropout (and/or input sparsity) -> no max found!
            _a.add(row, _b.get(row));
            max = Math.max(_a.get(row), max);
          }
        }
        if( max > 1 ) ArrayUtils.div(_a.raw(), max);
      }
      else {
        SparseVector x = (SparseVector)_previous._a;
        for( int row = 0; row < _a.size(); row++ ) {
          _a.set(row, 0);
          if( !training || _dropout == null || _dropout.unit_active(row) ) {
//            _a.set(row, Float.NEGATIVE_INFINITY);
//            for( int i = 0; i < _previous._a.size(); i++ )
//              _a.set(row, Math.max(_a.get(row), _w.get(row, i) * _previous._a.get(i)));
            float mymax = Float.NEGATIVE_INFINITY;
            int start = x.begin()._idx;
            int end = x.end()._idx;
            for (int it = start; it < end; ++it) {
              mymax = Math.max(mymax, _w.get(row, x._indices[it]) * x._values[it]);
            }
            _a.set(row, mymax);
            if (Float.isInfinite(-_a.get(row))) _a.set(row, 0); //catch the case where there is dropout (and/or input sparsity) -> no max found!
            _a.add(row, _b.get(row));
            max = Math.max(_a.get(row), max);
          }
        }
        if( max > 1f ) ArrayUtils.div(_a.raw(), max);
      }
      compute_sparsity();
    }
    @Override protected void bprop() {
      float m = momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      if (_w instanceof DenseRowMatrix) {
        final int rows = _a.size();
        for( int row = 0; row < rows; row++ ) {
          assert (!_minfo.get_params().autoencoder);
//          if (_minfo.get_params().autoencoder && _index == _minfo.get_params().hidden.length)
//            _e.set(row, autoEncoderError(row));
          float g = _e.get(row);
//                if( _a[o] < 0 )   Not sure if we should be using maxout with a hard zero bottom
//                    g = 0;
          bprop(row, g, r, m);
        }
      }
      else {
        bprop_sparse(r, m);
      }
    }
  }

  /**
   * Maxout neurons with dropout
   */
  public static class MaxoutDropout extends Maxout {
    public MaxoutDropout(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      if (training) {
        seed += params.seed + 0x51C8D00D;
        _dropout.fillBytes(seed);
        super.fprop(seed, true);
      }
      else {
        super.fprop(seed, false);
        ArrayUtils.mult(_a.raw(), (float)params.hidden_dropout_ratios[_index]);
      }
    }
  }

  /**
   * Rectifier linear unit (ReLU) neurons
   */
  public static class Rectifier extends Neurons {
    public Rectifier(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      gemv((DenseVector)_a, _w, _previous._a, _b, _dropout != null ? _dropout.bits() : null);
      final int rows = _a.size();
      for( int row = 0; row < rows; row++ ) {
        _a.set(row, Math.max(_a.get(row), 0f));
        compute_sparsity();
      }
    }

    @Override protected void bprop() {
      float m = momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      final int rows = _a.size();
      if (_w instanceof DenseRowMatrix) {
        for (int row = 0; row < rows; row++) {
          //(d/dx)(max(0,x)) = 1 if x > 0, otherwise 0
          if (_minfo.get_params().autoencoder && _index == _minfo.get_params().hidden.length)
            _e.set(row, autoEncoderError(row));
          float g = _a.get(row) > 0f ? _e.get(row) : 0f;
          bprop(row, g, r, m);
        }
      }
      else {
        bprop_sparse(r, m);
      }
    }
  }

  /**
   * Rectifier linear unit (ReLU) neurons with dropout
   */
  public static class RectifierDropout extends Rectifier {
    public RectifierDropout(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      if (training) {
        seed += params.seed + 0x3C71F1ED;
        _dropout.fillBytes(seed);
        super.fprop(seed, true);
      }
      else {
        super.fprop(seed, false);
        ArrayUtils.mult(_a.raw(), (float)params.hidden_dropout_ratios[_index]);
      }
    }
  }

  /**
   * Abstract class for Output neurons
   */
  public static abstract class Output extends Neurons {
    Output(int units) { super(units); }
    protected void fprop(long seed, boolean training) { throw new UnsupportedOperationException(); }
    protected void bprop() { throw new UnsupportedOperationException(); }
  }

  /**
   * Output neurons for classification - Softmax
   */
  public static class Softmax extends Output {
    public Softmax(int units) { super(units); }
    protected void fprop() {
      gemv((DenseVector) _a, (DenseRowMatrix) _w, (DenseVector) _previous._a, _b, null);
      final float max = ArrayUtils.maxValue(_a.raw());
      float scale = 0f;
      final float rows = _a.size();
      for( int row = 0; row < rows; row++ ) {
        _a.set(row, (float)Math.exp(_a.get(row) - max));
        scale += _a.get(row);
      }
      for( int row = 0; row < rows; row++ ) {
        if (Float.isNaN(_a.get(row))) {
          _minfo.set_unstable();
          throw new RuntimeException("Numerical instability, predicted NaN.");
        }
        _a.raw()[row] /= scale;
      }
    }

    /**
     * Backpropagation for classification
     * Update every weight as follows: w += -rate * dE/dw
     * Compute dE/dw via chain rule: dE/dw = dE/dy * dy/dnet * dnet/dw, where net = sum(xi*wi)+b and y = activation function
     * @param target actual class label
     */
    protected void bprop(int target) {
      assert (target != missing_int_value); // no correction of weights/biases for missing label
      float m = momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      float g; //partial derivative dE/dy * dy/dnet
      final float rows = _a.size();
      for( int row = 0; row < rows; row++ ) {
        final float t = (row == target ? 1f : 0f);
        final float y = _a.get(row);
        //dy/dnet = derivative of softmax = (1-y)*y
        if (params.loss == DeepLearningModel.DeepLearningParameters.Loss.CrossEntropy) {
          //nothing else needed, -dCE/dy * dy/dnet = target - y
          //cf. http://www.stanford.edu/group/pdplab/pdphandbook/handbookch6.html
          g = t - y;
        } else {
          assert(params.loss == DeepLearningModel.DeepLearningParameters.Loss.MeanSquare);
          //-dMSE/dy = target-y
          g = (t - y) * (1f - y) * y;
        }
        // this call expects dE/dnet
        bprop(row, g, r, m);
      }
    }
  }

  /**
   * Output neurons for regression - Softmax
   */
  public static class Linear extends Output {
    public Linear(int units) { super(units); }
    protected void fprop() {
      gemv((DenseVector)_a, _w, _previous._a, _b, _dropout != null ? _dropout.bits() : null);
    }

    /**
     * Backpropagation for regression
     * @param target floating-point target value
     */
    protected void bprop(float target) {
      assert (target != missing_real_value);
      if (params.loss != DeepLearningModel.DeepLearningParameters.Loss.MeanSquare) throw new UnsupportedOperationException("Regression is only implemented for MeanSquare error.");
      final int row = 0;
      // Computing partial derivative: dE/dnet = dE/dy * dy/dnet = dE/dy * 1
      final float g = target - _a.get(row); //for MSE -dMSE/dy = target-y
      float m = momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      bprop(row, g, r, m);
    }
  }

  /**
   * Mat-Vec Plus Add (with optional row dropout)
   * @param res = a*x+y (pre-allocated, will be overwritten)
   * @param a matrix of size rows x cols
   * @param x vector of length cols
   * @param y vector of length rows
   * @param row_bits if not null, check bits of this byte[] to determine whether a row is used or not
   */
  static void gemv_naive(final float[] res, final float[] a, final float[] x, final float[] y, byte[] row_bits) {
    final int cols = x.length;
    final int rows = y.length;
    assert(res.length == rows);
    for(int row = 0; row<rows; row++) {
      res[row] = 0;
      if( row_bits != null && (row_bits[row / 8] & (1 << (row % 8))) == 0) continue;
      for(int col = 0; col<cols; col++)
        res[row] += a[row*cols+col] * x[col];
      res[row] += y[row];
    }
  }

  /**
   * Optimized Mat-Vec Plus Add (with optional row dropout)
   * Optimization: Partial sums can be evaluated in parallel
   * @param res = a*x+y (pre-allocated, will be overwritten)
   * @param a matrix of size rows x cols
   * @param x vector of length cols
   * @param y vector of length rows
   * @param row_bits if not null, check bits of this byte[] to determine whether a row is used or not
   */
  static void gemv_row_optimized(final float[] res, final float[] a, final float[] x, final float[] y, final byte[] row_bits) {
    final int cols = x.length;
    final int rows = y.length;
    assert(res.length == rows);
    final int extra=cols-cols%8;
    final int multiple = (cols/8)*8-1;
    int idx = 0;
    for (int row = 0; row<rows; row++) {
      res[row] = 0;
      if( row_bits == null || (row_bits[row / 8] & (1 << (row % 8))) != 0) {
        float psum0 = 0, psum1 = 0, psum2 = 0, psum3 = 0, psum4 = 0, psum5 = 0, psum6 = 0, psum7 = 0;
        for (int col = 0; col < multiple; col += 8) {
          int off = idx + col;
          psum0 += a[off    ] * x[col    ];
          psum1 += a[off + 1] * x[col + 1];
          psum2 += a[off + 2] * x[col + 2];
          psum3 += a[off + 3] * x[col + 3];
          psum4 += a[off + 4] * x[col + 4];
          psum5 += a[off + 5] * x[col + 5];
          psum6 += a[off + 6] * x[col + 6];
          psum7 += a[off + 7] * x[col + 7];
        }
        res[row] += psum0 + psum1 + psum2 + psum3;
        res[row] += psum4 + psum5 + psum6 + psum7;
        for (int col = extra; col < cols; col++)
          res[row] += a[idx + col] * x[col];
        res[row] += y[row];
      }
      idx += cols;
    }
  }

  /**
   * Helper to do a generic gemv: res = a*x + y
   * @param res Dense result
   * @param a Matrix (sparse or dense)
   * @param x Vector (sparse or dense)
   * @param y Dense vector to add to result
   * @param row_bits Bit mask for which rows to use
   */
  static void gemv(final DenseVector res, final Matrix a, final Vector x, final DenseVector y, byte[] row_bits) {
    if (a instanceof DenseRowMatrix && x instanceof DenseVector)
      gemv(res, (DenseRowMatrix)a, (DenseVector)x, y, row_bits); //default
    else if (a instanceof DenseColMatrix && x instanceof SparseVector)
      gemv(res, (DenseColMatrix)a, (SparseVector)x, y, row_bits); //fast for really sparse
    else if (a instanceof DenseRowMatrix && x instanceof SparseVector)
      gemv(res, (DenseRowMatrix) a, (SparseVector) x, y, row_bits); //try
    else if (a instanceof DenseColMatrix && x instanceof DenseVector)
      gemv(res, (DenseColMatrix) a, (DenseVector) x, y, row_bits); //try
    else throw new UnsupportedOperationException("gemv for matrix " + a.getClass().getSimpleName() + " and vector + " + x.getClass().getSimpleName() + " not yet implemented.");
  }

  static void gemv(final DenseVector res, final DenseRowMatrix a, final DenseVector x, final DenseVector y, byte[] row_bits) {
    gemv_row_optimized(res.raw(), a.raw(), x.raw(), y.raw(), row_bits);
  }

  static void gemv_naive(final DenseVector res, final DenseRowMatrix a, final DenseVector x, final DenseVector y, byte[] row_bits) {
    gemv_naive(res.raw(), a.raw(), x.raw(), y.raw(), row_bits);
  }

  //TODO: make optimized version for col matrix
  static void gemv(final DenseVector res, final DenseColMatrix a, final DenseVector x, final DenseVector y, byte[] row_bits) {
    final int cols = x.size();
    final int rows = y.size();
    assert(res.size() == rows);
    for(int r = 0; r<rows; r++) {
      res.set(r, 0);
    }
    for(int c = 0; c<cols; c++) {
      final float val = x.get(c);
      for(int r = 0; r<rows; r++) {
        if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
        res.add(r, a.get(r,c) * val);
      }
    }
    for(int r = 0; r<rows; r++) {
      if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
      res.add(r, y.get(r));
    }
  }

  static void gemv(final DenseVector res, final DenseRowMatrix a, final SparseVector x, final DenseVector y, byte[] row_bits) {
    final int rows = y.size();
    assert(res.size() == rows);
    for(int r = 0; r<rows; r++) {
      res.set(r, 0);
      if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
      int start = x.begin()._idx;
      int end = x.end()._idx;
      for (int it = start; it < end; ++it) {
        res.add(r, a.get(r, x._indices[it]) * x._values[it]);
      }
      res.add(r, y.get(r));
    }
  }

  static void gemv(final DenseVector res, final DenseColMatrix a, final SparseVector x, final DenseVector y, byte[] row_bits) {
    final int rows = y.size();
    assert(res.size() == rows);
    for(int r = 0; r<rows; r++) {
      res.set(r, 0);
    }
    int start = x.begin()._idx;
    int end = x.end()._idx;
    for (int it = start; it < end; ++it) {
      final float val = x._values[it];
      if (val == 0f) continue;
      for(int r = 0; r<rows; r++) {
        if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
        res.add(r, a.get(r,x._indices[it]) * val);
      }
    }
    for(int r = 0; r<rows; r++) {
      if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
      res.add(r, y.get(r));
    }
  }

  static void gemv(final DenseVector res, final SparseRowMatrix a, final SparseVector x, final DenseVector y, byte[] row_bits) {
    final int rows = y.size();
    assert(res.size() == rows);
    for(int r = 0; r<rows; r++) {
      res.set(r, 0);
      if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
      // iterate over all non-empty columns for this row
      TreeMap<Integer, Float> row = a.row(r);
      Set<Map.Entry<Integer,Float>> set = row.entrySet();

      for (Map.Entry<Integer,Float> e : set) {
        final float val = x.get(e.getKey());
        if (val != 0f) res.add(r, e.getValue() * val); //TODO: iterate over both iterators and only add where there are matching indices
      }
      res.add(r, y.get(r));
    }
  }

  static void gemv(final DenseVector res, final SparseColMatrix a, final SparseVector x, final DenseVector y, byte[] row_bits) {
    final int rows = y.size();
    assert(res.size() == rows);
    for(int r = 0; r<rows; r++) {
      res.set(r, 0);
    }
    for(int c = 0; c<a.cols(); c++) {
      TreeMap<Integer, Float> col = a.col(c);
      final float val = x.get(c);
      if (val == 0f) continue;
      for (Map.Entry<Integer,Float> e : col.entrySet()) {
        final int r = e.getKey();
        if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
        // iterate over all non-empty columns for this row
        res.add(r, e.getValue() * val);
      }
    }
    for(int r = 0; r<rows; r++) {
      if( row_bits != null && (row_bits[r / 8] & (1 << (r % 8))) == 0) continue;
      res.add(r, y.get(r));
    }
  }

  /**
   * Abstract vector interface
   */
  public abstract interface Vector {
    public abstract float get(int i);
    public abstract void set(int i, float val);
    public abstract void add(int i, float val);
    public abstract int size();
    public abstract float[] raw();
  }

  /**
   * Dense vector implementation
   */
  public static class DenseVector extends Iced implements Vector {
    private float[] _data;
    DenseVector(int len) { _data = new float[len]; }
    DenseVector(float[] v) { _data = v; }
    @Override public float get(int i) { return _data[i]; }
    @Override public void set(int i, float val) { _data[i] = val; }
    @Override public void add(int i, float val) { _data[i] += val; }
    @Override public int size() { return _data.length; }
    @Override public float[] raw() { return _data; }
  }

  /**
   * Sparse vector implementation
   */
  public static class SparseVector extends Iced implements Vector {
    private int[] _indices;
    private float[] _values;
    private int _size;
    private int _nnz;

    @Override public int size() { return _size; }
    public int nnz() { return _nnz; }

    SparseVector(float[] v) { this(new DenseVector(v)); }
    SparseVector(final DenseVector dv) {
      _size = dv.size();
      // first count non-zeros
      for (int i=0; i<dv._data.length; ++i) {
        if (dv.get(i) != 0.0f) {
          _nnz++;
        }
      }
      // only allocate what's needed
      _indices = new int[_nnz];
      _values = new float[_nnz];
      // fill values
      int idx = 0;
      for (int i=0; i<dv._data.length; ++i) {
        if (dv.get(i) != 0.0f) {
          _indices[idx] = i;
          _values[idx] = dv.get(i);
          idx++;
        }
      }
      assert(idx == nnz());
    }

    /**
     * Slow path access to i-th element
     * @param i element index
     * @return real value
     */
    @Override public float get(int i) {
      final int idx = Arrays.binarySearch(_indices, i);
      return idx < 0 ? 0f : _values[idx];
    }

    @Override
    public void set(int i, float val) {
      throw new UnsupportedOperationException("setting values in a sparse vector is not implemented.");
    }

    @Override
    public void add(int i, float val) {
      throw new UnsupportedOperationException("adding values in a sparse vector is not implemented.");
    }

    @Override
    public float[] raw() {
      throw new UnsupportedOperationException("raw access to the data in a sparse vector is not implemented.");
    }

    /**
     * Iterator over a sparse vector
     */
    public class Iterator {
      int _idx; //which nnz
      Iterator(int id) { _idx = id; }
      Iterator next() {
        _idx++;
        return this;
      }
//      boolean hasNext() {
//        return _idx < _indices.length-1;
//      }
      boolean equals(Iterator other) {
        return _idx == other._idx;
      }
      @Override
      public String toString() {
        return index() + " -> " + value();
      }
      float value() { return _values[_idx]; }
      int index() { return _indices[_idx]; }
      void setValue(float val) { _values[_idx] = val; }
    }

    public Iterator begin() { return new Iterator(0); }
    public Iterator end() { return new Iterator(_indices.length); }
  }

  /**
   * Abstract matrix interface
   */
  public abstract interface Matrix {
    abstract float get(int row, int col);
    abstract void set(int row, int col, float val);
    abstract void add(int row, int col, float val);
    abstract int cols();
    abstract int rows();
    abstract long size();
    abstract float[] raw();
  }

  /**
   * Dense row matrix implementation
   */
  public final static class DenseRowMatrix extends Iced implements Matrix {
    private float[] _data;
    private int _cols;
    private int _rows;
    DenseRowMatrix(int rows, int cols) { this(new float[cols*rows], rows, cols); }
    DenseRowMatrix(float[] v, int rows, int cols) { _data = v; _rows = rows; _cols = cols; }
    @Override public float get(int row, int col) { assert(row<_rows && col<_cols); return _data[row*_cols + col]; }
    @Override public void set(int row, int col, float val) { assert(row<_rows && col<_cols); _data[row*_cols + col] = val; }
    @Override public void add(int row, int col, float val) { assert(row<_rows && col<_cols); _data[row*_cols + col] += val; }
    @Override public int cols() { return _cols; }
    @Override public int rows() { return _rows; }
    @Override public long size() { return (long)_rows*(long)_cols; }
    public float[] raw() { return _data; }
  }

  /**
   * Dense column matrix implementation
   */
  public final static class DenseColMatrix extends Iced implements Matrix {
    private float[] _data;
    private int _cols;
    private int _rows;
    DenseColMatrix(int rows, int cols) { this(new float[cols*rows], rows, cols); }
    DenseColMatrix(float[] v, int rows, int cols) { _data = v; _rows = rows; _cols = cols; }
    DenseColMatrix(DenseRowMatrix m, int rows, int cols) { this(rows, cols); for (int row=0;row<rows;++row) for (int col=0;col<cols;++col) set(row,col, m.get(row,col)); }
    @Override public float get(int row, int col) { assert(row<_rows && col<_cols); return _data[col*_rows + row]; }
    @Override public void set(int row, int col, float val) { assert(row<_rows && col<_cols); _data[col*_rows + row] = val; }
    @Override public void add(int row, int col, float val) { assert(row<_rows && col<_cols); _data[col*_rows + row] += val; }
    @Override public int cols() { return _cols; }
    @Override public int rows() { return _rows; }
    @Override public long size() { return (long)_rows*(long)_cols; }
    public float[] raw() { return _data; }
  }

  /**
   * Sparse row matrix implementation
   */
  public final static class SparseRowMatrix implements Matrix {
    private TreeMap<Integer, Float>[] _rows;
    private int _cols;
    SparseRowMatrix(int rows, int cols) { this(null, rows, cols); }
    SparseRowMatrix(Matrix v, int rows, int cols) {
      _rows = new TreeMap[rows];
      for (int row=0;row<rows;++row) _rows[row] = new TreeMap<Integer, Float>();
      _cols = cols;
      if (v!=null)
        for (int row=0;row<rows;++row)
          for (int col=0;col<cols;++col)
            if (v.get(row,col) != 0f)
              add(row,col, v.get(row,col));
    }
    @Override public float get(int row, int col) { Float v = _rows[row].get(col); if (v == null) return 0f; else return v; }
    @Override public void add(int row, int col, float val) { set(row,col,get(row,col)+val); }
    @Override public void set(int row, int col, float val) { _rows[row].put(col, val); }
    @Override public int cols() { return _cols; }
    @Override public int rows() { return _rows.length; }
    @Override public long size() { return (long)_rows.length*(long)_cols; }
    TreeMap<Integer, Float> row(int row) { return _rows[row]; }
    public float[] raw() { throw new UnsupportedOperationException("raw access to the data in a sparse matrix is not implemented."); }
  }

  /**
   * Sparse column matrix implementation
   */
  static final class SparseColMatrix implements Matrix {
    private TreeMap<Integer, Float>[] _cols;
    private int _rows;
    SparseColMatrix(int rows, int cols) { this(null, rows, cols); }
    SparseColMatrix(Matrix v, int rows, int cols) {
      _rows = rows;
      _cols = new TreeMap[cols];
      for (int col=0;col<cols;++col) _cols[col] = new TreeMap<Integer, Float>();
      if (v!=null)
        for (int row=0;row<rows;++row)
          for (int col=0;col<cols;++col)
            if (v.get(row,col) != 0f)
              add(row,col, v.get(row,col));
    }
    @Override public float get(int row, int col) { Float v = _cols[col].get(row); if (v == null) return 0f; else return v; }
    @Override public void add(int row, int col, float val) { set(row,col,get(row,col)+val); }
    @Override public void set(int row, int col, float val) { _cols[col].put(row, val); }
    @Override public int cols() { return _cols.length; }
    @Override public int rows() { return _rows; }
    @Override public long size() { return (long)_rows*(long)_cols.length; }
    TreeMap<Integer, Float> col(int col) { return _cols[col]; }
    public float[] raw() { throw new UnsupportedOperationException("raw access to the data in a sparse matrix is not implemented."); }
  }
}
