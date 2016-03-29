package hex.deeplearning;

import hex.DataInfo;
import static java.lang.Double.isNaN;

import hex.Model;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import water.*;
import water.fvec.Frame;
import water.util.*;

import java.util.Arrays;
import java.util.Random;


/**
 * This class contains the state of the Deep Learning model
 * This will be shared: one per node
 */
final public class DeepLearningModelInfo extends Iced {

  public TwoDimTable summaryTable;

  public DataInfo data_info;
  public DataInfo data_info() {
    return data_info;
  }

  // model is described by parameters and the following arrays
  private Storage.DenseRowMatrix[] dense_row_weights; //one 2D weight matrix per layer (stored as a 1D array each)
  private Storage.DenseVector[] biases; //one 1D bias array per layer
  private Storage.DenseVector[] avg_activations; //one 1D array per hidden layer

  // helpers for storing previous step deltas
  // Note: These two arrays *could* be made transient and then initialized freshly in makeNeurons() and in DeepLearningTask.initLocal()
  // But then, after each reduction, the weights would be lost and would have to restart afresh -> not *exactly* right, but close...
  private Storage.DenseRowMatrix[] dense_row_weights_momenta;
  private Storage.DenseVector[] biases_momenta;

  // helpers for AdaDelta
  private Storage.DenseRowMatrix[] dense_row_ada_dx_g;
  private Storage.DenseVector[] biases_ada_dx_g;

  private boolean[] _saw_missing_cats;  // whether missing value was encountered for each categorical predictor - needed for varimp

  // compute model size (number of model parameters required for making predictions)
  // momenta are not counted here, but they are needed for model building
  public long size() {
    long siz = 0;
    for (Storage.DenseRowMatrix w : dense_row_weights) if (w != null) siz += w.size();
    for (Storage.Vector b : biases) siz += b.size();
    return siz;
  }

  /**
   * Check whether a missing value was found for every categorical predictor
   * @param cats activation of categorical buckets for a given row
   */
  void checkMissingCats(int[] cats)  {
    if (cats == null) return;
    if (_saw_missing_cats == null) return;
    for (int i=0; i<cats.length; ++i) {
      assert(data_info._catMissing[i]); //have a missing bucket for each categorical
      if (_saw_missing_cats[i]) continue;
      _saw_missing_cats[i] = (cats[i] == data_info._catOffsets[i+1]-1);
    }
  }

  // accessors to (shared) weights and biases - those will be updated racily (c.f. Hogwild!)
  boolean has_momenta() {
    return get_params()._momentum_start != 0 || get_params()._momentum_stable != 0;
  }

  boolean adaDelta() {
    return get_params()._adaptive_rate;
  }

  public final Storage.DenseRowMatrix get_weights(int i) {
    return dense_row_weights[i];
  }

  public final Storage.DenseVector get_biases(int i) {
    return biases[i];
  }

  public final Storage.DenseRowMatrix get_weights_momenta(int i) {
    return dense_row_weights_momenta[i];
  }

  public final Storage.DenseVector get_biases_momenta(int i) {
    return biases_momenta[i];
  }

  public final Storage.DenseRowMatrix get_ada_dx_g(int i) {
    return dense_row_ada_dx_g[i];
  }

  public final Storage.DenseVector get_biases_ada_dx_g(int i) {
    return biases_ada_dx_g[i];
  }

  //accessor to shared parameter defining avg activations
  public final Storage.DenseVector get_avg_activations(int i) {
    return avg_activations[i];
  }

  public DeepLearningParameters parameters;
  Key<Model> _model_id;
  public final DeepLearningParameters get_params() { return parameters; }
  public final void set_params(DeepLearningParameters p, Key<Model> model_id ) {
    parameters = (DeepLearningParameters) p.clone();
    _model_id = model_id;
  }

  private double[] mean_rate;
  private double[] rms_rate;
  private double[] mean_bias;
  private double[] rms_bias;
  private double[] mean_weight;
  public double[] rms_weight;
  public double[] mean_a;

  private volatile boolean unstable = false;
  public boolean isUnstable() { return unstable; }
  public void setUnstable() {
    if (!unstable) computeStats();
    unstable = true;
  }

  private long processed_global;
  public synchronized long get_processed_global() { return processed_global; }
  public synchronized void set_processed_global(long p) { processed_global = p; }
  public synchronized void add_processed_global(long p) { processed_global += p; }
  private long processed_local;
  public synchronized long get_processed_local() { return processed_local; }
  public synchronized void set_processed_local(long p) { processed_local = p; }
  public synchronized void add_processed_local(long p) { processed_local += p; }
  public synchronized long get_processed_total() { return processed_global + processed_local; }

