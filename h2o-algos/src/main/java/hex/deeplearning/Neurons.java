package hex.deeplearning;

import hex.DataInfo;
import hex.Distribution;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import water.*;
import water.util.ArrayUtils;
import water.util.MathUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * This class implements the concept of a Neuron layer in a Neural Network
 * During training, every MRTask F/J thread is expected to create these neurons for every map call (Cheap to make).
 * These Neurons are NOT sent over the wire.
 * The weights connecting the neurons are in a separate class (DeepLearningModel.DeepLearningModelInfo), and will be shared per node.
 */
public abstract class Neurons {
  short _k; //number of parallel channels
  int[] _maxIncoming; //index of largest incoming signal (out of k channels)

  Distribution _dist;
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
  protected transient DeepLearningParameters params;
  protected transient int _index; //which hidden layer it is

  /**
   * Layer state (one per neuron): activity, error
   */
  public transient Storage.DenseVector _origa;
  public transient Storage.DenseVector _a;
  public transient Storage.DenseVector _e;

  /**
   * References for feed-forward connectivity
   */
  public Neurons _previous;
  public Neurons _input;
  DeepLearningModelInfo _minfo; //reference to shared model info
  public Storage.DenseRowMatrix _w;
  public Storage.DenseRowMatrix _wEA; //weights for elastic averaging
  public Storage.DenseVector _b;
  public Storage.DenseVector _bEA; //bias for elastic averaging

  /**
   * References for momentum training
   */
  Storage.DenseRowMatrix _wm;
  Storage.DenseVector _bm;

  /**
   * References for ADADELTA
   */
  Storage.DenseRowMatrix _ada_dx_g;
  Storage.DenseVector _bias_ada_dx_g;

  /**
   * For Dropout training
   */
  protected Dropout _dropout;

  /**
   * Helper to shortcut bprop
   */
  private boolean _shortcut = false;

  public Storage.DenseVector _avg_a;

