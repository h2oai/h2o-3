package hex.deeplearning;

import hex.*;
import hex.FrameTask.DataInfo;
import hex.schemas.DeepLearningModelV2;
import water.*;
import water.api.ModelSchema;
import water.api.ValidationAdapter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.Arrays;
import java.util.Random;

import static java.lang.Double.isNaN;

/**
 * The Deep Learning model
 * It contains a DeepLearningModelInfo with the most up-to-date model,
 * a scoring history, as well as some helpers to indicate the progress
 */

public class DeepLearningModel extends SupervisedModel<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningOutput> {

  public static class DeepLearningParameters extends Model.Parameters<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningOutput> {
    public boolean classification;
    public int n_folds;
    public boolean keep_cross_validation_splits;

    /**
     * A model key associated with a previously trained Deep Learning
     * model. This option allows users to build a new model as a
     * continuation of a previously generated model (e.g., by a grid search).
     */
    public Key checkpoint;

    /**
     * If enabled, store the best model under the destination key of this model at the end of training.
     * Only applicable if training is not cancelled.
     */
    public boolean override_with_best_model = true;

    /**
     * Unlock expert mode parameters than can affect model building speed,
     * predictive accuracy and scoring. Leaving expert mode parameters at default
     * values is fine for many problems, but best results on complex datasets are often
     * only attainable via expert mode options.
     */
    public boolean expert_mode = false;

    public boolean autoencoder = false;

    public boolean use_all_factor_levels = true;

  /*Neural Net Topology*/
    /**
     * The activation function (non-linearity) to be used the neurons in the hidden layers.
     * Tanh: Hyperbolic tangent function (same as scaled and shifted sigmoid).
     * Rectifier: Chooses the maximum of (0, x) where x is the input value.
     * Maxout: Choose the maximum coordinate of the input vector.
     * With Dropout: Zero out a random user-given fraction of the
     *      incoming weights to each hidden layer during training, for each
     *      training row. This effectively trains exponentially many models at
     *      once, and can improve generalization.
     */
    public Activation activation = Activation.Rectifier;

    /**
     * The number and size of each hidden layer in the model.
     * For example, if a user specifies "100,200,100" a model with 3 hidden
     * layers will be produced, and the middle hidden layer will have 200
     * neurons.To specify a grid search, add parentheses around each
     * model's specification: "(100,100), (50,50,50), (20,20,20,20)".
     */
    public int[] hidden = new int[] { 200, 200 };

    /**
     * The number of passes over the training dataset to be carried out.
     * It is recommended to start with lower values for initial grid searches.
     * This value can be modified during checkpoint restarts and allows continuation
     * of selected models.
     */
    public double epochs = 10;

    /**
     * The number of training data rows to be processed per iteration. Note that
     * independent of this parameter, each row is used immediately to update the model
     * with (online) stochastic gradient descent. This parameter controls the
     * synchronization period between nodes in a distributed environment and the
     * frequency at which scoring and model cancellation can happen. For example, if
     * it is set to 10,000 on H2O running on 4 nodes, then each node will
     * process 2,500 rows per iteration, sampling randomly from their local data.
     * Then, model averaging between the nodes takes place, and scoring can happen
     * (dependent on scoring interval and duty factor). Special values are 0 for
     * one epoch per iteration, -1 for processing the maximum amount of data
     * per iteration (if **replicate training data** is enabled, N epochs
     * will be trained per iteration on N nodes, otherwise one epoch). Special value
     * of -2 turns on automatic mode (auto-tuning).
     */
    public long train_samples_per_iteration = -2;

    public double target_ratio_comm_to_comp = 0.02;

    /**
     * The random seed controls sampling and initialization. Reproducible
     * results are only expected with single-threaded operation (i.e.,
     * when running on one node, turning off load balancing and providing
     * a small dataset that fits in one chunk).  In general, the
     * multi-threaded asynchronous updates to the model parameters will
     * result in (intentional) race conditions and non-reproducible
     * results. Note that deterministic sampling and initialization might
     * still lead to some weak sense of determinism in the model.
     */
    public long seed = new Random().nextLong();

  /*Adaptive Learning Rate*/
    /**
     * The implemented adaptive learning rate algorithm (ADADELTA) automatically
     * combines the benefits of learning rate annealing and momentum
     * training to avoid slow convergence. Specification of only two
     * parameters (rho and epsilon)  simplifies hyper parameter search.
     * In some cases, manually controlled (non-adaptive) learning rate and
     * momentum specifications can lead to better results, but require the
     * specification (and hyper parameter search) of up to 7 parameters.
     * If the model is built on a topology with many local minima or
     * long plateaus, it is possible for a constant learning rate to produce
     * sub-optimal results. Learning rate annealing allows digging deeper into
     * local minima, while rate decay allows specification of different
     * learning rates per layer.  When the gradient is being estimated in
     * a long valley in the optimization landscape, a large learning rate
     * can cause the gradient to oscillate and move in the wrong
     * direction. When the gradient is computed on a relatively flat
     * surface with small learning rates, the model can converge far
     * slower than necessary.
     */
    public boolean adaptive_rate = true;

    /**
     * The first of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to momentum and relates to the memory to prior weight updates.
     * Typical values are between 0.9 and 0.999.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    public double rho = 0.99;

    /**
     * The second of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to learning rate annealing during initial training
     * and momentum at later stages where it allows forward progress.
     * Typical values are between 1e-10 and 1e-4.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    public double epsilon = 1e-8;

  /*Learning Rate*/
    /**
     * When adaptive learning rate is disabled, the magnitude of the weight
     * updates are determined by the user specified learning rate
     * (potentially annealed), and are a function  of the difference
     * between the predicted value and the target value. That difference,
     * generally called delta, is only available at the output layer. To
     * correct the output at each hidden layer, back propagation is
     * used. Momentum modifies back propagation by allowing prior
     * iterations to influence the current update. Using the momentum
     * parameter can aid in avoiding local minima and the associated
     * instability. Too much momentum can lead to instabilities, that's
     * why the momentum is best ramped up slowly.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double rate = .005;

    /**
     * Learning rate annealing reduces the learning rate to "freeze" into
     * local minima in the optimization landscape.  The annealing rate is the
     * inverse of the number of training samples it takes to cut the learning rate in half
     * (e.g., 1e-6 means that it takes 1e6 training samples to halve the learning rate).
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double rate_annealing = 1e-6;

    /**
     * The learning rate decay parameter controls the change of learning rate across layers.
     * For example, assume the rate parameter is set to 0.01, and the rate_decay parameter is set to 0.5.
     * Then the learning rate for the weights connecting the input and first hidden layer will be 0.01,
     * the learning rate for the weights connecting the first and the second hidden layer will be 0.005,
     * and the learning rate for the weights connecting the second and third hidden layer will be 0.0025, etc.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double rate_decay = 1.0;

  /*Momentum*/
    /**
     * The momentum_start parameter controls the amount of momentum at the beginning of training.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double momentum_start = 0;

    /**
     * The momentum_ramp parameter controls the amount of learning for which momentum increases
     * (assuming momentum_stable is larger than momentum_start). The ramp is measured in the number
     * of training samples.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double momentum_ramp = 1e6;

    /**
     * The momentum_stable parameter controls the final momentum value reached after momentum_ramp training samples.
     * The momentum used for training will remain the same for training beyond reaching that point.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double momentum_stable = 0;

    /**
     * The Nesterov accelerated gradient descent method is a modification to
     * traditional gradient descent for convex functions. The method relies on
     * gradient information at various points to build a polynomial approximation that
     * minimizes the residuals in fewer iterations of the descent.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public boolean nesterov_accelerated_gradient = true;

  /*Regularization*/
    /**
     * A fraction of the features for each training row to be omitted from training in order
     * to improve generalization (dimension sampling).
     */
    public double input_dropout_ratio = 0.0;

    /**
     * A fraction of the inputs for each hidden layer to be omitted from training in order
     * to improve generalization. Defaults to 0.5 for each hidden layer if omitted.
     */
    public double[] hidden_dropout_ratios;

    /**
     * A regularization method that constrains the absolute value of the weights and
     * has the net effect of dropping some weights (setting them to zero) from a model
     * to reduce complexity and avoid overfitting.
     */
    public double l1 = 0.0;

    /**
     *  A regularization method that constrdains the sum of the squared
     * weights. This method introduces bias into parameter estimates, but
     * frequently produces substantial gains in modeling as estimate variance is
     * reduced.
     */
    public double l2 = 0.0;

    /**
     *  A maximum on the sum of the squared incoming weights into
     * any one neuron. This tuning parameter is especially useful for unbound
     * activation functions such as Maxout or Rectifier.
     */
    public float max_w2 = Float.POSITIVE_INFINITY;

  /*Initialization*/
    /**
     * The distribution from which initial weights are to be drawn. The default
     * option is an optimized initialization that considers the size of the network.
     * The "uniform" option uses a uniform distribution with a mean of 0 and a given
     * interval. The "normal" option draws weights from the standard normal
     * distribution with a mean of 0 and given standard deviation.
     */
    public InitialWeightDistribution initial_weight_distribution = InitialWeightDistribution.UniformAdaptive;