  // package local helpers
  int[] units; //number of neurons per layer, extracted from parameters and from datainfo

  final boolean _classification; // Classification cache (nclasses>1)
  final Frame _train;         // Prepared training frame
  final Frame _valid;         // Prepared validation frame

  /**
   * Dummy constructor, only to be used for deserialization from autobuffer
   */
  private DeepLearningModelInfo() {
    super(); // key is null
    _classification = false;
    _train = _valid = null;
  }

  /**
   * Main constructor
   * @param params Model parameters
   * @param dinfo Data Info
   * @param nClasses number of classes (1 for regression, 0 for autoencoder)
   * @param train User-given training data frame, prepared by AdaptTestTrain
   * @param valid User-specified validation data frame, prepared by AdaptTestTrain
   */
  public DeepLearningModelInfo(final DeepLearningParameters params, Key model_id, final DataInfo dinfo, int nClasses, Frame train, Frame valid) {
    _classification = nClasses > 1;
    _train = train;
    _valid = valid;
    data_info = dinfo;
    parameters = (DeepLearningParameters) params.clone(); //make a copy, don't change model's parameters
    _model_id = model_id;
    DeepLearningParameters.Sanity.modifyParms(parameters, parameters, nClasses); //sanitize the model_info's parameters

    final int num_input = dinfo.fullN();
    final int num_output = get_params()._autoencoder ? num_input : (_classification ? train.lastVec().cardinality() : 1);
    if (!get_params()._autoencoder) assert(num_output == nClasses);

    _saw_missing_cats = dinfo._cats > 0 ? new boolean[data_info._cats] : null;
    assert (num_input > 0);
    assert (num_output > 0);
    if (has_momenta() && adaDelta())
      throw new IllegalArgumentException("Cannot have non-zero momentum and adaptive rate at the same time.");
    final int layers = get_params()._hidden.length;
    // units (# neurons for each layer)
    units = new int[layers + 2];
    if (get_params()._max_categorical_features <= Integer.MAX_VALUE - dinfo._nums)
      units[0] = Math.min(dinfo._nums + get_params()._max_categorical_features, num_input);
    else
      units[0] = num_input;
    System.arraycopy(get_params()._hidden, 0, units, 1, layers);
    units[layers + 1] = num_output;

    boolean printLevels = units[0] > 1000L;
    boolean warn = units[0] > 100000L;
    if (printLevels) {
      final String[][] domains = dinfo._adaptedFrame.domains();
      int[] levels = new int[domains.length];
      for (int i = 0; i < levels.length; ++i) {
        levels[i] = domains[i] != null ? domains[i].length : 0;
      }
      Arrays.sort(levels);
      if (warn) {
        Log.warn("===================================================================================================================================");
        Log.warn(num_input + " input features" + (dinfo._cats > 0 ? " (after categorical one-hot encoding)" : "") + ". Can be slow and require a lot of memory.");
      }
      if (levels[levels.length - 1] > 0) {
        int levelcutoff = levels[levels.length - 1 - Math.min(10, levels.length - 1)];
        int count = 0;
        for (int i = 0; i < dinfo._adaptedFrame.numCols() - (get_params()._autoencoder ? 0 : 1) && count < 10; ++i) {
          if (dinfo._adaptedFrame.domains()[i] != null && dinfo._adaptedFrame.domains()[i].length >= levelcutoff) {
            if (warn) {
              Log.warn("Categorical feature '" + dinfo._adaptedFrame._names[i] + "' has cardinality " + dinfo._adaptedFrame.domains()[i].length + ".");
            } else {
              Log.info("Categorical feature '" + dinfo._adaptedFrame._names[i] + "' has cardinality " + dinfo._adaptedFrame.domains()[i].length + ".");
            }
          }
          count++;
        }
      }
      if (warn) {
        Log.warn("Suggestions:");
        Log.warn(" *) Limit the size of the first hidden layer");
        if (dinfo._cats > 0) {
          Log.warn(" *) Limit the total number of one-hot encoded features with the parameter 'max_categorical_features'");
          Log.warn(" *) Run h2o.interaction(...,pairwise=F) on high-cardinality categorical columns to limit the factor count, see http://learn.h2o.ai");
        }
        Log.warn("===================================================================================================================================");
      }
    }

    int[] mult = new int[layers + 1];
    for (int i=0;i<layers;++i) {
      mult[i] = (get_params()._activation == DeepLearningParameters.Activation.Maxout || get_params()._activation == DeepLearningParameters.Activation.MaxoutWithDropout) ? 2 : 1;
    }
    mult[layers]=1; //Output is never Maxout

    // weights (to connect layers)
    dense_row_weights = new Storage.DenseRowMatrix[layers + 1];
    dense_row_weights[0] = new Storage.DenseRowMatrix(mult[0]*units[1], units[0]);
    for (int i = 1; i <= layers; ++i)
      dense_row_weights[i] = new Storage.DenseRowMatrix(mult[i] * units[i + 1] /*rows*/, units[i] /*cols*/);

    // biases (only for hidden layers and output layer)
    biases = new Storage.DenseVector[layers + 1];
    for (int i = 0; i <= layers; ++i)
      biases[i] = new Storage.DenseVector(mult[i] * units[i+1]);
    // average activation (only for hidden layers)
    if (get_params()._autoencoder && get_params()._sparsity_beta > 0) {
      avg_activations = new Storage.DenseVector[layers];
      mean_a = new double[layers];
      for (int i = 0; i < layers; ++i)
        avg_activations[i] = new Storage.DenseVector(mult[i] * units[i + 1]);
    }
    allocateHelperArrays();
    // for diagnostics
    mean_rate = new double[units.length-1];
    rms_rate = new double[units.length-1];
    mean_bias = new double[units.length-1];
    rms_bias = new double[units.length-1];
    mean_weight = new double[units.length-1];
    rms_weight = new double[units.length-1];
  }