  /**
   * Helper to check sanity of Neuron layers
   * @param training whether training or testing is done
   */
  void sanityCheck(boolean training) {
    if (this instanceof Input) {
      assert(_previous == null);
    } else {
      assert(_previous != null);
      if (_minfo.has_momenta()) {
        assert(_wm != null);
        assert(_bm != null);
        assert(_ada_dx_g == null);
      }
      if (_minfo.adaDelta()) {
        if (params._rho == 0) throw new IllegalArgumentException("rho must be > 0 if epsilon is >0.");
        if (params._epsilon == 0) throw new IllegalArgumentException("epsilon must be > 0 if rho is >0.");
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
  public final void init(Neurons[] neurons, int index, DeepLearningParameters p, final DeepLearningModelInfo minfo, boolean training) {
    _index = index-1;
    params = (DeepLearningParameters)p.clone();
    params._hidden_dropout_ratios = minfo.get_params()._hidden_dropout_ratios;
    params._rate *= Math.pow(params._rate_decay, index-1);
    params._distribution = minfo.get_params()._distribution;
    _dist = new Distribution(params);
    _a = new Storage.DenseVector(units);
    if (!(this instanceof Input)) {
      _e = new Storage.DenseVector(units);
    } else if (params._autoencoder && params._input_dropout_ratio > 0) {
      _origa = new Storage.DenseVector(units);
    }
    if (training && (this instanceof MaxoutDropout || this instanceof TanhDropout
            || this instanceof RectifierDropout || this instanceof ExpRectifierDropout || this instanceof Input) ) {
      _dropout = this instanceof Input ?
              (params._input_dropout_ratio==0 ? null : new Dropout(units, params._input_dropout_ratio)) //input dropout
              : new Dropout(units, params._hidden_dropout_ratios[_index]); //hidden dropout
    }
    if (!(this instanceof Input)) {
      _previous = neurons[_index]; //incoming neurons
      _minfo = minfo;
      _w = minfo.get_weights(_index); //incoming weights
      _b = minfo.get_biases(_index); //bias for this layer (starting at hidden layer)
      if(params._autoencoder && params._sparsity_beta > 0 && _index < params._hidden.length) {
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
      _shortcut = (params._fast_mode || (
              // not doing fast mode, but also don't have anything else to update (neither momentum nor ADADELTA history), and no L1/L2
              !params._adaptive_rate && !_minfo.has_momenta() && params._l1 == 0.0 && params._l2 == 0.0));
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
   *  Back propagation of error terms stored in _e (for non-final layers)
   */
  protected abstract void bprop();

  /**
   * Back-propagate gradient in output layer
   */
  final protected void bpropOutputLayer() {
    assert(_index == params._hidden.length);
    final int rows = _a.size();
    float m = _minfo.adaDelta() ? 0 : momentum();
    float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
    for( int row = 0; row < rows; row++ ) {
      final double g = _e.raw()[row];
      bprop(row, g, r, m);
    }
  }

  /**
   * Accumulation of reconstruction errors for a generic Neurons class
   * (This is only used for AutoEncoders)
   */
  protected void setOutputLayerGradient(double ignored) {
    assert (_minfo.get_params()._autoencoder && _index == _minfo.get_params()._hidden.length);
    final int rows = _a.size();
    for (int row = 0; row < rows; row++) {
      _e.set(row, autoEncoderGradient(row));
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
  final void bprop(final int row, final double partial_grad, final float rate, final float momentum) {
    if (_shortcut && partial_grad == 0f) return;
    final float rho = (float)params._rho;
    final float eps = (float)params._epsilon;
    final float l1 = (float)params._l1;
    final float l2 = (float)params._l2;
    final float max_w2 = params._max_w2;
    final boolean have_momenta = _minfo.has_momenta();
    final boolean have_ada = _minfo.adaDelta();
    final boolean nesterov = params._nesterov_accelerated_gradient;
    final boolean update_prev = _previous._e != null;
    final boolean fast_mode = params._fast_mode;
    final int cols = _previous._a.size();

    double avg_grad2 = 0;

    final int idx = row * cols;

    for( int col = 0; col < cols; col++ ) {
      int w = idx + col;

      // for Maxout, return the "winning" linear index into the matrix
      if (_k != 0) w = _k * w + _maxIncoming[row];

      final double weight = _w.raw()[w];
      if( update_prev ) _previous._e.add(col, partial_grad * weight); // propagate the error dE/dnet to the previous layer, via connecting weights
      final double previous_a = _previous._a.get(col);
      if (fast_mode && previous_a == 0) continue;

      //this is the actual gradient dE/dw
      double grad = partial_grad * previous_a - Math.signum(weight) * l1 - weight * l2;
      if (_wEA !=null) {
        grad -= params._elastic_averaging_regularization * (_w.raw()[w] -_wEA.raw()[w]);
//        Log.info("weight: my: " + _w.raw()[w] + ", consensus: " + _wEA.raw()[w] + ", delta: " + (_w.raw()[w] -_wEA.raw()[w]) + ", relative delta: " + (_w.raw()[w] -_wEA.raw()[w])/_w.raw()[w]);
      }

      // store the gradient (grad is the negative gradient)
      if (DeepLearningModelInfo.gradientCheck != null)
        DeepLearningModelInfo.gradientCheck.apply(_index, row, col, -grad);

      if (have_ada) {
        final double grad2 = grad*grad;
        avg_grad2 += grad2;
        float brate = computeAdaDeltaRateForWeight(grad, w, _ada_dx_g, rho, eps);
        _w.raw()[w] += brate * grad;
      } else {
        if (!nesterov) {
          final double delta = rate * grad;
          _w.raw()[w] += delta;
          if( have_momenta ) {
            _w.raw()[w] += momentum * _wm.raw()[w];
            _wm.raw()[w] = (float)delta;
          }
        } else {
          double tmp = grad;
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
    update_bias(_b, _bEA, _bm, row, partial_grad, avg_grad2, rate, momentum);
  }

  private void rescale_weights(final Storage.DenseRowMatrix w, final int row, final float max_w2) {
    final int cols = _previous._a.size();
    int start;
    int end;
    if (_k != 0) {
      start = _k * (row*cols           ) + _maxIncoming[row];
      end =   _k * (row*cols + (cols-1)) + _maxIncoming[row];
    } else {
      start = row * cols;
      end =   row * cols + cols;
    }
    float r2 = MathUtils.sumSquares(w.raw(), start, end);
//    float r2 = MathUtils.approxSumSquares(w.raw(), idx, idx + cols);
    if( r2 > max_w2) {
      final float scale = MathUtils.approxSqrt(max_w2 / r2);
      for( int c = start; c < end; c++ )
        w.raw()[c] *= scale;
    }
  }

  /**
   * Helper to compute the reconstruction error for auto-encoders (part of the gradient computation)
   * @param row neuron index
   * @return difference between the output (auto-encoder output layer activation) and the target (input layer activation)
   */
  protected double autoEncoderGradient(int row) {
    assert (_minfo.get_params()._autoencoder && _index == _minfo.get_params()._hidden.length);
    final double t = _input._origa != null ? _input._origa.get(row) : _input._a.get(row);
    final double y = _a.get(row);
    return _dist.gradient(t, y);
  }

  /**
   * Compute learning rate with AdaDelta, specialized for DenseRowMatrix
   * http://www.matthewzeiler.com/pubs/googleTR2012/googleTR2012.pdf
   * @param grad gradient
   * @param w neuron index
   * @param ada_dx_g Matrix holding helper values (2 floats per weight)
   * @param rho hyper-parameter #1
   * @param eps hyper-parameter #2
   * @return learning rate
   */
  private static float computeAdaDeltaRateForWeight(final double grad, final int w,
                                                  final Storage.DenseRowMatrix ada_dx_g,
                                                  final float rho, final float eps) {
    final double grad2 = grad*grad;
    ada_dx_g.raw()[2*w+1] = (float)(rho * ada_dx_g.raw()[2*w+1] + (1 - rho) * grad2);
    final float rate = MathUtils.approxSqrt((ada_dx_g.raw()[2 * w] + eps) / (ada_dx_g.raw()[2 * w + 1] + eps));
    ada_dx_g.raw()[2*w  ] = (float)(rho * ada_dx_g.raw()[2*w]   + (1 - rho) * rate * rate * grad2);
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
  private static double computeAdaDeltaRateForBias(final double grad2, final int row,
                                                  final Storage.DenseVector bias_ada_dx_g,
                                                  final float rho, final float eps) {
    bias_ada_dx_g.raw()[2*row+1] = rho * bias_ada_dx_g.raw()[2*row+1] + (1f - rho) * grad2;
    final double rate = MathUtils.approxSqrt((bias_ada_dx_g.raw()[2 * row] + eps) / (bias_ada_dx_g.raw()[2 * row + 1] + eps));
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
        _avg_a.set(row, 0.999 * (_avg_a.get(row)) + 0.001 * (_a.get(row)));
      }
    }
  }

  /**
   * Helper to update the bias values
   * @param _b bias vector
   * @param _bEA elastic average bias vector
   * @param _bm bias momentum vector
   * @param row index of the neuron for which we back-propagate
   * @param partial_grad partial derivative dE/dnet = dE/dy * dy/net
   * @param avg_grad2 average squared gradient for this neuron's incoming weights (only for ADADELTA)
   * @param rate learning rate
   * @param momentum momentum factor (needed only if ADADELTA isn't used)
   */
  private void update_bias(final Storage.DenseVector _b, final Storage.DenseVector _bEA, final Storage.DenseVector _bm, final int row,
                   double partial_grad, final double avg_grad2, double rate, final double momentum) {
    final boolean have_momenta = _minfo.has_momenta();
    final boolean have_ada = _minfo.adaDelta();
    final float l1 = (float)params._l1;
    final float l2 = (float)params._l2;
    final int b = _k != 0 ? _k*row+_maxIncoming[row] : row;
    final double bias = _b.get(b);

    partial_grad -= Math.signum(bias) * l1 + bias * l2;
    if (_bEA != null) partial_grad -= (bias - _bEA.get(b)) * params._elastic_averaging_regularization;

    if (have_ada) {
      final float rho = (float)params._rho;
      final float eps = (float)params._epsilon;
      rate = computeAdaDeltaRateForBias(avg_grad2, b, _bias_ada_dx_g, rho, eps);
    }
    if (!params._nesterov_accelerated_gradient) {
      final double delta = rate * partial_grad;
      _b.add(b, delta);
      if (have_momenta) {
        _b.add(b, momentum * _bm.get(b));
        _bm.set(b, delta);
      }
    } else {
      double d = partial_grad;
      if (have_momenta) {
        _bm.set(b, _bm.get(b) * momentum);
        _bm.add(b, d);
        d = _bm.get(b);
      }
      _b.add(b, rate * d);
    }
    //update for sparsity constraint
    if (params._autoencoder && params._sparsity_beta > 0 && !(this instanceof Output) && !(this instanceof Input) && (_index != params._hidden.length)) {
      _b.add(b, -(rate * params._sparsity_beta * (_avg_a.raw()[b] - params._average_activation)));
    }
    if (Double.isInfinite(_b.get(b))) _minfo.setUnstable();
  }


  /**
   * The learning rate
   * @param n The number of training samples seen so far (for rate_annealing greater than 0)
   * @return Learning rate
   */
  public float rate(double n) {
    return (float)(params._rate / (1 + params._rate_annealing * n));
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
  final public float momentum(double n) {
    double m = params._momentum_start;
    if( params._momentum_ramp > 0 ) {
      final double num = n != -1 ? _minfo.get_processed_total() : n;
      if( num >= params._momentum_ramp)
        m = params._momentum_stable;
      else
        m += (params._momentum_stable - params._momentum_start) * num / params._momentum_ramp;
    }
    return (float)m;
  }

  /**
   * Input layer of the Neural Network
   * This layer is different from other layers as it has no incoming weights,
   * but instead gets its activation values from the training points.
   */
  public static class Input extends Neurons {

    private DataInfo _dinfo; //training data

    Input(int units, final DataInfo d) {
      super(units);
      _dinfo = d;
      _a = new Storage.DenseVector(units);
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
        assert(_dinfo._catMissing[i] != 0); //we now *always* have a categorical level for NAs, just in case.
        if (Double.isNaN(data[i])) {
          cats[ncats] = (_dinfo._catOffsets[i+1]-1); //use the extra level for NAs made during training
        } else {
          int c = (int)data[i];

          if (_dinfo._useAllFactorLevels)
            cats[ncats] = c + _dinfo._catOffsets[i];
          else if (c!=0)
            cats[ncats] = c + _dinfo._catOffsets[i] - 1;

          // If factor level in test set was not seen by training, then turn it into an NA
          if (cats[ncats] >= _dinfo._catOffsets[i+1]) {
            cats[ncats] = (_dinfo._catOffsets[i+1]-1);
          }
        }
        ncats++;
      }
      for(;i < data.length;++i){
        double d = data[i];
        if(_dinfo._normMul != null) d = (d - _dinfo._normSub[i-_dinfo._cats])*_dinfo._normMul[i-_dinfo._cats];
        nums[i-_dinfo._cats] = d; //can be NaN for missing numerical data
      }
      setInput(seed, null, nums, ncats, cats);
    }

    /**
     * The second method used to set input layer values. This one is used directly by FrameTask.processRow() and by the method above.
     * @param seed For seeding the RNG inside (for input dropout)
     * @param nums Array containing numerical values, can be NaN
     * @param numcat Number of horizontalized categorical non-zero values (i.e., those not being the first factor of a class)
     * @param cats Array of indices, the first numcat values are the input layer unit (==column) indices for the non-zero categorical values
     *             (This allows this array to be re-usable by the caller, without re-allocating each time)
     */
    public void setInput(long seed, final int[] numIds, final double[] nums, final int numcat, final int[] cats) {
      Arrays.fill(_a.raw(), 0f);

      // random projection from fullN down to max_categorical_features
      if (params._max_categorical_features < _dinfo.fullN() - _dinfo._nums) {
        assert(nums.length == _dinfo._nums);
        final int M = nums.length + params._max_categorical_features;
//        final boolean random_projection = false;
//        final boolean hash_trick = true;
//        if (random_projection) {
//          final int N = _dinfo.fullN();
//          assert (_a.size() == M);
//
//          // sparse random projection
//          for (int i = 0; i < M; ++i) {
//            for (int c = 0; c < numcat; ++c) {
//              int j = cats[c];
//              Random rng = RandomUtils.getRNG(params._seed + i * N + j);
//              double val = 0;
//              final float rnd = rng.nextFloat();
//              if (rnd < 1. / 6.) val = Math.sqrt(3);
//              if (rnd > 5. / 6.) val = -Math.sqrt(3);
//              _a.add(i, 1f * val);
//            }
//            Random rng = RandomUtils.getRNG(params._seed + i*N + _dinfo.numStart());
//            for (int n = 0; n < nums.length; ++n) {
//              double val = 0;
//              final float rnd = rng.nextFloat();
//              if (rnd < 1. / 6.) val = Math.sqrt(3);
//              if (rnd > 5. / 6.) val = - Math.sqrt(3);
//              _a.set(i, (Double.isNaN(nums[n]) ? 0f /*Always do MeanImputation during scoring*/ : nums[n]) * val);
//            }
//          }
//        } else if (hash_trick) {
          // Use hash trick for categorical features
          assert (_a.size() == M);
          // hash N-nums.length down to M-nums.length = cM (#categorical slots - always use all numerical features)
          final int cM = params._max_categorical_features;

          assert (_a.size() == M);
          MurmurHash murmur = MurmurHash.getInstance();
          for (int i = 0; i < numcat; ++i) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            int hashval = murmur.hash(buf.putInt(cats[i]).array(), 4, (int)params._seed); // turn horizontalized categorical integer into another integer, based on seed
            _a.add(Math.abs(hashval % cM), 1f); // restrict to limited range
          }
          for (int i = 0; i < nums.length; ++i)
            _a.set(cM + i, Double.isNaN(nums[i]) ? 0f /*Always do MeanImputation during scoring*/ : nums[i]);
//        }
      } else {
        assert(_a.size() == _dinfo.fullN());
        for (int i = 0; i < numcat; ++i) _a.set(cats[i], 1f); // one-hot encode categoricals
        if (numIds != null) {
          //sparse
          for (int i = 0; i < numIds.length; ++i)
            _a.set(numIds[i], Double.isNaN(nums[i]) ? 0f /*Always do MeanImputation during scoring*/ : nums[i]);
        } else {
          //dense
          for (int i = 0; i < nums.length; ++i)
            _a.set(_dinfo.numStart() + i, Double.isNaN(nums[i]) ? 0f /*Always do MeanImputation during scoring*/ : nums[i]);
        }
      }

      // Input Dropout
      if (_dropout == null) return;
      if (params._autoencoder && params._input_dropout_ratio > 0) {
        // copy input into _origa -- needed for reconstruction error
        System.arraycopy(_a.raw(), 0, _origa.raw(), 0, _a.raw().length);
      }
      seed += params._seed + 0x1337B4BE;
      _dropout.randomlySparsifyActivation(_a, seed);
    }

  }

  /**
   * Tanh neurons - most common, most stable
   */
  public static class Tanh extends Neurons {
    public Tanh(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      gemv(_a, _w, _previous._a, _b, _dropout != null ? _dropout.bits() : null);
      final int rows = _a.size();
      for( int row = 0; row < rows; row++ )
        _a.set(row, 1. - 2. / (1. + Math.exp(2*_a.get(row)))); //evals faster than tanh(x), but is slightly less numerically stable - OK
      compute_sparsity();
    }
    // Computing partial derivative g = dE/dnet = dE/dy * dy/dnet, where dE/dy is the backpropagated error
    // dy/dnet = (1 - a^2) for y(net) = tanh(net)
    @Override protected void bprop() {
      assert (_index < _minfo.get_params()._hidden.length);
      float m = _minfo.adaDelta() ? 0 : momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      final int rows = _a.size();
      for (int row = 0; row < rows; row++) {
        double g = _e.get(row) * (1 - _a.get(row) * _a.get(row));
        bprop(row, g, r, m);
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
        seed += params._seed + 0xDA7A6000;
        _dropout.fillBytes(seed);
        super.fprop(seed, true);
      }
      else {
        super.fprop(seed, false);
        ArrayUtils.mult(_a.raw(), 1-params._hidden_dropout_ratios[_index]);
      }
    }
  }

  /**
   * Maxout neurons (picks the max out of the k activation_j = sum(A_ij*x_i) + b_j)
   * Requires k times the model parameters (weights/biases) as a "normal" neuron
   */
  public static class Maxout extends Neurons {

    public Maxout(short k, int units) { super(units);
      _k = k;
      _maxIncoming=new int[units];
      if (_k!=2) throw H2O.unimpl("Maxout is currently hardcoded for 2 channels. Trivial to enable k > 2 though.");
    }
    @Override protected void fprop(long seed, boolean training) {
      assert(_b.size() == _a.size() * _k);
      assert(_w.size() == _a.size() * _previous._a.size() * _k);
      final int rows = _a.size();
      double[] channel = new double[_k];
      for( int row = 0; row < rows; row++ ) {
        _a.set(row, 0);
        if( !training || _dropout == null || _dropout.unit_active(row) ) {
          final int cols = _previous._a.size();
          // For each neuron in the previous layer, there's k channels
          // Each channel has its own weight and bias values
          // The channel leading to the highest incoming value (W*x + b) is the "winner" and will activate this neuron
          short maxK = 0;
          for( short k = 0; k < _k; k++ ) {
            channel[k] = 0;
            for( int col = 0; col < cols; col++ ) {
              channel[k] += _w.raw()[_k*(row * cols + col) + k] * _previous._a.get(col);
            }
            channel[k] += _b.raw()[_k*row+k];
            if (channel[k] > channel[maxK]) maxK=k;
          }
          _maxIncoming[row] = maxK;
          _a.set(row, channel[maxK]);
        }
      }
      compute_sparsity();
    }

    @Override protected void bprop() {
      assert(_index != params._hidden.length);
      float m = _minfo.adaDelta() ? 0 : momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      final int rows = _a.size();
      for (int row = 0; row < rows; row++) {
        double g = _e.get(row);
        bprop(row, g, r, m);
      }
    }
  }

  /**
   * Maxout neurons with dropout
   */
  public static class MaxoutDropout extends Maxout {
    public MaxoutDropout(short k, int units) { super(k,units); }
    @Override protected void fprop(long seed, boolean training) {
      if (training) {
        seed += params._seed + 0x51C8D00D;
        _dropout.fillBytes(seed);
        super.fprop(seed, true);
      }
      else {
        super.fprop(seed, false);
        ArrayUtils.mult(_a.raw(), 1-params._hidden_dropout_ratios[_index]);
      }
    }
  }

  /**
   * Rectifier linear unit (ReLU) neurons
   */
  public static class Rectifier extends Neurons {
    public Rectifier(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      gemv(_a, _w, _previous._a, _b, _dropout != null ? _dropout.bits() : null);
      final int rows = _a.size();
      for( int row = 0; row < rows; row++ ) {
        _a.set(row, 0.5f* (_a.get(row) + Math.abs(_a.get(row)))); //faster than max(a, 0)
//        _a.set(row, Math.max(_a.get(row), 0f));
        compute_sparsity();
      }
    }

    @Override protected void bprop() {
      assert (_index < _minfo.get_params()._hidden.length);
      float m = _minfo.adaDelta() ? 0 : momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      final int rows = _a.size();
      for (int row = 0; row < rows; row++) {
        //(d/dx)(max(0,x)) = 1 if x > 0, otherwise 0
        double g = _a.get(row) > 0f ? _e.get(row) : 0f;
        bprop(row, g, r, m);
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
        seed += params._seed + 0x3C71F1ED;
        _dropout.fillBytes(seed);
        super.fprop(seed, true);
      }
      else {
        super.fprop(seed, false);
        ArrayUtils.mult(_a.raw(), 1-params._hidden_dropout_ratios[_index]);
      }
    }
  }

  public static class ExpRectifier extends Neurons {
    public ExpRectifier(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      gemv(_a, _w, _previous._a, _b, _dropout != null ? _dropout.bits() : null);
      final int rows = _a.size();
      for( int row = 0; row < rows; row++ ) {
        double x = _a.get(row);
        double val = x >= 0 ? x : Math.exp(x)-1;
        _a.set(row, val);
      }
      compute_sparsity();
    }
    // Computing partial derivative g = dE/dnet = dE/dy * dy/dnet, where dE/dy is the backpropagated error
    @Override protected void bprop() {
      assert (_index < _minfo.get_params()._hidden.length);
      float m = _minfo.adaDelta() ? 0 : momentum();
      float r = _minfo.adaDelta() ? 0 : rate(_minfo.get_processed_total()) * (1f - m);
      final int rows = _a.size();
      for (int row = 0; row < rows; row++) {
        double x = _a.get(row);
        double val = x >= 0 ? 1 : Math.exp(x);
        double g = _e.get(row) * val;
        bprop(row, g, r, m);
      }
    }
  }

  /**
   * Tanh neurons with dropout
   */
  public static class ExpRectifierDropout extends ExpRectifier {
    public ExpRectifierDropout(int units) { super(units); }
    @Override protected void fprop(long seed, boolean training) {
      if (training) {
        seed += params._seed + 0xDA7A6000;
        _dropout.fillBytes(seed);
        super.fprop(seed, true);
      }
      else {
        super.fprop(seed, false);
        ArrayUtils.mult(_a.raw(), 1-params._hidden_dropout_ratios[_index]);
      }
    }
  }

  /**
   * Abstract class for Output neurons
   */
  public static abstract class Output extends Neurons {
    Output(int units) { super(units); }
    protected void bprop() { throw new UnsupportedOperationException(); }
  }

  /**
   * Output neurons for classification - Softmax
   */
  public static class Softmax extends Output {
    public Softmax(int units) { super(units); }
    protected void fprop(long seed, boolean training) {
      gemv(_a, _w, _previous._a, _b, null);
      final double max = ArrayUtils.maxValue(_a.raw());
      double scaling = 0;
      final int rows = _a.size();
      for( int row = 0; row < rows; row++ ) {
        _a.set(row, Math.exp(_a.get(row) - max));
        scaling += _a.get(row);
      }
      for( int row = 0; row < rows; row++ ) {
        _a.raw()[row] /= scaling;
      }
    }

    /**
     * Part of backpropagation for classification
     * Update every weight as follows: w += -rate * dE/dw
     * Compute dE/dw via chain rule: dE/dw = dE/dy * dy/dnet * dnet/dw, where net = sum(xi*wi)+b and y = activation function
     * @param target actual class label (integer)
     */
    @Override protected void setOutputLayerGradient(double target) {
      assert(target == (int)target);
      double g; //partial derivative dE/dy * dy/dnet
      final int rows = _a.size();
      for( int row = 0; row < rows; row++ ) {
        final double t = (row == (int)target ? 1 : 0);
        final double y = _a.get(row);
        //dy/dnet = derivative of softmax = (1-y)*y
        switch(params._loss) {
          case Automatic:
          case CrossEntropy:
            //nothing else needed, -dCE/dy * dy/dnet = target - y
            //cf. http://www.stanford.edu/group/pdplab/pdphandbook/handbookch6.html
            g = t - y;
            break;
          case Absolute:
            g = (2 * t - 1) * (1f - y) * y; //-dL/dy = 2*t-1
            break;
          case Quadratic:
            //-dMSE/dy = target-y
            g = (t - y) * (1f - y) * y;
            break;
          case Huber:
            if (t == 0) {
              if (y < 0.5) {
                g = -4 * y; //L=2*y^2 for y<0.5
              } else {
                g = -2;   //L=2*y-0.5 for y>=0.5
              }
            } else {
              if (y > 0.5) {
                g = 4 * (1 - y); //L=2*(1-y)^2 for y<0.5
              } else {
                g = 2;   //L=2*(1-y)-0.5 for y>=0.5
              }
            }
            g *= (1f - y) * y;
            break;
          default:
            throw H2O.unimpl();
        }
        _e.set(row, g);
      }
    }
  }

  /**
   * Output neurons for regression - Linear units
   */
  public static class Linear extends Output {
    public Linear() {
      super(1);
    }
    protected void fprop(long seed, boolean training) {
      gemv(_a, _w, _previous._a, _b, _dropout != null ? _dropout.bits() : null);
    }

    /**
     * Backpropagation for regression
     * @param target floating-point target value
     */
    @Override protected void setOutputLayerGradient(double target) {
      final int row = 0;
      final double y = _a.get(row);
      double g = _dist.gradient(target, y); //y is in link space
      _e.set(row, g);
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
  static void gemv_naive(final double[] res, final float[] a, final double[] x, final double[] y, byte[] row_bits) {
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
  static void gemv_row_optimized(final double[] res, final float[] a, final double[] x, final double[] y, final byte[] row_bits) {
    final int cols = x.length;
    final int rows = y.length;
    assert(res.length == rows);
    final int extra=cols-cols%8;
    final int multiple = (cols/8)*8-1;
    int idx = 0;
    for (int row = 0; row<rows; row++) {
      res[row] = 0;
      if( row_bits == null || (row_bits[row / 8] & (1 << (row % 8))) != 0) {
        double psum0 = 0, psum1 = 0, psum2 = 0, psum3 = 0, psum4 = 0, psum5 = 0, psum6 = 0, psum7 = 0;
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
   * @param a DenseMatrix
   * @param x DenseVector
   * @param y Dense vector to add to result
   * @param row_bits Bit mask for which rows to use
   */
  static void gemv(final Storage.DenseVector res, final Storage.DenseRowMatrix a, final Storage.DenseVector x, final Storage.DenseVector y, byte[] row_bits) {
    gemv_row_optimized(res.raw(), a.raw(), x.raw(), y.raw(), row_bits);
  }

  static void gemv_naive(final Storage.DenseVector res, final Storage.DenseRowMatrix a, final Storage.DenseVector x, final Storage.DenseVector y, byte[] row_bits) {
    gemv_naive(res.raw(), a.raw(), x.raw(), y.raw(), row_bits);
  }
}