    /**
     * The scale of the distribution function for Uniform or Normal distributions.
     * For Uniform, the values are drawn uniformly from -initial_weight_scale...initial_weight_scale.
     * For Normal, the values are drawn from a Normal distribution with a standard deviation of initial_weight_scale.
     */
    public double initial_weight_scale = 1.0;

    /**
     * The loss (error) function to be minimized by the model.
     * Cross Entropy loss is used when the model output consists of independent
     * hypotheses, and the outputs can be interpreted as the probability that each
     * hypothesis is true. Cross entropy is the recommended loss function when the
     * target values are class labels, and especially for imbalanced data.
     * It strongly penalizes error in the prediction of the actual class label.
     * Mean Square loss is used when the model output are continuous real values, but can
     * be used for classification as well (where it emphasizes the error on all
     * output classes, not just for the actual class).
     */
    public Loss loss = Loss.Automatic;

  /*Scoring*/
    /**
     * The minimum time (in seconds) to elapse between model scoring. The actual
     * interval is determined by the number of training samples per iteration and the scoring duty cycle.
     */
    public double score_interval = 5;

    /**
     * The number of training dataset points to be used for scoring. Will be
     * randomly sampled. Use 0 for selecting the entire training dataset.
     */
    public long score_training_samples = 10000l;

    /**
     * The number of validation dataset points to be used for scoring. Can be
     * randomly sampled or stratified (if "balance classes" is set and "score
     * validation sampling" is set to stratify). Use 0 for selecting the entire
     * training dataset.
     */
    public long score_validation_samples = 0l;

    /**
     * Maximum fraction of wall clock time spent on model scoring on training and validation samples,
     * and on diagnostics such as computation of feature importances (i.e., not on training).
     */
    public double score_duty_cycle = 0.1;

    /**
     * The stopping criteria in terms of classification error (1-accuracy) on the
     * training data scoring dataset. When the error is at or below this threshold,
     * training stops.
     */
    public double classification_stop = 0;

    /**
     * The stopping criteria in terms of regression error (MSE) on the training
     * data scoring dataset. When the error is at or below this threshold, training
     * stops.
     */
    public double regression_stop = 1e-6;

    /**
     * Enable quiet mode for less output to standard output.
     */
    public boolean quiet_mode = false;

    /**
     * For classification models, the maximum size (in terms of classes) of the
     * confusion matrix for it to be printed. This option is meant to avoid printing
     * extremely large confusion matrices.
     */
    public int max_confusion_matrix_size = 20;

    /**
     * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
     */
    public int max_hit_ratio_k = 10;

  /*Imbalanced Classes*/
    /**
     * For imbalanced data, balance training data class counts via
     * over/under-sampling. This can result in improved predictive accuracy.
     */
    public boolean balance_classes = false;


    /**
     * Desired over/under-sampling ratios per class (lexicographic order).
     * Only when balance_classes is enabled.
     * If not specified, they will be automatically computed to obtain class balance during training.
     */
    public float[] class_sampling_factors;

    /**
     * When classes are balanced, limit the resulting dataset size to the
     * specified multiple of the original dataset size.
     */
    public float max_after_balance_size = 5.0f;

    /**
     * Method used to sample the validation dataset for scoring, see Score Validation Samples above.
     */
    public ClassSamplingMethod score_validation_sampling = ClassSamplingMethod.Uniform;

  /*Misc*/
    /**
     * Gather diagnostics for hidden layers, such as mean and RMS values of learning
     * rate, momentum, weights and biases.
     */
    public boolean diagnostics = true;

    /**
     * Whether to compute variable importances for input features.
     * The implemented method (by Gedeon) considers the weights connecting the
     * input features to the first two hidden layers.
     */
    public boolean variable_importances = false;

    /**
     * Enable fast mode (minor approximation in back-propagation), should not affect results significantly.
     */
    public boolean fast_mode = true;

    /**
     * Ignore constant training columns (no information can be gained anyway).
     */
    public boolean ignore_const_cols = true;

    /**
     * Increase training speed on small datasets by splitting it into many chunks
     * to allow utilization of all cores.
     */
    public boolean force_load_balance = true;

    /**
     * Replicate the entire training dataset onto every node for faster training on small datasets.
     */
    public boolean replicate_training_data = true;

    /**
     * Run on a single node for fine-tuning of model parameters. Can be useful for
     * checkpoint resumes after training on multiple nodes for fast initial
     * convergence.
     */
    public boolean single_node_mode = false;

    /**
     * Enable shuffling of training data (on each node). This option is
     * recommended if training data is replicated on N nodes, and the number of training samples per iteration
     * is close to N times the dataset size, where all nodes train will (almost) all
     * the data. It is automatically enabled if the number of training samples per iteration is set to -1 (or to N
     * times the dataset size or larger).
     */
    public boolean shuffle_training_data = false;

    public MissingValuesHandling missing_values_handling = MissingValuesHandling.MeanImputation;

    public boolean sparse = false;

    public boolean col_major = false;

    public double average_activation = 0;

    public double sparsity_beta = 0;

    public enum MissingValuesHandling {
      Skip, MeanImputation
    }

    public enum ClassSamplingMethod {
      Uniform, Stratified
    }

    public enum InitialWeightDistribution {
      UniformAdaptive, Uniform, Normal
    }

    /**
     * Activation functions
     */
    public enum Activation {
      Tanh, TanhWithDropout, Rectifier, RectifierWithDropout, Maxout, MaxoutWithDropout
    }

    /**
     * Loss functions
     * CrossEntropy is recommended
     */
    public enum Loss {
      Automatic, MeanSquare, CrossEntropy
    }


    //Sanity check for Deep Learning job parameters
    public void sanityCheck() {
      Frame fr = sanityCheckFrameKey(_training_frame,"Training Frame");
      if( _validation_frame != null ) sanityCheckFrameKey(_validation_frame,"Validation Frame");
      if (fr.numCols() <= 1)
        throw new IllegalArgumentException("Training data must have at least 2 features (incl. response).");

      if (hidden == null) throw new IllegalArgumentException("There must be at least one hidden layer.");

      for (int i=0;i<hidden.length;++i) {
        if (hidden[i]==0)
          throw new IllegalArgumentException("Hidden layer size must be >0.");
      }

      //Auto-fill defaults
      if (hidden_dropout_ratios == null) {
        if (activation == Activation.TanhWithDropout || activation == Activation.MaxoutWithDropout || activation == Activation.RectifierWithDropout) {
          hidden_dropout_ratios = new double[hidden.length];
          if (!quiet_mode) Log.info("Automatically setting all hidden dropout ratios to 0.5.");
          Arrays.fill(hidden_dropout_ratios, 0.5);
        }
      }
      else if (hidden_dropout_ratios.length != hidden.length) throw new IllegalArgumentException("Must have " + hidden.length + " hidden layer dropout ratios.");
      else if (activation != Activation.TanhWithDropout && activation != Activation.MaxoutWithDropout && activation != Activation.RectifierWithDropout) {
        if (!quiet_mode) Log.info("Ignoring hidden_dropout_ratios because a non-Dropout activation function was specified.");
      }
      if (input_dropout_ratio < 0 || input_dropout_ratio >= 1) {
        throw new IllegalArgumentException("Input dropout must be in [0,1).");
      }

      if (classification) {
        if (response_column == null)
          throw new IllegalArgumentException("Response column must be specified.");
        if (null == fr.vec(response_column))
          throw new IllegalArgumentException("Response column " + response_column + " not found in frame: " + _training_frame + ".");
      }

      if (null != ignored_columns)
        for (String ignored_column : ignored_columns)
          if (null == fr.vec(ignored_column))
            throw new IllegalArgumentException("Ignored column " + ignored_column + " not found in frame: " + _training_frame + ".");

      if (null != response_column && fr.vec(response_column).isEnum() && !classification) {
        Log.info("Automatically switching to classification for enum response_vec.");
        classification = true;
      }
      if (H2O.CLOUD.size() == 1 && replicate_training_data) {
        Log.info("Disabling replicate_training_data on 1 node.");
        replicate_training_data = false;
      }
      if (single_node_mode && (H2O.CLOUD.size() == 1 || !replicate_training_data)) {
        Log.info("Disabling single_node_mode (only for multi-node operation with replicated training data).");
        single_node_mode = false;
      }
      if (!use_all_factor_levels && autoencoder ) {
        Log.info("Enabling all_factor_levels for auto-encoders.");
        use_all_factor_levels = true;
      }
      if(override_with_best_model && n_folds != 0) {
        Log.info("Disabling override_with_best_model in combination with n-fold cross-validation.");
        override_with_best_model = false;
      }

      if (!quiet_mode) {
        if (adaptive_rate) {
          Log.info("Using automatic learning rate.  Ignoring the following input parameters:");
          Log.info("  rate, rate_decay, rate_annealing, momentum_start, momentum_ramp, momentum_stable, nesterov_accelerated_gradient.");
          momentum_start = 0;
          momentum_stable = 0;
        } else {
          Log.info("Using manual learning rate.  Ignoring the following input parameters:");
          Log.info("  rho, epsilon.");
          rho = 0;
          epsilon = 0;
        }

        if (initial_weight_distribution == InitialWeightDistribution.UniformAdaptive) {
          Log.info("Ignoring initial_weight_scale for UniformAdaptive weight distribution.");
        }
        if (n_folds != 0) {
          if (override_with_best_model) {
            Log.info("Automatically setting override_with_best_model to false, since the final model is the only scored model with n-fold cross-validation.");
            override_with_best_model = false;
          }
        }
      }

      if(loss == Loss.Automatic) {
        if (!classification) {
          if (!quiet_mode) Log.info("Automatically setting loss to MeanSquare for regression.");
          loss = Loss.MeanSquare;
        }
        else if (autoencoder) {
          if (!quiet_mode) Log.info("Automatically setting loss to MeanSquare for auto-encoder.");
          loss = Loss.MeanSquare;
        }
        else {
          if (!quiet_mode) Log.info("Automatically setting loss to Cross-Entropy for classification.");
          loss = Loss.CrossEntropy;
        }
      }

      if(autoencoder && sparsity_beta > 0) {
        if (activation == Activation.Tanh || activation == Activation.TanhWithDropout) {
          if (average_activation >= 1 || average_activation <= -1)
            throw new IllegalArgumentException("Tanh average activation must be in (-1,1).");
        }
        else if (activation == Activation.Rectifier || activation == Activation.RectifierWithDropout) {
          if (average_activation <= 0)
            throw new IllegalArgumentException("Rectifier average activation must be positive.");
        }
      }

      if (!classification && loss == Loss.CrossEntropy) throw new IllegalArgumentException("Cannot use CrossEntropy loss function for regression.");
      if (autoencoder && loss != Loss.MeanSquare) throw new IllegalArgumentException("Must use MeanSquare loss function for auto-encoder.");
      if (autoencoder && classification) { classification = false; Log.info("Using regression mode for auto-encoder.");}

      // reason for the error message below is that validation might not have the same horizontalized features as the training data (or different order)
      if (autoencoder && _validation_frame != null) throw new UnsupportedOperationException("Cannot specify a validation dataset for auto-encoder.");
      if (autoencoder && activation == Activation.Maxout) throw new UnsupportedOperationException("Maxout activation is not supported for auto-encoder.");

      if (!sparse && col_major) {
        if (!quiet_mode) throw new IllegalArgumentException("Cannot use column major storage for non-sparse data handling.");
      }
      if (!classification && balance_classes) {
        throw new IllegalArgumentException("balance_classes requires classification to be enabled.");
      }
      if (class_sampling_factors != null && !balance_classes) {
        throw new IllegalArgumentException("class_sampling_factors requires balance_classes to be enabled.");
      }
    }

  }