  // deep clone all weights/biases
  DeepLearningModelInfo deep_clone() {
    AutoBuffer ab = new AutoBuffer();
    this.write(ab);
    ab.flipForReading();
    return (DeepLearningModelInfo) new DeepLearningModelInfo().read(ab);
  }

  /**
   * Allocate helper arrays for momentum/learning rate, etc.
   */
  void allocateHelperArrays() {
    int[] mult = new int[units.length-1];
    for (int i=0;i<units.length-1;++i) {
      mult[i] = (get_params()._activation == DeepLearningParameters.Activation.Maxout || get_params()._activation == DeepLearningParameters.Activation.MaxoutWithDropout) ? 2 : 1;
    }
    mult[units.length-2]=1; //Output is never Maxout

    if (has_momenta()) {
      dense_row_weights_momenta = new Storage.DenseRowMatrix[dense_row_weights.length];
      if (dense_row_weights[0] != null)
        dense_row_weights_momenta[0] = new Storage.DenseRowMatrix(mult[0]*units[1], units[0]);
      for (int i = 1; i < dense_row_weights_momenta.length; ++i)
        dense_row_weights_momenta[i] = new Storage.DenseRowMatrix(mult[i]*units[i + 1], units[i]);
      biases_momenta = new Storage.DenseVector[biases.length];
      for (int i = 0; i < biases_momenta.length; ++i)
        biases_momenta[i] = new Storage.DenseVector(mult[i]*units[i + 1]);
    } else if (adaDelta()) {
      dense_row_ada_dx_g = new Storage.DenseRowMatrix[dense_row_weights.length];
      //AdaGrad
      dense_row_ada_dx_g[0] = new Storage.DenseRowMatrix(mult[0]*2*units[1], units[0]);
      for (int i = 1; i < dense_row_ada_dx_g.length; ++i) {
        dense_row_ada_dx_g[i] = new Storage.DenseRowMatrix(mult[i]*units[i + 1], 2 * units[i]);
      }
      biases_ada_dx_g = new Storage.DenseVector[biases.length];
      for (int i = 0; i < biases_ada_dx_g.length; ++i) {
        biases_ada_dx_g[i] = new Storage.DenseVector(mult[i]*2* units[i + 1]);
      }
    }
  }

  /**
   * Create a summary table
   * @return TwoDimTable with the summary of the model
   */
  TwoDimTable createSummaryTable() {
    computeStats();
    Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(this);
    long byte_size = new AutoBuffer().put(this).buf().length;
    TwoDimTable table = new TwoDimTable(
            "Status of Neuron Layers",
            (!get_params()._autoencoder ? ("predicting " + data_info._adaptedFrame.lastVecName() + ", ") : "") +
                    (get_params()._autoencoder ? "auto-encoder" :
                            _classification ? (units[units.length - 1] + "-class classification") : "regression")
                    + ", " + get_params()._distribution + " distribution, " + get_params()._loss + " loss, "
                    + String.format("%,d", size()) + " weights/biases, " + PrettyPrint.bytes(byte_size) + ", "
                    + String.format("%,d", get_processed_global()) + " training samples, "
                    + "mini-batch size " + String.format("%,d", get_params()._mini_batch_size),
            new String[neurons.length],
            new String[]{"Layer", "Units", "Type", "Dropout", "L1", "L2",
                    "Mean Rate", "Rate RMS", "Momentum",
                    "Mean Weight", "Weight RMS",
                    "Mean Bias", "Bias RMS"
            },
            new String[]{"int", "int", "string", "double", "double", "double",
                    "double", "double", "double",
                    "double", "double",
                    "double", "double"
            },
            new String[]{"%d", "%d", "%s", "%2.2f %%", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f"},
            "");

    for (int i = 0; i < neurons.length; ++i) {
      table.set(i, 0, i + 1);
      table.set(i, 1, neurons[i].units);
      table.set(i, 2, neurons[i].getClass().getSimpleName());

      if (i == 0) {
        table.set(i, 3, neurons[i].params._input_dropout_ratio * 100);
        continue;
      } else if (i < neurons.length - 1) {
        if (neurons[i].params._hidden_dropout_ratios == null) {
          table.set(i, 3, 0);
        } else {
          table.set(i, 3, neurons[i].params._hidden_dropout_ratios[i - 1] * 100);
        }
      }
      table.set(i, 4, neurons[i].params._l1);
      table.set(i, 5, neurons[i].params._l2);
      table.set(i, 6, (get_params()._adaptive_rate ? mean_rate[i-1] : neurons[i].rate(get_processed_total())));
      table.set(i, 7, (get_params()._adaptive_rate ? rms_rate[i-1] : 0));
      table.set(i, 8, get_params()._adaptive_rate ? 0 : neurons[i].momentum(get_processed_total()));
      table.set(i, 9, mean_weight[i-1]);
      table.set(i, 10, rms_weight[i-1]);
      table.set(i, 11, mean_bias[i-1]);
      table.set(i, 12, rms_bias[i-1]);
    }
    summaryTable = table;
    return summaryTable;
  }

  /**
   * Print a summary table
   * @return String containing ASCII version of summary table
   */
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    if (!get_params()._quiet_mode) {
      if (get_params()._sparsity_beta > 0) {
        for (int k = 0; k < get_params()._hidden.length; k++) {
          sb.append("Average activation in hidden layer ").append(k).append(" is  ").append(mean_a[k]).append(" \n");
        }
      }
      createSummaryTable();
      sb.append(summaryTable.toString(1));
    }
    return sb.toString();
  }

  /**
   * Debugging printout
   * @return String with useful info
   */
  public String toStringAll() {
    StringBuilder sb = new StringBuilder();
    sb.append(toString());

    for (int i = 0; i < units.length - 1; ++i)
      sb.append("\nweights[").append(i).append("][]=").append(Arrays.toString(get_weights(i).raw()));
    for (int i = 0; i < units.length - 1; ++i) {
      sb.append("\nbiases[").append(i).append("][]=").append(Arrays.toString(get_biases(i).raw()));
    }
    if (has_momenta()) {
      for (int i = 0; i < units.length - 1; ++i)
        sb.append("\nweights_momenta[").append(i).append("][]=").append(Arrays.toString(get_weights_momenta(i).raw()));
    }
    if (biases_momenta != null) {
      for (int i = 0; i < units.length - 1; ++i) {
        sb.append("\nbiases_momenta[").append(i).append("][]=").append(Arrays.toString(biases_momenta[i].raw()));
      }
    }
    sb.append("\nunits[]=").append(Arrays.toString(units));
    sb.append("\nprocessed global: ").append(get_processed_global());
    sb.append("\nprocessed local:  ").append(get_processed_local());
    sb.append("\nprocessed total:  ").append(get_processed_total());
    sb.append("\n");
    return sb.toString();
  }