  public static class DeepLearningOutput extends Model.Output<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningOutput> {
    //FIXME
    //add output fields
  }

  @Override protected String errStr() {
    return "Locking of DeepLearningModel failed.";
  }

  // Default publically visible Schema is V2
  public ModelSchema schema() { return new DeepLearningModelV2(); }

  private volatile DeepLearningModelInfo model_info;
  void set_model_info(DeepLearningModelInfo mi) { model_info = mi; }
  final public DeepLearningModelInfo model_info() { return model_info; }

  private long run_time;
  final private long start_time;

  public long actual_train_samples_per_iteration;
  public double time_for_communication_us; //helper for auto-tuning: time in microseconds for collective bcast/reduce of the model

  public double epoch_counter;

  public long training_rows;

  public long validation_rows;

  private Errors[] errors;
  public Errors[] scoring_history() { return errors; }

  // Keep the best model so far, based on a single criterion (overall class. error or MSE)
  private float _bestError = Float.MAX_VALUE;

  public Key actual_best_model_key;

  // return the most up-to-date model metrics
  Errors last_scored() { return errors == null ? null : errors[errors.length-1]; }

//  @Override
  public final DeepLearningParameters get_params() { return _parms; }
//  @Override public final Request2 job() { return get_params(); }

  protected double missingColumnsType() { return get_params().sparse ? 0 : Double.NaN; }

  public float error() { return (float) (_output.isClassifier() ? cm().err() : mse()); }

  public int compareTo(DeepLearningModel o) {
    if (o._output.isClassifier() != _output.isClassifier()) throw new UnsupportedOperationException("Cannot compare classifier against regressor.");
    if (o._output.nclasses() != _output.nclasses()) throw new UnsupportedOperationException("Cannot compare models with different number of classes.");
    return (error() < o.error() ? -1 : error() > o.error() ? 1 : 0);
  }

  public static class Errors extends Iced {
//    static final int API_WEAVER = 1;
//    static public DocGen.FieldDoc[] DOC_FIELDS;

    public double epoch_counter;
    public long training_samples;
    public long training_time_ms;

    //training/validation sets
    boolean validation;
    int num_folds;
    public long score_training_samples;
    public long score_validation_samples;

    public boolean classification;

    VarImp variable_importances;

    // classification
    public ConfusionMatrix train_confusion_matrix;
    public ConfusionMatrix valid_confusion_matrix;
    public double train_err = 1;
    public double valid_err = 1;
    public AUCData trainAUC;
    public AUCData validAUC;
    public HitRatio train_hitratio; // "Hit ratio on training data"
    public HitRatio valid_hitratio; // "Hit ratio on validation data"

    // regression
    public double train_mse = Double.POSITIVE_INFINITY;
    public double valid_mse = Double.POSITIVE_INFINITY;

    public long scoring_time;

    Errors deep_clone() {
      AutoBuffer ab = new AutoBuffer();
      this.write(ab);
      ab.flipForReading();
      return (Errors) new Errors().read(ab);
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      if (classification) {
        sb.append("Error on training data (misclassification)"
                + (trainAUC != null ? " [using threshold for " + trainAUC.threshold_criterion.toString().replace("_"," ") +"]: ": ": ")
                + String.format("%.2f", 100*train_err) + "%");

        if (trainAUC != null) sb.append(", AUC on training data: " + String.format("%.4f", 100*trainAUC.AUC) + "%");
        if (validation || num_folds>0)
          sb.append("\nError on " + (num_folds>0 ? num_folds + "-fold cross-":"")+ "validation data (misclassification)"
                + (validAUC != null ? " [using threshold for " + validAUC.threshold_criterion.toString().replace("_"," ") +"]: ": ": ")
                + String.format("%.2f", (100*valid_err)) + "%");
        if (validAUC != null) sb.append(", AUC on validation data: " + String.format("%.4f", 100*validAUC.AUC) + "%");
      } else if (!Double.isInfinite(train_mse)) {
        sb.append("Error on training data (MSE): " + train_mse);
        if (validation || num_folds>0)
          sb.append("\nError on "+ (num_folds>0 ? num_folds + "-fold cross-":"")+ "validation data (MSE): " + valid_mse);
      }
      return sb.toString();
    }
  }

  final private static class ConfMat extends ConfusionMatrix2 {
    final private double _err;
    final private double _f1;
    public ConfMat(double err, double f1) {
      super(null);
      _err=err;
      _f1=f1;
    }
    @Override public double err() { return _err; }
    @Override public double F1() { return _f1; }
    @Override public double[] classErr() { return null; }
  }

  /** for grid search error reporting */
//  @Override
  public ConfusionMatrix2 cm() {
    final Errors lasterror = last_scored();
    if (lasterror == null) return null;
    ConfusionMatrix cm = lasterror.validation || lasterror.num_folds > 0 ?
            lasterror.valid_confusion_matrix :
            lasterror.train_confusion_matrix;
    if (cm == null || cm.cm == null) {
      if (lasterror.validation || lasterror.num_folds > 0) {
        return new ConfMat(lasterror.valid_err, lasterror.validAUC != null ? lasterror.validAUC.F1() : 0);
      } else {
        return new ConfMat(lasterror.train_err, lasterror.trainAUC != null ? lasterror.trainAUC.F1() : 0);
      }
    }
    // cm.cm has NaN padding, reduce it to N-1 size
    return new ConfusionMatrix2(cm.cm, cm.cm.length-1);
  }

//  @Override
  public double mse() {
    if (errors == null) return Double.NaN;
    return last_scored().validation || last_scored().num_folds > 0 ? last_scored().valid_mse : last_scored().train_mse;
  }

//  @Override
  public VarImp varimp() {
    if (errors == null) return null;
    return last_scored().variable_importances;
  }

  // This describes the model, together with the parameters
  // This will be shared: one per node
  public static class DeepLearningModelInfo extends Iced {
//    static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
//    static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.

    private DataInfo data_info;
    public DataInfo data_info() { return data_info; }

    // model is described by parameters and the following arrays
    private Neurons.DenseRowMatrix[] dense_row_weights; //one 2D weight matrix per layer (stored as a 1D array each)
    private Neurons.DenseColMatrix[] dense_col_weights; //one 2D weight matrix per layer (stored as a 1D array each)
    private Neurons.DenseVector[] biases; //one 1D bias array per layer
    private Neurons.DenseVector[] avg_activations; //one 1D array per hidden layer

    // helpers for storing previous step deltas
    // Note: These two arrays *could* be made transient and then initialized freshly in makeNeurons() and in DeepLearningTask.initLocal()
    // But then, after each reduction, the weights would be lost and would have to restart afresh -> not *exactly* right, but close...
    private Neurons.DenseRowMatrix[] dense_row_weights_momenta;
    private Neurons.DenseColMatrix[] dense_col_weights_momenta;
    private Neurons.DenseVector[] biases_momenta;