  /**
   * Initialize weights/biases
   */
  void initializeMembers(Key<Frame>[] initial_weights, Key<Frame>[] initial_biases) {
    randomizeWeights();
    //TODO: determine good/optimal/best initialization scheme for biases
    // hidden layers
    for (int i = 0; i < get_params()._hidden.length; ++i) {
      if (get_params()._activation == DeepLearningParameters.Activation.Rectifier
              || get_params()._activation == DeepLearningParameters.Activation.RectifierWithDropout
              || get_params()._activation == DeepLearningParameters.Activation.Maxout
              || get_params()._activation == DeepLearningParameters.Activation.MaxoutWithDropout
              ) {
//          Arrays.fill(biases[i], 1.); //old behavior
        Arrays.fill(biases[i].raw(), i == 0 ? 0.5f : 1f); //new behavior, might be slightly better
      } else if (get_params()._activation == DeepLearningParameters.Activation.Tanh || get_params()._activation == DeepLearningParameters.Activation.TanhWithDropout) {
        Arrays.fill(biases[i].raw(), 0f);
      }
    }
    Arrays.fill(biases[biases.length - 1].raw(), 0f); //output layer
    if (initial_weights!=null || initial_biases!=null) {
      Log.info("Initializing initial model state from user-given weights/biases.");
      for (int i = 0; i < get_params()._hidden.length+1; ++i) {
        if (initial_weights[i] == null) {
          Log.info("No user-given weight matrix given for weights #" + (i+1) + ". Initializing those weights randomly.");
          continue;
        }
        if (initial_biases[i] == null) {
          Log.info("No user-given bias vector given for biases #" + (i+1) + ". Initializing those biases randomly.");
          continue;
        }
        Frame w = initial_weights[i].get();
        if (w==null) {
          throw new IllegalArgumentException("User-given weight matrix for weights #" + (i+1) + " '" + initial_weights[i].toString() + "' not found. Initializing those weights randomly.");
        }
        if (w.numRows() != get_weights(i).rows() || w.numCols() != get_weights(i).cols()) {
          throw new IllegalArgumentException("Dimensionality mismatch: initial_weights matrix #" + i +
                  " should have " +  get_weights(i).rows() + " rows and " + get_weights(i).cols()
                  + " columns, but has " + w.numRows() + " rows and " + w.numCols() + " columns.");
        }
        Frame b = initial_biases[i].get();
        if (b==null) {
          throw new IllegalArgumentException("User-given bias vector for biases #" + (i+1) + " '" + initial_biases[i].toString() + "' not found. Initializing those biases randomly.");
        }
        if (b.numRows() != get_biases(i).size() || b.numCols() != 1) {
          throw new IllegalArgumentException("Dimensionality mismatch: initial_biases vector #" + i +
                  " should have " +  get_biases(i).size() + " rows and 1"
                  + " column, but has " + b.numRows() + " rows and " + b.numCols() + " column(s).");
        }
        for (int c=0; c<w.numCols(); ++c)
          for (int r=0; r<w.numRows(); ++r)
            get_weights(i).set(r,c,(float)w.vec(c).at(r));
        for (int r=0; r<w.numRows(); ++r)
          get_biases(i).set(r,(float)b.vec(0).at(r));
      }
    }
    else {
      Log.info("Created random initial model state.");
    }
  }

  /**
   * Fill weights and biases from a pretrained autoencoder model
   * @param autoencoder Autoencoder DL model with matching inputs and hidden layers
   */
  void initializeFromPretrainedModel(DeepLearningModelInfo autoencoder) {
    assert(autoencoder.parameters._autoencoder);
    randomizeWeights();
    // now overwrite the weights with those from the pretrained model
    for (int w = 0; w < dense_row_weights.length-1 /*skip output layer*/; ++w) {
      if (get_weights(w).rows() != autoencoder.get_weights(w).rows())
        throw new IllegalArgumentException("Mismatch between weights in pretrained model and this model: rows in layer " + w + ": " + autoencoder.get_weights(w).rows() + " vs " + get_weights(w).rows() +
        ". Enable ignored_const_cols for both models and/or check categorical levels for consistency.");
      if (get_weights(w).cols() != autoencoder.get_weights(w).cols())
        throw new IllegalArgumentException("Mismatch between weights in pretrained model and this model: cols in layer " + w + ": " + autoencoder.get_weights(w).cols() + " vs " + get_weights(w).cols() +
      ". Enable ignored_const_cols for both models and/or check categorical levels for consistency.");
      for (int i = 0; i < get_weights(w).rows(); i++) {
        for (int j = 0; j < get_weights(w).cols(); j++) {
          get_weights(w).set(i, j, autoencoder.get_weights(w).get(i, j));
        }
      }
    }
    for (int i = 0; i < get_params()._hidden.length; ++i) {
      for (int j = 0; j < biases[i].raw().length; ++j) {
        biases[i].set(j, autoencoder.biases[i].get(j));
      }
    }
    Arrays.fill(biases[biases.length - 1].raw(), 0f); //output layer
  }