    // helpers for AdaDelta
    private Neurons.DenseRowMatrix[] dense_row_ada_dx_g;
    private Neurons.DenseColMatrix[] dense_col_ada_dx_g;
    private Neurons.DenseVector[] biases_ada_dx_g;

    // compute model size (number of model parameters required for making predictions)
    // momenta are not counted here, but they are needed for model building
    public long size() {
      long siz = 0;
      for (Neurons.Matrix w : dense_row_weights) if (w != null) siz += w.size();
      for (Neurons.Matrix w : dense_col_weights) if (w != null) siz += w.size();
      for (Neurons.Vector b : biases) siz += b.size();
      return siz;
    }

    // accessors to (shared) weights and biases - those will be updated racily (c.f. Hogwild!)
    boolean has_momenta() { return get_params().momentum_start != 0 || get_params().momentum_stable != 0; }
    boolean adaDelta() { return get_params().adaptive_rate; }
    public final Neurons.Matrix get_weights(int i) { return dense_row_weights[i] == null ? dense_col_weights[i] : dense_row_weights[i]; }
    public final Neurons.DenseVector get_biases(int i) { return biases[i]; }
    public final Neurons.Matrix get_weights_momenta(int i) { return dense_row_weights_momenta[i] == null ? dense_col_weights_momenta[i] : dense_row_weights_momenta[i]; }
    public final Neurons.DenseVector get_biases_momenta(int i) { return biases_momenta[i]; }
    public final Neurons.Matrix get_ada_dx_g(int i) { return dense_row_ada_dx_g[i] == null ? dense_col_ada_dx_g[i] : dense_row_ada_dx_g[i]; }
    public final Neurons.DenseVector get_biases_ada_dx_g(int i) { return biases_ada_dx_g[i]; }
    //accessor to shared parameter defining avg activations
    public final Neurons.DenseVector get_avg_activations(int i) { return avg_activations[i]; }

    private DeepLearningParameters parameters;
    public final DeepLearningParameters get_params() { return parameters; }

    private float[] mean_rate;

    private float[] rms_rate;

    private float[] mean_bias;

    private float[] rms_bias;

    private float[] mean_weight;

    public float[] rms_weight;

    public float[] mean_a;

    private volatile boolean unstable = false;
    public boolean unstable() { return unstable; }
    public void set_unstable() { if (!unstable) computeStats(); unstable = true; }

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

    public DeepLearningModelInfo() {}

    public DeepLearningModelInfo(final DeepLearningParameters params, final DataInfo dinfo) {
      data_info = dinfo;
      parameters = params;
      final int num_input = dinfo.fullN();
      final int num_output = get_params().autoencoder ? num_input : get_params().classification ? dinfo._adaptedFrame.domains()[dinfo._adaptedFrame.domains().length-1].length : 1;
      assert(num_input > 0);
      assert(num_output > 0);
      if (has_momenta() && adaDelta()) throw new IllegalArgumentException("Cannot have non-zero momentum and adaptive rate at the same time.");
      final int layers=get_params().hidden.length;
      // units (# neurons for each layer)
      units = new int[layers+2];
      units[0] = num_input;
      System.arraycopy(get_params().hidden, 0, units, 1, layers);
      units[layers+1] = num_output;
      // weights (to connect layers)
      dense_row_weights = new Neurons.DenseRowMatrix[layers+1];
      dense_col_weights = new Neurons.DenseColMatrix[layers+1];

      // decide format of weight matrices row-major or col-major
      if (get_params().col_major) dense_col_weights[0] = new Neurons.DenseColMatrix(units[1], units[0]);
      else dense_row_weights[0] = new Neurons.DenseRowMatrix(units[1], units[0]);
      for (int i = 1; i <= layers; ++i)
        dense_row_weights[i] = new Neurons.DenseRowMatrix(units[i + 1] /*rows*/, units[i] /*cols*/);

      // biases (only for hidden layers and output layer)
      biases = new Neurons.DenseVector[layers+1];
      for (int i=0; i<=layers; ++i) biases[i] = new Neurons.DenseVector(units[i+1]);
      // average activation (only for hidden layers)
      if (get_params().autoencoder && get_params().sparsity_beta > 0) {
        avg_activations = new Neurons.DenseVector[layers];
        mean_a = new float[layers];
        for (int i = 0; i < layers; ++i) avg_activations[i] = new Neurons.DenseVector(units[i + 1]);
      }
      fillHelpers();
      // for diagnostics
      mean_rate = new float[units.length];
      rms_rate = new float[units.length];
      mean_bias = new float[units.length];
      rms_bias = new float[units.length];
      mean_weight = new float[units.length];
      rms_weight = new float[units.length];
    }

    // deep clone all weights/biases
    DeepLearningModelInfo deep_clone() {
      AutoBuffer ab = new AutoBuffer();
      this.write(ab);
      ab.flipForReading();
      return (DeepLearningModelInfo) new DeepLearningModelInfo().read(ab);
    }

    void fillHelpers() {
      if (has_momenta()) {
        dense_row_weights_momenta = new Neurons.DenseRowMatrix[dense_row_weights.length];
        dense_col_weights_momenta = new Neurons.DenseColMatrix[dense_col_weights.length];
        if (dense_row_weights[0] != null)
          dense_row_weights_momenta[0] = new Neurons.DenseRowMatrix(units[1], units[0]);
        else
          dense_col_weights_momenta[0] = new Neurons.DenseColMatrix(units[1], units[0]);
        for (int i=1; i<dense_row_weights_momenta.length; ++i) dense_row_weights_momenta[i] = new Neurons.DenseRowMatrix(units[i+1], units[i]);

        biases_momenta = new Neurons.DenseVector[biases.length];
        for (int i=0; i<biases_momenta.length; ++i) biases_momenta[i] = new Neurons.DenseVector(units[i+1]);
      }
      else if (adaDelta()) {
        dense_row_ada_dx_g = new Neurons.DenseRowMatrix[dense_row_weights.length];
        dense_col_ada_dx_g = new Neurons.DenseColMatrix[dense_col_weights.length];
        //AdaGrad
        if (dense_row_weights[0] != null) {
          dense_row_ada_dx_g[0] = new Neurons.DenseRowMatrix(units[1], 2*units[0]);
        } else {
          dense_col_ada_dx_g[0] = new Neurons.DenseColMatrix(2*units[1], units[0]);
        }
        for (int i=1; i<dense_row_ada_dx_g.length; ++i) {
          dense_row_ada_dx_g[i] = new Neurons.DenseRowMatrix(units[i+1], 2*units[i]);
        }
        biases_ada_dx_g = new Neurons.DenseVector[biases.length];
        for (int i=0; i<biases_ada_dx_g.length; ++i) {
          biases_ada_dx_g[i] = new Neurons.DenseVector(2*units[i+1]);
        }
      }
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      if (get_params().diagnostics && !get_params().quiet_mode) {
        Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(this);

        sb.append("Number of hidden layers is " + get_params().hidden.length + " \n");

        if (get_params().sparsity_beta > 0) {
          for (int k = 0; k < get_params().hidden.length; k++)
            sb.append("Average activation in hidden layer " + k + " is  " + mean_a[k] + " \n");
        }

        sb.append("Status of Neuron Layers:\n");
        sb.append("#  Units         Type      Dropout    L1       L2    " + (get_params().adaptive_rate ? "  Rate (Mean,RMS)   " : "  Rate      Momentum") + "   Weight (Mean, RMS)      Bias (Mean,RMS)\n");
        final String format = "%7g";
        for (int i=0; i<neurons.length; ++i) {
          sb.append((i+1) + " " + String.format("%6d", neurons[i].units)
                  + " " + String.format("%16s", neurons[i].getClass().getSimpleName()));
          if (i == 0) {
            sb.append("  " + PrettyPrint.formatPct(neurons[i].params.input_dropout_ratio) + " \n");
            continue;
          }
          else if (i < neurons.length-1) {
            if (neurons[i].params.hidden_dropout_ratios == null)
              sb.append("  " + PrettyPrint.formatPct(0) + " ");
            else
              sb.append("  " + PrettyPrint.formatPct(neurons[i].params.hidden_dropout_ratios[i - 1]) + " ");
          } else {
            sb.append("          ");
          }
          sb.append(
                  " " + String.format("%5f", neurons[i].params.l1)
                          + " " + String.format("%5f", neurons[i].params.l2)
                          + " " + (get_params().adaptive_rate ? (" (" + String.format(format, mean_rate[i]) + ", " + String.format(format, rms_rate[i]) + ")" )
                          : (String.format("%10g", neurons[i].rate(get_processed_total())) + " " + String.format("%5f", neurons[i].momentum(get_processed_total()))))
                          + " (" + String.format(format, mean_weight[i])
                          + ", " + String.format(format, rms_weight[i]) + ")"
                          + " (" + String.format(format, mean_bias[i])
                          + ", " + String.format(format, rms_bias[i]) + ")\n");

          if (get_params().sparsity_beta > 0) {
            // sb.append("  " + String.format(format, mean_a[i]) + " \n");
          }
        }
      }
      return sb.toString();
    }