  /**
   * Add another model info into this
   * This will add the weights/biases/learning rate helpers, and the number of processed training samples
   * Note: It will NOT add the elastic averaging helpers, which are always kept constant (they already are the result of a reduction)
   * @param other Other DeepLearningModelInfo to add into this one
   */
  public void add(DeepLearningModelInfo other) {
    for (int i = 0; i < dense_row_weights.length; ++i)
      ArrayUtils.add(get_weights(i).raw(), other.get_weights(i).raw());
    for (int i = 0; i < biases.length; ++i) ArrayUtils.add(biases[i].raw(), other.biases[i].raw());
    if (avg_activations != null)
      for (int i = 0; i < avg_activations.length; ++i)
        ArrayUtils.add(avg_activations[i].raw(), other.biases[i].raw());
    if (has_momenta()) {
      assert (other.has_momenta());
      for (int i = 0; i < dense_row_weights_momenta.length; ++i)
        ArrayUtils.add(get_weights_momenta(i).raw(), other.get_weights_momenta(i).raw());
      for (int i = 0; i < biases_momenta.length; ++i)
        ArrayUtils.add(biases_momenta[i].raw(), other.biases_momenta[i].raw());
    }
    if (adaDelta()) {
      assert (other.adaDelta());
      for (int i = 0; i < dense_row_ada_dx_g.length; ++i) {
        ArrayUtils.add(get_ada_dx_g(i).raw(), other.get_ada_dx_g(i).raw());
      }
    }
    add_processed_local(other.get_processed_local());
  }

  /**
   * Multiply all weights/biases by a real-valued number
   * @param N multiplication factor
   */
  protected void mult(double N) {
    div(1 / N);
  }

  /**
   * Divide all weights/biases by a real-valued number
   * @param N divisor
   */
  protected void div(double N) {
    for (int i = 0; i < dense_row_weights.length; ++i)
      ArrayUtils.div(get_weights(i).raw(), (float)N);
    for (Storage.Vector bias : biases) ArrayUtils.div(bias.raw(), N);
    if (avg_activations != null)
      for (Storage.Vector avgac : avg_activations)
        ArrayUtils.div(avgac.raw(), N);
    if (has_momenta()) {
      for (int i = 0; i < dense_row_weights_momenta.length; ++i)
        ArrayUtils.div(get_weights_momenta(i).raw(), (float)N);
      for (Storage.Vector bias_momenta : biases_momenta) ArrayUtils.div(bias_momenta.raw(), N);
    }
    if (adaDelta()) {
      for (int i = 0; i < dense_row_ada_dx_g.length; ++i) {
        ArrayUtils.div(get_ada_dx_g(i).raw(), (float)N);
      }
    }
  }

  double uniformDist(Random rand, double min, double max) {
    return min + rand.nextFloat() * (max - min);
  }

  /**
   * Initialization of neural net weights
   * cf. http://machinelearning.wustl.edu/mlpapers/paper_files/AISTATS2010_GlorotB10.pdf
   */
  private void randomizeWeights() {
    for (int w = 0; w < dense_row_weights.length; ++w) {
      final Random rng = water.util.RandomUtils.getRNG(get_params()._seed + 0xBAD5EED + w + 1); //to match NeuralNet behavior
      final double range = Math.sqrt(6. / (units[w] + units[w + 1]));
      for (int i = 0; i < get_weights(w).rows(); i++) {
        for (int j = 0; j < get_weights(w).cols(); j++) {
          if (get_params()._initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.UniformAdaptive) {
            // cf. http://machinelearning.wustl.edu/mlpapers/paper_files/AISTATS2010_GlorotB10.pdf
            if (w == dense_row_weights.length - 1 && _classification)
              get_weights(w).set(i, j, (float) (4. * uniformDist(rng, -range, range))); //Softmax might need an extra factor 4, since it's like a sigmoid
            else
              get_weights(w).set(i, j, (float) uniformDist(rng, -range, range));
          } else if (get_params()._initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.Uniform) {
            get_weights(w).set(i, j, (float) uniformDist(rng, -get_params()._initial_weight_scale, get_params()._initial_weight_scale));
          } else if (get_params()._initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.Normal) {
            get_weights(w).set(i, j, (float) (rng.nextGaussian() * get_params()._initial_weight_scale));
          }
        }
      }
    }
  }

  // TODO: Add "subset randomize" function
//        int count = Math.min(15, _previous.units);
//        double min = -.1f, max = +.1f;
//        //double min = -1f, max = +1f;
//        for( int o = 0; o < units; o++ ) {
//          for( int n = 0; n < count; n++ ) {
//            int i = rand.nextInt(_previous.units);
//            int w = o * _previous.units + i;
//            _w[w] = uniformDist(rand, min, max);
//          }
//        }

  /**
   * Compute Variable Importance, based on
   * GEDEON: DATA MINING OF INPUTS: ANALYSING MAGNITUDE AND FUNCTIONAL MEASURES
   *
   * @return variable importances for input features
   */
  public float[] computeVariableImportances() {
    float[] vi = new float[units[0]];
    Arrays.fill(vi, 0f);

    float[][] Qik = new float[units[0]][units[2]]; //importance of input i on output k
    float[] sum_wj = new float[units[1]]; //sum of incoming weights into first hidden layer
    float[] sum_wk = new float[units[2]]; //sum of incoming weights into output layer (or second hidden layer)
    for (float[] Qi : Qik) Arrays.fill(Qi, 0f);
    Arrays.fill(sum_wj, 0f);
    Arrays.fill(sum_wk, 0f);

    // compute sum of absolute incoming weights
    for (int j = 0; j < units[1]; j++) {
      for (int i = 0; i < units[0]; i++) {
        float wij = get_weights(0).get(j, i);
        sum_wj[j] += Math.abs(wij);
      }
    }
    for (int k = 0; k < units[2]; k++) {
      for (int j = 0; j < units[1]; j++) {
        float wjk = get_weights(1).get(k, j);
        sum_wk[k] += Math.abs(wjk);
      }
    }
    // compute importance of input i on output k as product of connecting weights going through j
    for (int i = 0; i < units[0]; i++) {
      for (int k = 0; k < units[2]; k++) {
        for (int j = 0; j < units[1]; j++) {
          float wij = get_weights(0).get(j, i);
          float wjk = get_weights(1).get(k, j);
          //Qik[i][k] += Math.abs(wij)/sum_wj[j] * wjk; //Wong,Gedeon,Taggart '95
          Qik[i][k] += Math.abs(wij) / sum_wj[j] * Math.abs(wjk) / sum_wk[k]; //Gedeon '97
        }
      }
    }
    // normalize Qik over all outputs k
    for (int k = 0; k < units[2]; k++) {
      float sumQk = 0;
      for (int i = 0; i < units[0]; i++) sumQk += Qik[i][k];
      for (int i = 0; i < units[0]; i++) Qik[i][k] /= sumQk;
    }
    // importance for feature i is the sum over k of i->k importances
    for (int i = 0; i < units[0]; i++) vi[i] = ArrayUtils.sum(Qik[i]);

    //normalize importances such that max(vi) = 1
    ArrayUtils.div(vi, ArrayUtils.maxValue(vi));

    // zero out missing categorical variables if they were never seen
    if (_saw_missing_cats != null) {
      for (int i = 0; i < _saw_missing_cats.length; ++i) {
        assert (data_info._catMissing[i]); //have a missing bucket for each categorical
        if (!_saw_missing_cats[i]) vi[data_info._catOffsets[i + 1] - 1] = 0;
      }
    }
    return vi;
  }

  /**
   * Compute statistics about this model on all nodes
   */
  public void computeStats() {
    float[][] rate = get_params()._adaptive_rate ? new float[units.length - 1][] : null;

    if (get_params()._autoencoder && get_params()._sparsity_beta > 0) {
      for (int k = 0; k < get_params()._hidden.length; k++) {
        mean_a[k] = 0;
        for (int j = 0; j < avg_activations[k].size(); j++)
          mean_a[k] += avg_activations[k].get(j);
        mean_a[k] /= avg_activations[k].size();
      }
    }

    for (int y = 0; y < units.length-1; y++) {
      mean_rate[y] = rms_rate[y] = 0;
      mean_bias[y] = rms_bias[y] = 0;
      mean_weight[y] = rms_weight[y] = 0;
      for (int u = 0; u < biases[y].size(); u++) {
        mean_bias[y] += biases[y].get(u);
      }
      if (rate != null) rate[y] = new float[get_weights(y).raw().length];
      for (int u = 0; u < get_weights(y).raw().length; u++) {
        mean_weight[y] += get_weights(y).raw()[u];
        if (rate != null) {
//            final float RMS_dx = (float)Math.sqrt(ada[y][2*u]+(float)get_params().epsilon);
//            final float invRMS_g = (float)(1/Math.sqrt(ada[y][2*u+1]+(float)get_params().epsilon));
          final float RMS_dx = MathUtils.approxSqrt(get_ada_dx_g(y).raw()[2 * u] + (float) get_params()._epsilon);
          final float invRMS_g = MathUtils.approxInvSqrt(get_ada_dx_g(y).raw()[2 * u + 1] + (float) get_params()._epsilon);
          rate[y][u] = RMS_dx * invRMS_g; //not exactly right, RMS_dx should be from the previous time step -> but close enough for diagnostics.
          mean_rate[y] += rate[y][u];
        }
      }
      mean_bias[y] /= biases[y].size();
      mean_weight[y] /= get_weights(y).size();
      if (rate != null) mean_rate[y] /= rate[y].length;
      for (int u = 0; u < biases[y].size(); u++) {
        final double db = biases[y].get(u) - mean_bias[y];
        rms_bias[y] += db * db;
      }
      for (int u = 0; u < get_weights(y).size(); u++) {
        final double dw = get_weights(y).raw()[u] - mean_weight[y];
        rms_weight[y] += dw * dw;
        if (rate != null) {
          final double drate = rate[y][u] - mean_rate[y];
          rms_rate[y] += drate * drate;
        }
      }
      rms_bias[y] = MathUtils.approxSqrt(rms_bias[y] / biases[y].size());
      rms_weight[y] = MathUtils.approxSqrt(rms_weight[y] / get_weights(y).size());
      if (rate != null) rms_rate[y] = MathUtils.approxSqrt(rms_rate[y]/ rate[y].length);
//        rms_bias[y] = (float)Math.sqrt(rms_bias[y]/biases[y].length);
//        rms_weight[y] = (float)Math.sqrt(rms_weight[y]/weights[y].length);
//        if (rate != null) rms_rate[y] = (float)Math.sqrt(rms_rate[y]/rate[y].length);

      // Abort the run if weights or biases are unreasonably large (Note that all input values are normalized upfront)
      // This can happen with Rectifier units when L1/L2/max_w2 are all set to 0, especially when using more than 1 hidden layer.
      final double thresh = 1e10;
      final double bthresh = 1e5;
      unstable |= isNaN(mean_bias[y]) || isNaN(rms_bias[y])
          || isNaN(mean_weight[y]) || isNaN(rms_weight[y])
          // large weights
          || Math.abs(mean_weight[y]) > thresh
          || rms_weight[y] > thresh
          // large biases
          || Math.abs(mean_bias[y]) > bthresh
          || rms_bias[y] > bthresh;
    }
  }