    // DEBUGGING
    public String toStringAll() {
      StringBuilder sb = new StringBuilder();
      sb.append(toString());

      for (int i=0; i<units.length-1; ++i)
        sb.append("\nweights["+i+"][]="+Arrays.toString(get_weights(i).raw()));
      for (int i=0; i<units.length-1; ++i)
        sb.append("\nbiases["+i+"][]="+Arrays.toString(get_biases(i).raw()));
      if (has_momenta()) {
        for (int i=0; i<units.length-1; ++i)
          sb.append("\nweights_momenta["+i+"][]="+Arrays.toString(get_weights_momenta(i).raw()));
      }
      if (biases_momenta != null) {
        for (int i=0; i<units.length-1; ++i)
          sb.append("\nbiases_momenta["+i+"][]="+Arrays.toString(biases_momenta[i].raw()));
      }
      sb.append("\nunits[]="+Arrays.toString(units));
      sb.append("\nprocessed global: "+get_processed_global());
      sb.append("\nprocessed local:  "+get_processed_local());
      sb.append("\nprocessed total:  " + get_processed_total());
      sb.append("\n");
      return sb.toString();
    }

    void initializeMembers() {
      randomizeWeights();
      //TODO: determine good/optimal/best initialization scheme for biases
      // hidden layers
      for (int i=0; i<get_params().hidden.length; ++i) {
        if (get_params().activation == DeepLearningParameters.Activation.Rectifier
                || get_params().activation == DeepLearningParameters.Activation.RectifierWithDropout
                || get_params().activation == DeepLearningParameters.Activation.Maxout
                || get_params().activation == DeepLearningParameters.Activation.MaxoutWithDropout
                ) {
//          Arrays.fill(biases[i], 1.); //old behavior
          Arrays.fill(biases[i].raw(), i == 0 ? 0.5f : 1f); //new behavior, might be slightly better
        }
        else if (get_params().activation == DeepLearningParameters.Activation.Tanh || get_params().activation == DeepLearningParameters.Activation.TanhWithDropout) {
          Arrays.fill(biases[i].raw(), 0f);
        }
      }
      Arrays.fill(biases[biases.length-1].raw(), 0f); //output layer
    }
    public void add(DeepLearningModelInfo other) {
      for (int i=0;i<dense_row_weights.length;++i)
        ArrayUtils.add(get_weights(i).raw(), other.get_weights(i).raw());
      for (int i=0;i<biases.length;++i) ArrayUtils.add(biases[i].raw(), other.biases[i].raw());
      if (avg_activations != null)
        for (int i=0;i<avg_activations.length;++i)
          ArrayUtils.add(avg_activations[i].raw(), other.biases[i].raw());
      if (has_momenta()) {
        assert(other.has_momenta());
        for (int i=0;i<dense_row_weights_momenta.length;++i)
          ArrayUtils.add(get_weights_momenta(i).raw(), other.get_weights_momenta(i).raw());
        for (int i=0;i<biases_momenta.length;++i)
          ArrayUtils.add(biases_momenta[i].raw(),  other.biases_momenta[i].raw());
      }
      if (adaDelta()) {
        assert(other.adaDelta());
        for (int i=0;i<dense_row_ada_dx_g.length;++i) {
          ArrayUtils.add(get_ada_dx_g(i).raw(), other.get_ada_dx_g(i).raw());
        }
      }
      add_processed_local(other.get_processed_local());
    }
    protected void div(float N) {
      for (int i=0; i<dense_row_weights.length; ++i)
        ArrayUtils.div(get_weights(i).raw(), N);
      for (Neurons.Vector bias : biases) ArrayUtils.div(bias.raw(), N);
      if (avg_activations != null)
        for (Neurons.Vector avgac : avg_activations)
          ArrayUtils.div(avgac.raw(), N);
      if (has_momenta()) {
        for (int i=0; i<dense_row_weights_momenta.length; ++i)
          ArrayUtils.div(get_weights_momenta(i).raw(), N);
        for (Neurons.Vector bias_momenta : biases_momenta) ArrayUtils.div(bias_momenta.raw(), N);
      }
      if (adaDelta()) {
        for (int i=0;i<dense_row_ada_dx_g.length;++i) {
          ArrayUtils.div(get_ada_dx_g(i).raw(), N);
        }
      }
    }
    double uniformDist(Random rand, double min, double max) {
      return min + rand.nextFloat() * (max - min);
    }
    void randomizeWeights() {
      for (int w=0; w<dense_row_weights.length; ++w) {
        final Random rng = water.util.RandomUtils.getDeterRNG(get_params().seed + 0xBAD5EED + w+1); //to match NeuralNet behavior
        final double range = Math.sqrt(6. / (units[w] + units[w+1]));
        for( int i = 0; i < get_weights(w).rows(); i++ ) {
          for( int j = 0; j < get_weights(w).cols(); j++ ) {
            if (get_params().initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.UniformAdaptive) {
              // cf. http://machinelearning.wustl.edu/mlpapers/paper_files/AISTATS2010_GlorotB10.pdf
              if (w==dense_row_weights.length-1 && get_params().classification)
                get_weights(w).set(i,j, (float)(4.*uniformDist(rng, -range, range))); //Softmax might need an extra factor 4, since it's like a sigmoid
              else
                get_weights(w).set(i,j, (float)uniformDist(rng, -range, range));
            }
            else if (get_params().initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.Uniform) {
              get_weights(w).set(i,j, (float)uniformDist(rng, -get_params().initial_weight_scale, get_params().initial_weight_scale));
            }
            else if (get_params().initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.Normal) {
              get_weights(w).set(i,j, (float)(rng.nextGaussian() * get_params().initial_weight_scale));
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
      for( int j = 0; j < units[1]; j++ ) {
        for( int i = 0; i < units[0]; i++ ) {
          float wij = get_weights(0).get(j, i);
          sum_wj[j] += Math.abs(wij);
        }
      }
      for( int k = 0; k < units[2]; k++ ) {
        for( int j = 0; j < units[1]; j++ ) {
          float wjk = get_weights(1).get(k,j);
          sum_wk[k] += Math.abs(wjk);
        }
      }
      // compute importance of input i on output k as product of connecting weights going through j
      for( int i = 0; i < units[0]; i++ ) {
        for( int k = 0; k < units[2]; k++ ) {
          for( int j = 0; j < units[1]; j++ ) {
            float wij = get_weights(0).get(j,i);
            float wjk = get_weights(1).get(k,j);
            //Qik[i][k] += Math.abs(wij)/sum_wj[j] * wjk; //Wong,Gedeon,Taggart '95
            Qik[i][k] += Math.abs(wij)/sum_wj[j] * Math.abs(wjk)/sum_wk[k]; //Gedeon '97
          }
        }
      }
      // normalize Qik over all outputs k
      for( int k = 0; k < units[2]; k++ ) {
        float sumQk = 0;
        for( int i = 0; i < units[0]; i++ ) sumQk += Qik[i][k];
        for( int i = 0; i < units[0]; i++ ) Qik[i][k] /= sumQk;
      }
      // importance for feature i is the sum over k of i->k importances
      for( int i = 0; i < units[0]; i++ ) vi[i] = ArrayUtils.sum(Qik[i]);

      //normalize importances such that max(vi) = 1
      ArrayUtils.div(vi, ArrayUtils.maxValue(vi));
      return vi;
    }

    // compute stats on all nodes
    public void computeStats() {
      float[][] rate = get_params().adaptive_rate ? new float[units.length-1][] : null;

      if (get_params().autoencoder && get_params().sparsity_beta > 0) {
        for (int k = 0; k < get_params().hidden.length; k++) {
          mean_a[k] = 0;
          for (int j = 0; j < avg_activations[k].size(); j++)
            mean_a[k] += avg_activations[k].get(j);
          mean_a[k] /= avg_activations[k].size();
        }
      }

      for( int y = 1; y < units.length; y++ ) {
        mean_rate[y] = rms_rate[y] = 0;
        mean_bias[y] = rms_bias[y] = 0;
        mean_weight[y] = rms_weight[y] = 0;
        for(int u = 0; u < biases[y-1].size(); u++) {
          mean_bias[y] += biases[y-1].get(u);
        }
        if (rate != null) rate[y-1] = new float[get_weights(y-1).raw().length];
        for(int u = 0; u < get_weights(y-1).raw().length; u++) {
          mean_weight[y] += get_weights(y-1).raw()[u];
          if (rate != null) {
//            final float RMS_dx = (float)Math.sqrt(ada[y-1][2*u]+(float)get_params().epsilon);
//            final float invRMS_g = (float)(1/Math.sqrt(ada[y-1][2*u+1]+(float)get_params().epsilon));
            final float RMS_dx = MathUtils.approxSqrt(get_ada_dx_g(y-1).raw()[2*u]+(float)get_params().epsilon);
            final float invRMS_g = MathUtils.approxInvSqrt(get_ada_dx_g(y-1).raw()[2*u+1]+(float)get_params().epsilon);
            rate[y-1][u] = RMS_dx*invRMS_g; //not exactly right, RMS_dx should be from the previous time step -> but close enough for diagnostics.
            mean_rate[y] += rate[y-1][u];
          }
        }


        mean_bias[y] /= biases[y-1].size();

        mean_weight[y] /= get_weights(y-1).size();
        if (rate != null) mean_rate[y] /= rate[y-1].length;

        for(int u = 0; u < biases[y-1].size(); u++) {
          final double db = biases[y-1].get(u) - mean_bias[y];
          rms_bias[y] += db * db;
        }
        for(int u = 0; u < get_weights(y-1).size(); u++) {
          final double dw = get_weights(y-1).raw()[u] - mean_weight[y];
          rms_weight[y] += dw * dw;
          if (rate != null) {
            final double drate = rate[y-1][u] - mean_rate[y];
            rms_rate[y] += drate * drate;
          }
        }
        rms_bias[y] = MathUtils.approxSqrt(rms_bias[y]/biases[y-1].size());
        rms_weight[y] = MathUtils.approxSqrt(rms_weight[y] / get_weights(y - 1).size());
        if (rate != null) rms_rate[y] = MathUtils.approxSqrt(rms_rate[y]/rate[y-1].length);
//        rms_bias[y] = (float)Math.sqrt(rms_bias[y]/biases[y-1].length);
//        rms_weight[y] = (float)Math.sqrt(rms_weight[y]/weights[y-1].length);
//        if (rate != null) rms_rate[y] = (float)Math.sqrt(rms_rate[y]/rate[y-1].length);

        // Abort the run if weights or biases are unreasonably large (Note that all input values are normalized upfront)
        // This can happen with Rectifier units when L1/L2/max_w2 are all set to 0, especially when using more than 1 hidden layer.
        final double thresh = 1e10;
        unstable |= mean_bias[y] > thresh  || isNaN(mean_bias[y])
                || rms_bias[y] > thresh    || isNaN(rms_bias[y])
                || mean_weight[y] > thresh || isNaN(mean_weight[y])
                || rms_weight[y] > thresh  || isNaN(rms_weight[y]);
      }
    }
  }

  /**
   * Helper to create a DataInfo object from the source and response
   * @return DataInfo object
   */
  public static DataInfo prepareDataInfo(DeepLearningParameters parms) {
    final Frame fr = parms._training_frame.get();
    final Frame train = FrameTask.DataInfo.prepareFrame(fr, parms.autoencoder ? null : fr.vec(parms.response_column), fr.indices(parms.ignored_columns), parms.classification, parms.ignore_const_cols, true /*drop >20% NA cols*/);
    final DataInfo dinfo = new FrameTask.DataInfo(train, parms.autoencoder ? 0 : 1, parms.autoencoder || parms.use_all_factor_levels, //use all FactorLevels for auto-encoder
            parms.autoencoder ? DataInfo.TransformType.NORMALIZE : DataInfo.TransformType.STANDARDIZE, //transform predictors
            parms.classification ? DataInfo.TransformType.NONE : DataInfo.TransformType.STANDARDIZE);
    if (!parms.autoencoder) {
      final Vec resp = dinfo._adaptedFrame.lastVec(); //convention from DataInfo: response is the last Vec
      assert (!parms.classification ^ resp.isEnum()) : "Must have enum response_vec for classification!"; //either regression or enum response
    }
    return dinfo;
  }

  /**
   * Constructor to restart from a checkpointed model
   * @param cp Checkpoint to restart from
   * @param destKey New destination key for the model
   * @param jobKey New job key (job which updates the model)
   */
  public DeepLearningModel(final DeepLearningModel cp, final Key destKey, final Key jobKey, final DataInfo dataInfo) {
    super(destKey, dataInfo._adaptedFrame.names(), dataInfo._adaptedFrame.domains(), cp._parms, new DeepLearningOutput(), cp._priorClassDist != null ? cp._priorClassDist.clone() : null);
    // _train = cp._dataKey;
    // _validationKey = cp._validationKey;
    final boolean store_best_model = (jobKey == null);
    if (store_best_model) {
      model_info = cp.model_info.deep_clone(); //don't want to interfere with model being built, just make a deep copy and store that
      model_info.data_info = dataInfo.deep_clone(); //replace previous data_info with updated version that's passed in (contains enum for classification)
      _modelClassDist = cp._modelClassDist != null ? cp._modelClassDist.clone() : null;
    } else {
      model_info = (DeepLearningModelInfo) cp.model_info.clone(); //shallow clone is ok (won't modify the Checkpoint in K-V store during checkpoint restart)
      model_info.data_info = dataInfo; //shallow clone is ok
      get_params().checkpoint = cp._key; //it's only a "real" checkpoint if job != null, otherwise a best model copy
    }
    actual_best_model_key = cp.actual_best_model_key;
    start_time = cp.start_time;
    run_time = cp.run_time;
    training_rows = cp.training_rows; //copy the value to display the right number on the model page before training has started
    validation_rows = cp.validation_rows; //copy the value to display the right number on the model page before training has started
    _bestError = cp._bestError;

    // deep clone scoring history
    errors = cp.errors.clone();
    for (int i=0; i<errors.length;++i)
      errors[i] = cp.errors[i].deep_clone();

    // set proper timing
    _timeLastScoreEnter = System.currentTimeMillis();
    _timeLastScoreStart = 0;
    _timeLastScoreEnd = 0;
    _timeLastPrintStart = 0;
    assert(Arrays.equals(_key._kb, destKey._kb));
  }

  public DeepLearningModel(final Key destKey, final Key jobKey, final Key dataKey, final DataInfo dinfo, final DeepLearningParameters params, final float[] priorDist) {
    super(destKey, /*dataKey, */dinfo._adaptedFrame, params, new DeepLearningOutput(), priorDist);
    run_time = 0;
    start_time = System.currentTimeMillis();
    _timeLastScoreEnter = start_time;
    model_info = new DeepLearningModelInfo(params, dinfo);
    actual_best_model_key = Key.makeUserHidden(Key.make());
    if (params.n_folds != 0) actual_best_model_key = null;
    if (!get_params().autoencoder) {
      errors = new Errors[1];
      errors[0] = new Errors();
      errors[0].validation = (params._validation_frame != null);
      errors[0].num_folds = params.n_folds;
    }
    assert(Arrays.equals(_key._kb, destKey._kb));
  }

  public long _timeLastScoreEnter; //not transient: needed for HTML display page
  transient private long _timeLastScoreStart;
  transient private long _timeLastScoreEnd;
  transient private long _timeLastPrintStart;
  /**
   *
   * @param train training data from which the model is built (for epoch counting only)
   * @param ftrain potentially downsampled training data for scoring
   * @param ftest  potentially downsampled validation data for scoring
   * @param job_key key of the owning job
   * @return true if model building is ongoing
   */
  boolean doScoring(Frame train, Frame ftrain, Frame ftest, Key job_key, ValidationAdapter.Response2CMAdaptor vadaptor) {
    boolean keep_running = true;
    try {
      final long now = System.currentTimeMillis();
      epoch_counter = (float)model_info().get_processed_total()/train.numRows();
      final double time_last_iter_millis = Math.max(1, now-_timeLastScoreEnter); //at least 1 msec

      // Auto-tuning
      // if multi-node and auto-tuning and at least 10 ms for communication (to avoid doing thins on multi-JVM on same node),
      // then adjust the auto-tuning parameter 'actual_train_samples_per_iteration' such that the targeted ratio of comm to comp is achieved
      // Note: actual communication time is estimated by the NetworkTest's collective test.
      if (H2O.CLOUD.size() > 1 && get_params().train_samples_per_iteration == -2 && time_for_communication_us > 1e4) {
//        Log.info("Time taken for communication: " + PrettyPrint.usecs((long)time_for_communication_us));
//        Log.info("Time taken for Map/Reduce iteration: " + PrettyPrint.msecs((long)time_last_iter_millis, true));
        final double comm_to_work_ratio = (time_for_communication_us *1e-3) / time_last_iter_millis;
//        Log.info("Ratio of network communication to computation: " + String.format("%.3f", comm_to_work_ratio));
//        Log.info("target_comm_to_work: " + get_params().target_ratio_comm_to_comp);
        final double correction = get_params().target_ratio_comm_to_comp / comm_to_work_ratio;
//        Log.warn("Suggested value for train_samples_per_iteration: " + get_params().actual_train_samples_per_iteration/correction);
        actual_train_samples_per_iteration /= correction;
        actual_train_samples_per_iteration = Math.max(1, actual_train_samples_per_iteration);
      }

      run_time += time_last_iter_millis;
      _timeLastScoreEnter = now;
      keep_running = (epoch_counter < get_params().epochs);
      final long sinceLastScore = now -_timeLastScoreStart;
      final long sinceLastPrint = now -_timeLastPrintStart;
      final long samples = model_info().get_processed_total();
      if (!keep_running || sinceLastPrint > get_params().score_interval*1000) {
        _timeLastPrintStart = now;
        Log.info("Training time: " + PrettyPrint.msecs(run_time, true)
                + ". Processed " + String.format("%,d", samples) + " samples" + " (" + String.format("%.3f", epoch_counter) + " epochs)."
                + " Speed: " + String.format("%.3f", 1000.*samples/run_time) + " samples/sec.");
      }

      // this is potentially slow - only do every so often
      if( !keep_running ||
              (sinceLastScore > get_params().score_interval*1000 //don't score too often
                      &&(double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < get_params().score_duty_cycle) ) { //duty cycle
        final boolean printme = !get_params().quiet_mode;
        final boolean adaptCM = (_output.isClassifier() && vadaptor.needsAdaptation2CM());
        _timeLastScoreStart = now;
        if (get_params().diagnostics) model_info().computeStats();
        Errors err = new Errors();
        err.training_time_ms = run_time;
        err.epoch_counter = epoch_counter;
        err.training_samples = model_info().get_processed_total();
        err.validation = ftest != null;
        err.score_training_samples = ftrain.numRows();

        if (get_params().autoencoder) {
          if (printme) Log.info("Scoring the auto-encoder.");
          // training
          {
            final Frame mse_frame = scoreAutoEncoder(ftrain);
            final Vec l2 = mse_frame.anyVec();
            Log.info("Mean reconstruction error on training data: " + l2.mean() + "\n");
            err.train_mse = l2.mean();
            mse_frame.delete();
          }
        } else {
          if (printme) Log.info("Scoring the model.");
          // compute errors
          err.classification = _output.isClassifier();
          assert (err.classification == get_params().classification);
          err.num_folds = get_params().n_folds;
          err.train_confusion_matrix = new ConfusionMatrix();
          final int hit_k = Math.min(_output.nclasses(), get_params().max_hit_ratio_k);
          if (err.classification && _output.nclasses() > 2 && hit_k > 0) {
            err.train_hitratio = new HitRatio();
            err.train_hitratio.set_max_k(hit_k);
          }
          final String m = model_info().toString();
          if (m.length() > 0) Log.info(m);
          final Frame trainPredict = score(ftrain, false);
          AUC trainAUC = null;
          if (err.classification && _output.nclasses() == 2) trainAUC = new AUC();
          final double trainErr = calcError(ftrain, ftrain.lastVec(), trainPredict, trainPredict, "training",
                  printme, get_params().max_confusion_matrix_size, err.train_confusion_matrix, trainAUC, err.train_hitratio);
          if (_output.isClassifier()) err.train_err = trainErr;
          if (trainAUC != null) err.trainAUC = trainAUC.data();
          else err.train_mse = trainErr;

          trainPredict.delete();

          if (err.validation) {
            assert ftest != null;
            err.score_validation_samples = ftest.numRows();
            err.valid_confusion_matrix = new ConfusionMatrix();
            if (err.classification && _output.nclasses() > 2 && hit_k > 0) {
              err.valid_hitratio = new HitRatio();
              err.valid_hitratio.set_max_k(hit_k);
            }
            final String adaptRespName = vadaptor.adaptedValidationResponse(_output.responseName());
            Vec adaptCMresp = null;
            if (adaptCM) {
              Vec[] v = ftest.vecs();
              assert (ftest.find(adaptRespName) == v.length - 1); //make sure to have (adapted) response in the test set
              adaptCMresp = ftest.remove(v.length - 1); //model would remove any extra columns anyway (need to keep it here for later)
            }

            final Frame validPredict = score(ftest, adaptCM);
            final Frame hitratio_validPredict = new Frame(validPredict);
            Vec orig_label = validPredict.vecs()[0];
            // Adapt output response domain, in case validation domain is different from training domain
            // Note: doesn't change predictions, just the *possible* label domain
            if (adaptCM) {
              assert (adaptCMresp != null);
              assert (ftest.find(adaptRespName) == -1);
              ftest.add(adaptRespName, adaptCMresp);
              final Vec CMadapted = vadaptor.adaptModelResponse2CM(validPredict.vecs()[0]);
              validPredict.replace(0, CMadapted); //replace label
              validPredict.add("to_be_deleted", CMadapted); //keep the Vec around to be deleted later (no leak)
            }
            AUC validAUC = null;
            if (err.classification && _output.nclasses() == 2) validAUC = new AUC();
            final double validErr = calcError(ftest, ftest.lastVec(), validPredict, hitratio_validPredict, "validation",
                    printme, get_params().max_confusion_matrix_size, err.valid_confusion_matrix, validAUC, err.valid_hitratio);
            if (_output.isClassifier()) err.valid_err = validErr;
            if (trainAUC != null) err.validAUC = validAUC.data();
            else err.valid_mse = validErr;
            validPredict.delete();
            //also delete the replaced label
            if (adaptCM) orig_label.remove(new Futures()).blockForPending();
          }

          if (get_params().variable_importances) {
            if (!get_params().quiet_mode) Log.info("Computing variable importances.");
            final float[] vi = model_info().computeVariableImportances();
            err.variable_importances = new VarImp(vi, Arrays.copyOfRange(model_info().data_info().coefNames(), 0, vi.length));
          }

          // only keep confusion matrices for the last step if there are fewer than specified number of output classes
          if (err.train_confusion_matrix.cm != null
                  && err.train_confusion_matrix.cm.length - 1 >= get_params().max_confusion_matrix_size) {
            err.train_confusion_matrix = null;
            err.valid_confusion_matrix = null;
          }
        }

        _timeLastScoreEnd = System.currentTimeMillis();
        err.scoring_time = System.currentTimeMillis() - now;
        // enlarge the error array by one, push latest score back
        if (errors == null) {
          errors = new Errors[]{err};
        } else {
          Errors[] err2 = new Errors[errors.length + 1];
          System.arraycopy(errors, 0, err2, 0, errors.length);
          err2[err2.length - 1] = err;
          errors = err2;
        }

        if (!get_params().autoencoder) {
          // always keep a copy of the best model so far (based on the following criterion)
          if (actual_best_model_key != null && (
                  // if we have a best_model in DKV, then compare against its error() (unless it's a different model as judged by the network size)
                  (DKV.get(actual_best_model_key) != null && (error() < DKV.get(actual_best_model_key).<DeepLearningModel>get().error() || !Arrays.equals(model_info().units, DKV.get(actual_best_model_key).<DeepLearningModel>get().model_info().units)))
                          ||
                          // otherwise, compare against our own _bestError
                          (DKV.get(actual_best_model_key) == null && error() < _bestError)
          ) ) {
            if (!get_params().quiet_mode)
              Log.info("Error reduced from " + _bestError + " to " + error() + ". Storing best model so far under key " + actual_best_model_key.toString() + ".");
            _bestError = error();
            putMeAsBestModel(actual_best_model_key);

            // debugging check
            if (false) {
              DeepLearningModel bestModel = DKV.get(actual_best_model_key).get();
              final Frame fr = ftest != null ? ftest : ftrain;
              final Frame bestPredict = bestModel.score(fr, ftest != null ? adaptCM : false);
              final Frame hitRatio_bestPredict = new Frame(bestPredict);
              // Adapt output response domain, in case validation domain is different from training domain
              // Note: doesn't change predictions, just the *possible* label domain
              if (adaptCM) {
                final Vec CMadapted = vadaptor.adaptModelResponse2CM(bestPredict.vecs()[0]);
                bestPredict.replace(0, CMadapted); //replace label
                bestPredict.add("to_be_deleted", CMadapted); //keep the Vec around to be deleted later (no leak)
              }
              final double err3 = calcError(fr, fr.lastVec(), bestPredict, hitRatio_bestPredict, "cross-check",
                      printme, get_params().max_confusion_matrix_size, new hex.ConfusionMatrix(), _output.isClassifier() && _output.nclasses() == 2 ? new AUC() : null, null);
              if (_output.isClassifier())
                assert (ftest != null ? Math.abs(err.valid_err - err3) < 1e-5 : Math.abs(err.train_err - err3) < 1e-5);
              else
                assert (ftest != null ? Math.abs(err.valid_mse - err3) < 1e-5 : Math.abs(err.train_mse - err3) < 1e-5);
              bestPredict.delete();
            }
          }
//        else {
//          // keep output JSON small
//          if (errors.length > 1) {
//            if (last_scored().trainAUC != null) last_scored().trainAUC.clear();
//            if (last_scored().validAUC != null) last_scored().validAUC.clear();
//            last_scored().variable_importances = null;
//          }
//        }

          // print the freshly scored model to ASCII
          for (String s : toString().split("\n")) Log.info(s);
          if (printme) Log.info("Time taken for scoring and diagnostics: " + PrettyPrint.msecs(err.scoring_time, true));
        }
      }
      if (model_info().unstable()) {
        Log.warn(unstable_msg);
        keep_running = false;
      } else if ( (_output.isClassifier() && last_scored().train_err <= get_params().classification_stop)
              || (!_output.isClassifier() && last_scored().train_mse <= get_params().regression_stop) ) {
        Log.info("Achieved requested predictive accuracy on the training data. Model building completed.");
        keep_running = false;
      }
      update(job_key);
    }
    catch (Exception ex) {
      ex.printStackTrace();
      keep_running = false;
      throw new RuntimeException(ex);
    } finally {
      return keep_running;
    }
  }

//  @Override protected void setCrossValidationError(Parameters job, double cv_error, ConfusionMatrix cm, AUCData auc, HitRatio hr) {
//    _have_cv_results = true;
//    if (!get_params().classification)
//      last_scored().valid_mse = cv_error;
//    else
//      last_scored().valid_err = cv_error;
//    last_scored().score_validation_samples = last_scored().score_training_samples / get_params().n_folds;
//    last_scored().num_folds = get_params().n_folds;
//    last_scored().valid_confusion_matrix = cm;
//    last_scored().validAUC = auc;
//    last_scored().valid_hitratio = hr;
//    DKV.put(this._key, this); //overwrite this model
//  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(model_info.toString());
    sb.append(last_scored().toString());
    return sb.toString();
  }

  public String toStringAll() {
    StringBuilder sb = new StringBuilder();
    sb.append(model_info.toStringAll());
    sb.append(last_scored().toString());
    return sb.toString();
  }

  /**
   * This is an overridden version of Model.score(). Make either a prediction or a reconstruction.
   * @param frame Test dataset
   * @return A frame containing the prediction or reconstruction
   */
  @Override
  public Frame score(Frame frame) {
    if (!get_params().autoencoder) {
      return super.score(frame);
    } else {
      // Reconstruction
      // Adapt the Frame layout - returns adapted frame and frame containing only
      // newly created vectors
      Frame[] adaptFrms = adapt(frame,false,false/*no response*/);
      // Adapted frame containing all columns - mix of original vectors from fr
      // and newly created vectors serving as adaptors
      Frame adaptFrm = adaptFrms[0];
      // Contains only newly created vectors. The frame eases deletion of these vectors.
      Frame onlyAdaptFrm = adaptFrms[1];

      final int len = model_info().data_info().fullN();
      String prefix = "reconstr_";
      assert(model_info().data_info()._responses == 0);
      String[] coefnames = model_info().data_info().coefNames();
      assert(len == coefnames.length);
      for( int c=0; c<len; c++ )
        adaptFrm.add(prefix+coefnames[c],adaptFrm.anyVec().makeZero());
      new MRTask() {
        @Override public void map( Chunk chks[] ) {
          double tmp [] = new double[_output._names.length];
          float preds[] = new float [len];
          final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
          for( int row=0; row<chks[0].len(); row++ ) {
            float p[] = score_autoencoder(chks, row, tmp, preds, neurons);
            for( int c=0; c<preds.length; c++ )
              chks[_output._names.length+c].set0(row,p[c]);
          }
        }
      }.doAll(adaptFrm);

      // Return the predicted columns
      int x=_output._names.length, y=adaptFrm.numCols();
      Frame f = adaptFrm.extractFrame(x, y); //this will call vec_impl() and we cannot call the delete() below just yet
      onlyAdaptFrm.delete();
      return f;
    }
  }

  /**
   * Predict from raw double values representing the data
   * @param data raw array containing categorical values (horizontalized to 1,0,0,1,0,0 etc.) and numerical values (0.35,1.24,5.3234,etc), both can contain NaNs
   * @param preds predicted label and per-class probabilities (for classification), predicted target (regression), can contain NaNs
   * @return preds, can contain NaNs
   */
  @Override public float[] score0(double[] data, float[] preds) {
    if (model_info().unstable()) {
      Log.warn(unstable_msg);
      throw new UnsupportedOperationException("Trying to predict with an unstable model.");
    }
    Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
    ((Neurons.Input)neurons[0]).setInput(-1, data);
    DeepLearningTask.step(-1, neurons, model_info, false, null);
    float[] out = neurons[neurons.length - 1]._a.raw();
    if (_output.isClassifier()) {
      assert (preds.length == out.length + 1);
      for (int i = 0; i < preds.length - 1; ++i) {
        preds[i + 1] = out[i];
        if (Float.isNaN(preds[i + 1])) throw new RuntimeException("Predicted class probability NaN!");
      }
      preds[0] = ModelUtils.getPrediction(preds, data);
    } else {
      assert (preds.length == 1 && out.length == 1);
      if (model_info().data_info()._normRespMul != null)
        preds[0] = (float) (out[0] / model_info().data_info()._normRespMul[0] + model_info().data_info()._normRespSub[0]);
      else
        preds[0] = out[0];
      if (Float.isNaN(preds[0])) throw new RuntimeException("Predicted regression target NaN!");
    }
    return preds;
  }

  /**
   * Score auto-encoded reconstruction (on-the-fly, without allocating the reconstruction as done in Frame score(Frame fr))
   * @param frame Original data (can contain response, will be ignored)
   * @return Frame containing one Vec with reconstruction error (MSE) of each reconstructed row, caller is responsible for deletion
   */
  public Frame scoreAutoEncoder(Frame frame) {
    final int len = _output._names.length;
    // Adapt the Frame layout - returns adapted frame and frame containing only
    // newly created vectors
    Frame[] adaptFrms = adapt(frame,false,false/*no response*/);
    // Adapted frame containing all columns - mix of original vectors from fr
    // and newly created vectors serving as adaptors
    Frame adaptFrm = adaptFrms[0];
    // Contains only newly created vectors. The frame eases deletion of these vectors.
    Frame onlyAdaptFrm = adaptFrms[1];
    adaptFrm.add("Reconstruction.MSE", adaptFrm.anyVec().makeZero());
    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        double tmp [] = new double[len];
        final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
        for( int row=0; row<chks[0].len(); row++ ) {
          for( int i=0; i<_output._names.length; i++ )
            tmp[i] = chks[i].at0(row); //original data
          chks[len].set0(row, score_autoencoder(tmp, null, neurons)); //store the per-row reconstruction error (MSE) in the last column
        }
      }
    }.doAll(adaptFrm);

    // Return just the output columns
    int x=_output._names.length, y=adaptFrm.numCols();
    final Frame l2 = adaptFrm.extractFrame(x, y);
    onlyAdaptFrm.delete();
    return l2;
  }

  // Make (potentially expanded) reconstruction
  private float[] score_autoencoder(Chunk[] chks, int row_in_chunk, double[] tmp, float[] preds, Neurons[] neurons) {
    assert(get_params().autoencoder);
    assert(tmp.length == _output._names.length);
    for( int i=0; i<tmp.length; i++ )
      tmp[i] = chks[i].at0(row_in_chunk);
    score_autoencoder(tmp, preds, neurons); // this fills preds, returns MSE error (ignored here)
    return preds;
  }

  /**
   * Helper to reconstruct original data into preds array and compute the reconstruction error (MSE)
   * @param data Original data (unexpanded)
   * @param preds Reconstruction (potentially expanded)
   * @return reconstruction error
   */
  private double score_autoencoder(double[] data, float[] preds, Neurons[] neurons) {
    assert(model_info().get_params().autoencoder);
    if (model_info().unstable()) {
      Log.warn(unstable_msg);
      throw new UnsupportedOperationException("Trying to predict with an unstable model.");
    }
    ((Neurons.Input)neurons[0]).setInput(-1, data); // expands categoricals inside
    DeepLearningTask.step(-1, neurons, model_info, false, null); // reconstructs data in expanded space
    float[] in  = neurons[0]._a.raw(); //input (expanded)
    float[] out = neurons[neurons.length - 1]._a.raw(); //output (expanded)
    assert(in.length == out.length);

    // First normalize categorical reconstructions to be probabilities
    // (such that they can be better compared to the input where one factor was 1 and the rest was 0)
//    model_info().data_info().softMaxCategoricals(out,out); //only modifies the categoricals

    // Compute MSE of reconstruction in expanded space (with categorical probabilities)
    double l2 = 0;
    for (int i = 0; i < in.length; ++i)
      l2 += Math.pow((out[i] - in[i]), 2);
    l2 /= in.length;

    if (preds!=null) {
      // Now scale back numerical columns to original data space (scale + shift)
      model_info().data_info().unScaleNumericals(out, out); //only modifies the numericals
      System.arraycopy(out, 0, preds, 0, out.length); //copy reconstruction into preds
    }
    return l2;
  }

//  /**
//   * Compute quantile-based threshold (in reconstruction error) to find outliers
//   * @param mse Vector containing reconstruction errors
//   * @param quantile Quantile for cut-off
//   * @return Threshold in MSE value for a point to be above the quantile
//   */
//  public double calcOutlierThreshold(Vec mse, double quantile) {
//    Frame mse_frame = new Frame(Key.make(), new String[]{"Reconstruction.MSE"}, new Vec[]{mse});
//    QuantilesPage qp = new QuantilesPage();
//    qp.column = mse_frame.vec(0);
//    qp.source_key = mse_frame;
//    qp.quantile = quantile;
//    qp.invoke();
//    DKV.remove(mse_frame._key);
//    return qp.result;
//  }

  // helper to push this model to another key (for keeping good models)
  private void putMeAsBestModel(Key bestModelKey) {
    final Key job = null;
    final DeepLearningModel cp = this;
    DeepLearningModel bestModel = new DeepLearningModel(cp, bestModelKey, job, model_info().data_info());
//    bestModel.get_params()._state = Job.JobState.DONE; //FIXME
//    bestModel.get_params()._key = get_params().self(); //FIXME : is private
    bestModel.delete_and_lock(job);
    bestModel.unlock(job);
    assert (DKV.get(bestModelKey) != null);
    assert (bestModel.compareTo(this) <= 0);
    assert (((DeepLearningModel) DKV.get(bestModelKey).get()).error() == _bestError);
  }

  public void delete_best_model( ) {
    if (actual_best_model_key != null && actual_best_model_key != _key) DKV.remove(actual_best_model_key);
  }

  public void delete_xval_models( ) {
//    if (get_params().xval_models != null) {
//      for (Key k : get_params().xval_models) {
//        DKV.get(k).<DeepLearningModel>get().delete_best_model();
//        DKV.get(k).<DeepLearningModel>get().delete();
//      }
//    }
  }

  transient private final String unstable_msg = "Job was aborted due to observed numerical instability (exponential growth)."
          + "\nTry a different initial distribution, a bounded activation function or adding"
          + "\nregularization with L1, L2 or max_w2 and/or use a smaller learning rate or faster annealing.";

}