  /**
   * Unique identifier for this model's state, based on raw numbers
   */
  protected long checksum_impl() {
    computeStats();
    Random rng = new Random(0xDECAFBBB);
    double cs = Double.longBitsToDouble(get_params()._seed);
    cs += size() * get_processed_total();
    for (double d : mean_bias) cs += (rng.nextDouble() * (d+123.23));
    for (double d : rms_bias) cs += (rng.nextDouble() * (d+123.23));
    for (double d : mean_weight) cs += (rng.nextDouble() * (d+123.23));
    for (double d : rms_weight) cs += (rng.nextDouble() * (d+123.23));
    for (double d : mean_rate) cs += (rng.nextDouble() * (d+123.23));
    for (double d : rms_rate) cs += (rng.nextDouble() * (d+123.23));
    return Double.doubleToRawLongBits(cs);
  }

  /**
   * TimeAveraging as part of Elastic Averaging Algorithm
   * Cf. equation 6 of arXiv:1412.6651v5
   * @param nodeAverageModel current average of per-node models
   * @return Time-average of node-averages (consensus model, "the" model)
   */
  public static DeepLearningModelInfo timeAverage(DeepLearningModelInfo nodeAverageModel) {
    float pa = (float) nodeAverageModel.get_params()._elastic_averaging_moving_rate;
    assert(pa > 0 && pa <= 1);
    DeepLearningModelInfo elasticAverage = DKV.getGet(nodeAverageModel.elasticAverageModelInfoKey()); //get latest version from DKV
    if (elasticAverage == null || pa == 1) {
      elasticAverage = nodeAverageModel.deep_clone();
    } else {
      nodeAverageModel.mult(pa);
      elasticAverage.mult(1 - pa);
      elasticAverage.add(nodeAverageModel); //ignore processed local value set here
      elasticAverage.set_processed_global(nodeAverageModel.get_processed_global());
    }
    elasticAverage.set_processed_local(0);
    DKV.put(elasticAverage.elasticAverageModelInfoKey(), elasticAverage);

//    nodeAverageModel.computeStats();
//    elasticAverage.computeStats();
//    Log.info("Local Model    :\n" + nodeAverageModel.toString());
//    Log.info("Elastic Average:\n" + elasticAverage.toString());
    return elasticAverage;
  }

  public Key localModelInfoKey(H2ONode node) {
    return Key.make(_model_id + ".node" + node.index(), (byte) 1 /*replica factor*/, (byte) 31 /*hidden user-key*/, true, node);
  }

  public Key elasticAverageModelInfoKey() {
    return Key.make(_model_id + ".elasticaverage", (byte) 1 /*replica factor*/, (byte) 31 /*hidden user-key*/, true, H2O.CLOUD._memary[0]);
  }

  static public class GradientCheck {
    GradientCheck(int l, int r, int c) { layer=l; row=r; col=c; gradient=0;}
    int layer;
    int row;
    int col;
    double gradient;
    void apply(int l, int r, int c, double g) {
      if (r==row && c==col && l==layer) {
        gradient += g;
      }
    }
  }
  static public GradientCheck gradientCheck = null;
  static public GradientCheck gradientCheckBias = null;
}
