package hex.deeplearning;

import hex.*;
import static hex.ModelMetrics.calcVarImp;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import hex.schemas.DeepLearningModelV2;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.api.ModelSchema;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.lang.Double.isNaN;

/**
 * The Deep Learning model
 * It contains a DeepLearningModelInfo with the most up-to-date model,
 * a scoring history, as well as some helpers to indicate the progress
 */

public class DeepLearningModel extends SupervisedModel<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningModelOutput> implements Model.DeepFeatures {

  public static class DeepLearningParameters extends SupervisedModel.SupervisedParameters {
    public int _n_folds;
    public boolean _keep_cross_validation_splits;

    /**
     * A model key associated with a previously trained Deep Learning
     * model. This option allows users to build a new model as a
     * continuation of a previously generated model (e.g., by a grid search).
     */
    public Key _checkpoint;

    /**
     * If enabled, store the best model under the destination key of this model at the end of training.
     * Only applicable if training is not cancelled.
     */
    public boolean _override_with_best_model = true;

    /**
     * Unlock expert mode parameters than can affect model building speed,
     * predictive accuracy and scoring. Leaving expert mode parameters at default
     * values is fine for many problems, but best results on complex datasets are often
     * only attainable via expert mode options.
     */
    public boolean _expert_mode = false;

    public boolean _autoencoder = false;

    public boolean _use_all_factor_levels = true;

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
    public Activation _activation = Activation.Rectifier;

    /**
     * The number and size of each hidden layer in the model.
     * For example, if a user specifies "100,200,100" a model with 3 hidden
     * layers will be produced, and the middle hidden layer will have 200
     * neurons.To specify a grid search, add parentheses around each
     * model's specification: "(100,100), (50,50,50), (20,20,20,20)".
     */
    public int[] _hidden = new int[] { 200, 200 };

    /**
     * The number of passes over the training dataset to be carried out.
     * It is recommended to start with lower values for initial grid searches.
     * This value can be modified during checkpoint restarts and allows continuation
     * of selected models.
     */
    public double _epochs = 10;

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
    public long _train_samples_per_iteration = -2;

    public double _target_ratio_comm_to_comp = 0.02;

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
    public long _seed = new Random().nextLong();

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
    public boolean _adaptive_rate = true;

    /**
     * The first of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to momentum and relates to the memory to prior weight updates.
     * Typical values are between 0.9 and 0.999.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    public double _rho = 0.99;

    /**
     * The second of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to learning rate annealing during initial training
     * and momentum at later stages where it allows forward progress.
     * Typical values are between 1e-10 and 1e-4.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    public double _epsilon = 1e-8;

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
    public double _rate = .005;

    /**
     * Learning rate annealing reduces the learning rate to "freeze" into
     * local minima in the optimization landscape.  The annealing rate is the
     * inverse of the number of training samples it takes to cut the learning rate in half
     * (e.g., 1e-6 means that it takes 1e6 training samples to halve the learning rate).
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _rate_annealing = 1e-6;

    /**
     * The learning rate decay parameter controls the change of learning rate across layers.
     * For example, assume the rate parameter is set to 0.01, and the rate_decay parameter is set to 0.5.
     * Then the learning rate for the weights connecting the input and first hidden layer will be 0.01,
     * the learning rate for the weights connecting the first and the second hidden layer will be 0.005,
     * and the learning rate for the weights connecting the second and third hidden layer will be 0.0025, etc.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _rate_decay = 1.0;

  /*Momentum*/
    /**
     * The momentum_start parameter controls the amount of momentum at the beginning of training.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_start = 0;

    /**
     * The momentum_ramp parameter controls the amount of learning for which momentum increases
     * (assuming momentum_stable is larger than momentum_start). The ramp is measured in the number
     * of training samples.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_ramp = 1e6;

    /**
     * The momentum_stable parameter controls the final momentum value reached after momentum_ramp training samples.
     * The momentum used for training will remain the same for training beyond reaching that point.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_stable = 0;

    /**
     * The Nesterov accelerated gradient descent method is a modification to
     * traditional gradient descent for convex functions. The method relies on
     * gradient information at various points to build a polynomial approximation that
     * minimizes the residuals in fewer iterations of the descent.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public boolean _nesterov_accelerated_gradient = true;

  /*Regularization*/
    /**
     * A fraction of the features for each training row to be omitted from training in order
     * to improve generalization (dimension sampling).
     */
    public double _input_dropout_ratio = 0.0;

    /**
     * A fraction of the inputs for each hidden layer to be omitted from training in order
     * to improve generalization. Defaults to 0.5 for each hidden layer if omitted.
     */
    public double[] _hidden_dropout_ratios;

    /**
     * A regularization method that constrains the absolute value of the weights and
     * has the net effect of dropping some weights (setting them to zero) from a model
     * to reduce complexity and avoid overfitting.
     */
    public double _l1 = 0.0;

    /**
     *  A regularization method that constrdains the sum of the squared
     * weights. This method introduces bias into parameter estimates, but
     * frequently produces substantial gains in modeling as estimate variance is
     * reduced.
     */
    public double _l2 = 0.0;

    /**
     *  A maximum on the sum of the squared incoming weights into
     * any one neuron. This tuning parameter is especially useful for unbound
     * activation functions such as Maxout or Rectifier.
     */
    public float _max_w2 = Float.POSITIVE_INFINITY;

  /*Initialization*/
    /**
     * The distribution from which initial weights are to be drawn. The default
     * option is an optimized initialization that considers the size of the network.
     * The "uniform" option uses a uniform distribution with a mean of 0 and a given
     * interval. The "normal" option draws weights from the standard normal
     * distribution with a mean of 0 and given standard deviation.
     */
    public InitialWeightDistribution _initial_weight_distribution = InitialWeightDistribution.UniformAdaptive;

    /**
     * The scale of the distribution function for Uniform or Normal distributions.
     * For Uniform, the values are drawn uniformly from -initial_weight_scale...initial_weight_scale.
     * For Normal, the values are drawn from a Normal distribution with a standard deviation of initial_weight_scale.
     */
    public double _initial_weight_scale = 1.0;

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
    public Loss _loss = Loss.Automatic;

  /*Scoring*/
    /**
     * The minimum time (in seconds) to elapse between model scoring. The actual
     * interval is determined by the number of training samples per iteration and the scoring duty cycle.
     */
    public double _score_interval = 5;

    /**
     * The number of training dataset points to be used for scoring. Will be
     * randomly sampled. Use 0 for selecting the entire training dataset.
     */
    public long _score_training_samples = 10000l;

    /**
     * The number of validation dataset points to be used for scoring. Can be
     * randomly sampled or stratified (if "balance classes" is set and "score
     * validation sampling" is set to stratify). Use 0 for selecting the entire
     * training dataset.
     */
    public long _score_validation_samples = 0l;

    /**
     * Maximum fraction of wall clock time spent on model scoring on training and validation samples,
     * and on diagnostics such as computation of feature importances (i.e., not on training).
     */
    public double _score_duty_cycle = 0.1;

    /**
     * The stopping criteria in terms of classification error (1-accuracy) on the
     * training data scoring dataset. When the error is at or below this threshold,
     * training stops.
     */
    public double _classification_stop = 0;

    /**
     * The stopping criteria in terms of regression error (MSE) on the training
     * data scoring dataset. When the error is at or below this threshold, training
     * stops.
     */
    public double _regression_stop = 1e-6;

    /**
     * Enable quiet mode for less output to standard output.
     */
    public boolean _quiet_mode = false;

    /**
     * Method used to sample the validation dataset for scoring, see Score Validation Samples above.
     */
    public ClassSamplingMethod _score_validation_sampling = ClassSamplingMethod.Uniform;

  /*Misc*/
    /**
     * Gather diagnostics for hidden layers, such as mean and RMS values of learning
     * rate, momentum, weights and biases.
     */
    public boolean _diagnostics = true;

    /**
     * Whether to compute variable importances for input features.
     * The implemented method (by Gedeon) considers the weights connecting the
     * input features to the first two hidden layers.
     */
    public boolean _variable_importances = false;

    /**
     * Enable fast mode (minor approximation in back-propagation), should not affect results significantly.
     */
    public boolean _fast_mode = true;

    /**
     * Ignore constant training columns (no information can be gained anyway).
     */
    public boolean _ignore_const_cols = true;

    /**
     * Increase training speed on small datasets by splitting it into many chunks
     * to allow utilization of all cores.
     */
    public boolean _force_load_balance = true;

    /**
     * Replicate the entire training dataset onto every node for faster training on small datasets.
     */
    public boolean _replicate_training_data = true;

    /**
     * Run on a single node for fine-tuning of model parameters. Can be useful for
     * checkpoint resumes after training on multiple nodes for fast initial
     * convergence.
     */
    public boolean _single_node_mode = false;

    /**
     * Enable shuffling of training data (on each node). This option is
     * recommended if training data is replicated on N nodes, and the number of training samples per iteration
     * is close to N times the dataset size, where all nodes train will (almost) all
     * the data. It is automatically enabled if the number of training samples per iteration is set to -1 (or to N
     * times the dataset size or larger).
     */
    public boolean _shuffle_training_data = false;

    public MissingValuesHandling _missing_values_handling = MissingValuesHandling.MeanImputation;

    public boolean _sparse = false;

    public boolean _col_major = false;

    public double _average_activation = 0;

    public double _sparsity_beta = 0;

    /**
     * Max. number of categorical features, enforced via hashing (Experimental)
     */
    public int _max_categorical_features = Integer.MAX_VALUE;

    /**
     * Force reproducibility on small data (will be slow - only uses 1 thread)
     */
    public boolean _reproducible = false;

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

    void validate( DeepLearning dl, boolean expensive ) {
      boolean classification = dl.isClassifier();
      if (_hidden == null || _hidden.length == 0) dl.error("_hidden", "There must be at least one hidden layer.");

      for( int h : _hidden ) if( h==0 ) dl.error("_hidden", "Hidden layer size must be >0.");

      if (!_autoencoder) {
        if (_valid == null)
          dl.hide("_score_validation_samples", "score_validation_samples requires a validation frame.");

        if (classification) {
          dl.hide("_regression_stop", "regression_stop is used only with regression.");
        } else {
          dl.hide("_classification_stop", "classification_stop is used only with classification.");
          dl.hide("_max_confusion_matrix_size", "max_confusion_matrix_size is used only with classification.");
          dl.hide("_max_hit_ratio_k", "max_hit_ratio_k is used only with classification.");
          dl.hide("_balance_classes", "balance_classes is used only with classification.");
        }

        if( !classification || !_balance_classes )
          dl.hide("_class_sampling_factors", "class_sampling_factors requires both classification and balance_classes.");


        if (!classification && _valid != null || _valid == null)
          dl.hide("_score_validation_sampling", "score_validation_sampling requires regression and a validation frame OR no validation frame.");
      }

      // Auto-fill defaults
      if (_activation != Activation.TanhWithDropout && _activation != Activation.MaxoutWithDropout && _activation != Activation.RectifierWithDropout)
        dl.hide("_hidden_dropout_ratios", "hidden_dropout_ratios requires a dropout activation function.");
      if (_hidden_dropout_ratios == null) {
        if (_activation == Activation.TanhWithDropout || _activation == Activation.MaxoutWithDropout || _activation == Activation.RectifierWithDropout) {
          if (expensive) {
            _hidden_dropout_ratios = new double[_hidden.length];
            if (!_quiet_mode)
              dl.info("_hidden_dropout_ratios", "Automatically setting all hidden dropout ratios to 0.5.");
            Arrays.fill(_hidden_dropout_ratios, 0.5);
          }
        }
      }
      else if (_hidden_dropout_ratios.length != _hidden.length) {
        dl.error("_hidden_dropout_ratios", "Must have " + _hidden.length + " hidden layer dropout ratios.");
      }
      else if (_activation != Activation.TanhWithDropout && _activation != Activation.MaxoutWithDropout && _activation != Activation.RectifierWithDropout) {
        if (!_quiet_mode) dl.warn("_hidden_dropout_ratios", "Ignoring hidden_dropout_ratios because a non-dropout activation function was specified.");
      }

      if (_input_dropout_ratio < 0 || _input_dropout_ratio >= 1)
        dl.error("_input_dropout_ratio", "Input dropout must be in [0,1).");

      if (H2O.CLOUD.size() == 1 && _replicate_training_data) {
        dl.hide("_replicate_training_data", "replicate_training_data is only valid with cloud size greater than 1.");
        if (expensive) {
          dl.info("_replicate_training_data", "Disabling replicate_training_data on 1 node.");
          _replicate_training_data = false;
        }
      }
      if (_single_node_mode && (H2O.CLOUD.size() == 1 || !_replicate_training_data)) {
        dl.hide("_single_node_mode", "single_node_mode is only used with multi-node operation with replicated training data.");
        if (expensive) {
          dl.info("_single_node_mode", "Disabling single_node_mode (only for multi-node operation with replicated training data).");
          _single_node_mode = false;
        }
      }

      if (_autoencoder) {
        dl.hide("_use_all_factor_levels", "use_all_factor_levels is unsupported in combination with autoencoder.");
        dl.hide("_convert_to_enum", "convert_to_enum is unsupported in combination with autoencoder.");
      }
      if (!_use_all_factor_levels && _autoencoder ) {
        if (expensive) {
          dl.warn("_use_all_factor_levels", "Enabling all_factor_levels for auto-encoders.");
          _use_all_factor_levels = true;
        }
      }

      if (_n_folds != 0)
        dl.hide("_override_with_best_model", "override_with_best_model is unsupported in combination with n-fold cross-validation.");
      if(_override_with_best_model && _n_folds != 0) {
        if (expensive) {
          dl.warn("_override_with_best_model", "Disabling override_with_best_model in combination with n-fold cross-validation.");
          _override_with_best_model = false;
        }
      }

      if (_adaptive_rate) {
        dl.hide("_rate", "rate is not used with adaptive_rate.");
        dl.hide("_rate_annealing", "rate_annealing is not used with adaptive_rate.");
        dl.hide("_rate_decay", "rate_decay is not used with adaptive_rate.");
        dl.hide("_momentum_start", "momentum_start is not used with adaptive_rate.");
        dl.hide("_momentum_ramp", "momentum_ramp is not used with adaptive_rate.");
        dl.hide("_momentum_stable", "momentum_stable is not used with adaptive_rate.");
        dl.hide("_nesterov_accelerated_gradient", "nesterov_accelerated_gradient is not used with adaptive_rate.");
      } else {
        // ! adaptive_rate
        dl.hide("_rho", "rho is only used with adaptive_rate.");
        dl.hide("_epsilon", "epsilon is only used with adaptive_rate.");
      }
      if (!_quiet_mode) {
        if (_adaptive_rate) {
          if (expensive) {
            dl.info("_adaptive_rate", "Using automatic learning rate.  Ignoring the following input parameters: "
                    + "rate, rate_decay, rate_annealing, momentum_start, momentum_ramp, momentum_stable, nesterov_accelerated_gradient.");
            _momentum_start = 0;
            _momentum_stable = 0;
          }
        } else {
          if (expensive) {
            dl.info("_adaptive_rate", "Using manual learning rate.  Ignoring the following input parameters: "
                    + "rho, epsilon.");
            _rho = 0;
            _epsilon = 0;
          }
        }

        if (_initial_weight_distribution == InitialWeightDistribution.UniformAdaptive) {
          dl.hide("_initial_weight_scale", "initial_weight_scale is not used if initial_weight_distribution == UniformAdaptive.");
        }
        if (_n_folds != 0) {
          if (expensive) {
            if (_override_with_best_model) {
              dl.warn("_override_with_best_model", "Automatically disabling override_with_best_model, since the final model is the only scored model with n-fold cross-validation.");
              _override_with_best_model = false;
            }
          }
        }
      }

      if(_loss == Loss.Automatic) {
        if (expensive) {
          if (!classification) {
            if (!_quiet_mode) dl.info("_loss", "Automatically setting loss to MeanSquare for regression.");
            _loss = Loss.MeanSquare;
          } else if (_autoencoder) {
            if (!_quiet_mode) dl.info("_loss", "Automatically setting loss to MeanSquare for auto-encoder.");
            _loss = Loss.MeanSquare;
          } else {
            if (!_quiet_mode) dl.info("_loss", "Automatically setting loss to Cross-Entropy for classification.");
            _loss = Loss.CrossEntropy;
          }
        }
      }
      else {
        if (_autoencoder && _loss != Loss.MeanSquare)
          dl.error("_loss", "Must use MeanSquare loss function for auto-encoder.");
        else if (!classification && _loss == Loss.CrossEntropy)
          dl.error("_loss", "Cannot use CrossEntropy loss function for regression.");
      }

      if (_score_training_samples < 0) {
        dl.error("_score_training_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
      }
      if (_score_validation_samples < 0) {
        dl.error("_score_validation_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
      }

      if(_autoencoder && _sparsity_beta > 0) {
        if (_activation == Activation.Tanh || _activation == Activation.TanhWithDropout) {
          if (_average_activation >= 1 || _average_activation <= -1)
            dl.error("_average_activation", "Tanh average activation must be in (-1,1).");
        }
        else if (_activation == Activation.Rectifier || _activation == Activation.RectifierWithDropout) {
          if (_average_activation <= 0)
            dl.error("_average_activation", "Rectifier average activation must be positive.");
        }
      }
      if (!_autoencoder && _sparsity_beta != 0) dl.info("_sparsity_beta", "Sparsity beta can only be used for autoencoder.");

      // reason for the error message below is that validation might not have the same horizontalized features as the training data (or different order)
      if (_autoencoder && _valid != null) dl.error("_validation_frame", "Cannot specify a validation dataset for auto-encoder.");
      if (_autoencoder && _activation == Activation.Maxout) dl.error("_activation", "Maxout activation is not supported for auto-encoder.");
      if (_max_categorical_features < 1) dl.error("_max_categorical_features", "max_categorical_features must be at least 1.");

      if (!_sparse && _col_major) {
        if (!_quiet_mode) dl.error("_col_major", "Cannot use column major storage for non-sparse data handling.");
      }
      if (!classification && _balance_classes) {
        dl.error("_balance_classes", "balance_classes requires classification to be enabled.");
      }
      if (_class_sampling_factors != null && !_balance_classes) {
        dl.error("_class_sampling_factors", "class_sampling_factors requires balance_classes to be enabled.");
      }
      if (_reproducible) {
        if (expensive) {
          if (!_quiet_mode)
            Log.info("Automatically enabling force_load_balancing, disabling single_node_mode and replicate_training_data\nand setting train_samples_per_iteration to -1 to enforce reproducibility.");
          _force_load_balance = true;
          _single_node_mode = false;
          _train_samples_per_iteration = -1;
          _replicate_training_data = false; //there's no benefit from having multiple nodes compute the exact same thing, and then average it back to the same
          //      replicate_training_data = true; //doesn't hurt, but does replicated identical work
        }
      }
    }
  }

  public static class DeepLearningModelOutput extends SupervisedModel.SupervisedOutput {
    public DeepLearningModelOutput() { super(); }
    public DeepLearningModelOutput(DeepLearning b) { super(b); }
    boolean autoencoder;
    DeepLearningScoring errors;
    TwoDimTable modelSummary;
    TwoDimTable scoringHistory;
    TwoDimTable variableImportances;
    ModelMetrics trainMetrics;
    ModelMetrics validMetrics;
    double run_time;

    @Override public ModelCategory getModelCategory() {
      return autoencoder ? ModelCategory.AutoEncoder : super.getModelCategory();
    }
  }

  // Default publicly visible Schema is V2
  public ModelSchema schema() { return new DeepLearningModelV2(); }

  private volatile DeepLearningModelInfo model_info;
  void set_model_info(DeepLearningModelInfo mi) { model_info = mi; }
  final public DeepLearningModelInfo model_info() { return model_info; }

  public long run_time;
  private long start_time;

  public long actual_train_samples_per_iteration;
  public double time_for_communication_us; //helper for auto-tuning: time in microseconds for collective bcast/reduce of the model

  public double epoch_counter;

  public long training_rows;

  public long validation_rows;

  private DeepLearningScoring[] errors;
  public DeepLearningScoring[] scoring_history() { return errors; }

  // Keep the best model so far, based on a single criterion (overall class. error or MSE)
  private float _bestError = Float.POSITIVE_INFINITY;

  public Key actual_best_model_key;

  // return the most up-to-date model metrics
  DeepLearningScoring last_scored() { return errors == null ? null : errors[errors.length-1]; }

//  @Override
  public final DeepLearningParameters get_params() { return _parms; }
//  @Override public final Request2 job() { return get_params(); }

  double missingColumnsType() { return get_params()._sparse ? 0 : Double.NaN; }

  public float error() { return (float) (_output.isClassifier() ? cm().err() : mse()); }

  @Override public boolean isSupervised() { return !model_info.get_params()._autoencoder; }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain, ModelUtils.DEFAULT_THRESHOLDS);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(domain);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      case AutoEncoder: return new ModelMetricsAutoEncoder.MetricBuilderAutoEncoder(_output.nfeatures());
      default: throw H2O.unimpl();
    }
  }

  public int compareTo(DeepLearningModel o) {
    if (o._output.isClassifier() != _output.isClassifier()) throw new UnsupportedOperationException("Cannot compare classifier against regressor.");
    if (o._output.nclasses() != _output.nclasses()) throw new UnsupportedOperationException("Cannot compare models with different number of classes.");
    return (error() < o.error() ? -1 : error() > o.error() ? 1 : 0);
  }

  public static class DeepLearningScoring extends Iced {
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
    public float[] train_hitratio; // "Hit ratio on training data"
    public float[] valid_hitratio; // "Hit ratio on validation data"

    // regression
    public double train_mse = Double.POSITIVE_INFINITY;
    public double valid_mse = Double.POSITIVE_INFINITY;
    public double train_r2 = Double.NaN;
    public double valid_r2 = Double.NaN;

    public long scoring_time;

    DeepLearningScoring deep_clone() {
      AutoBuffer ab = new AutoBuffer();
      this.write(ab);
      ab.flipForReading();
      return (DeepLearningScoring) new DeepLearningScoring().read(ab);
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Training MSE: " + train_mse + "\n");
      if (classification) {
        sb.append("Training R^2: " + train_r2 + "\n");
        sb.append("Training " + train_confusion_matrix.table.toString(1));
        sb.append("Training Misclassification"
                + (trainAUC != null ? " [using threshold for " + trainAUC.threshold_criterion.toString().replace("_", " ") + "]: " : ": ")
                + String.format("%.2f", 100 * train_err) + "%");
        if (trainAUC != null) sb.append(", AUC: " + String.format("%.4f", 100 * trainAUC.AUC) + "%");
      }
      if (validation || num_folds>0) {
        if (num_folds > 0) {
          sb.append("\nDoing " + num_folds + "-fold cross-validation:");
        }
        sb.append("\nValidation MSE: " + valid_mse + "\n");
        sb.append("Validation R^2: " + valid_r2 + "\n");
        if (classification) {
          sb.append("Validation " + valid_confusion_matrix.table.toString(1));
          sb.append("Validation Misclassification"
                  + (validAUC != null ? " [using threshold for " + validAUC.threshold_criterion.toString().replace("_", " ") + "]: " : ": ")
                  + String.format("%.2f", (100 * valid_err)) + "%");
          if (validAUC != null) sb.append(", AUC: " + String.format("%.4f", 100 * validAUC.AUC) + "%");
        }
      }
      sb.append("\n");
      return sb.toString();
    }
  }

  final private static class ConfMat extends ConfusionMatrix {
    final private double _err;
    final private double _f1;
    public ConfMat(double err, double f1) {
      super(null, null);
      _err=err;
      _f1=f1;
    }
    @Override public double err() { return _err; }
    @Override public double F1() { return _f1; }
    @Override public double[] classErr() { return null; }
  }

  /** for grid search error reporting */
//  @Override
  public ConfusionMatrix cm() {
    final DeepLearningScoring lasterror = last_scored();
    if (lasterror == null) return null;
    ConfusionMatrix cm = lasterror.validation || lasterror.num_folds > 0 ?
            lasterror.valid_confusion_matrix :
            lasterror.train_confusion_matrix;
    if (cm == null ) {
      if (lasterror.validation || lasterror.num_folds > 0) {
        return new ConfMat(lasterror.valid_err, lasterror.validAUC != null ? lasterror.validAUC.F1() : 0);
      } else {
        return new ConfMat(lasterror.train_err, lasterror.trainAUC != null ? lasterror.trainAUC.F1() : 0);
      }
    }
    return cm;
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

  private TwoDimTable createScoringHistoryTable(DeepLearningScoring[] errors) {
    return createScoringHistoryTable(errors, 20);
  }

  private TwoDimTable createScoringHistoryTable(DeepLearningScoring[] errors, final int size_limit) {
    assert (size_limit >= 10);
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Training Duration"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Training Epochs"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("Training Samples"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Training MSE"); colTypes.add("double"); colFormat.add("%.5f");
    if (!_output.autoencoder) {
      colHeaders.add("Training R^2");
      colTypes.add("double");
      colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial) {
      colHeaders.add("Training AUC");
      colTypes.add("double");
      colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial || _output.getModelCategory() == ModelCategory.Multinomial) {
      colHeaders.add("Training Classification Error");
      colTypes.add("double");
      colFormat.add("%.5f");
    }
    if (get_params()._valid != null) {
      colHeaders.add("Validation MSE"); colTypes.add("double"); colFormat.add("%.5f");
      if (!_output.autoencoder) {
        colHeaders.add("Validation R^2");
        colTypes.add("double");
        colFormat.add("%.5f");
      }
      if (_output.getModelCategory() == ModelCategory.Binomial) {
        colHeaders.add("Validation AUC");
        colTypes.add("double");
        colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Validation Classification Error");
        colTypes.add("double");
        colFormat.add("%.5f");
      }
    } else if (get_params()._n_folds > 0) {
      colHeaders.add("Cross-Validation MSE"); colTypes.add("double"); colFormat.add("%.5f");
//      colHeaders.add("Validation R^2"); colTypes.add("double"); colFormat.add("%g");
      if (_output.getModelCategory() == ModelCategory.Binomial) {
        colHeaders.add("Cross-Validation AUC");
        colTypes.add("double");
        colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Cross-Validation Classification Error");
        colTypes.add("double");
        colFormat.add("%.5f");
      }
    }

    List<Integer> which = new ArrayList<>();
    if (errors.length > size_limit) {
      // always show first few
      which.add(0);
//      which.add(1);
//      which.add(2);
//      which.add(3);
//      which.add(4);

      // always show last few
//      which.add(errors.length-5);
//      which.add(errors.length-4);
//      which.add(errors.length-3);
//      which.add(errors.length-2);
      which.add(errors.length-1);

      // pick the remaining scoring points from the middle section
      final float step = (float)(errors.length-which.size())/(size_limit-which.size());
      for (float i=5; i<errors.length-5; i+=step) {
        if (which.size() < size_limit) which.add((int)i);
      }
    }
    final int rows = Math.min(size_limit, errors.length);
    TwoDimTable table = new TwoDimTable(
            "Scoring History",
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    for( int i = 0; i<errors.length ; i++ ) {
      if (errors.length > size_limit && !which.contains(new Integer(i))) continue;
      final DeepLearningScoring e = errors[i];
      int col = 0;
      assert(row < table.getRowDim());
      assert(col < table.getColDim());
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      table.set(row, col++, fmt.print(start_time + e.training_time_ms));
      table.set(row, col++, PrettyPrint.msecs(e.training_time_ms, true));
      table.set(row, col++, e.epoch_counter);
      table.set(row, col++, e.training_samples);
      table.set(row, col++, e.train_mse);
      if (!_output.autoencoder)
        table.set(row, col++, e.train_r2);
      if (_output.getModelCategory() == ModelCategory.Binomial)
        table.set(row, col++, e.trainAUC != null ? e.trainAUC.AUC() : Double.NaN);
      if (_output.isClassifier())
        table.set(row, col++, e.train_err);
      if (get_params()._valid != null) {
        table.set(row, col++, e.valid_mse);
        if (!_output.autoencoder)
          table.set(row, col++, e.valid_r2);
        if (_output.getModelCategory() == ModelCategory.Binomial)
          table.set(row, col++, e.validAUC != null ? e.validAUC.AUC() : Double.NaN);
        if (_output.isClassifier())
          table.set(row, col++, e.valid_err);
      }
      else if(get_params()._n_folds > 0) {
        throw H2O.unimpl();
      }
      row++;
    }
    return table;
  }


  // This describes the model, together with the parameters
  // This will be shared: one per node
  public static class DeepLearningModelInfo extends Iced {

    public TwoDimTable summaryTable;
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
    boolean has_momenta() { return get_params()._momentum_start != 0 || get_params()._momentum_stable != 0; }
    boolean adaDelta() { return get_params()._adaptive_rate; }
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

    final boolean _classification; // Classification cache (nclasses>1)
    final Frame _train;         // Prepared training frame
    final Frame _valid;         // Prepared validation frame

    public DeepLearningModelInfo() {
      _classification = false;
      _train = _valid = null;
    }

    public DeepLearningModelInfo(final DeepLearningParameters params, final DataInfo dinfo, boolean classification, Frame train, Frame valid) {
      _classification = classification;
      _train = train;
      _valid = valid;
      data_info = dinfo;
      parameters = params;
      final int num_input = dinfo.fullN();
      final int num_output = get_params()._autoencoder ? num_input : (_classification ? train.lastVec().cardinality() : 1);
      assert(num_input > 0);
      assert(num_output > 0);
      if (has_momenta() && adaDelta()) throw new IllegalArgumentException("Cannot have non-zero momentum and adaptive rate at the same time.");
      final int layers=get_params()._hidden.length;
      // units (# neurons for each layer)
      units = new int[layers+2];
      if (get_params()._max_categorical_features <= Integer.MAX_VALUE - dinfo._nums)
        units[0] = Math.min(dinfo._nums + get_params()._max_categorical_features, num_input);
      else
        units[0] = num_input;
      System.arraycopy(get_params()._hidden, 0, units, 1, layers);
      units[layers+1] = num_output;

      if ((long)units[0] > 100000L) {
        final String[][] domains = dinfo._adaptedFrame.domains();
        int[] levels = new int[domains.length];
        for (int i=0; i<levels.length; ++i) {
          levels[i] = domains[i] != null ? domains[i].length : 0;
        }
        Arrays.sort(levels);
        Log.warn("===================================================================================================================================");
        Log.warn(num_input + " input features" + (dinfo._cats > 0 ? " (after categorical one-hot encoding)" : "") + ". Can be slow and require a lot of memory.");
        if (levels[levels.length-1] > 0) {
          int levelcutoff = levels[levels.length-1-Math.min(10, levels.length)];
          int count = 0;
          for (int i=0; i<dinfo._adaptedFrame.numCols() - (get_params()._autoencoder ? 0 : 1) && count < 10; ++i) {
            if (dinfo._adaptedFrame.domains()[i] != null && dinfo._adaptedFrame.domains()[i].length >= levelcutoff) {
              Log.warn("Categorical feature '" + dinfo._adaptedFrame._names[i] + "' has cardinality " + dinfo._adaptedFrame.domains()[i].length + ".");
            }
            count++;
          }
        }
        Log.warn("Suggestions:");
        Log.warn(" *) Limit the size of the first hidden layer");
        if (dinfo._cats > 0) {
          Log.warn(" *) Limit the total number of one-hot encoded features with the parameter 'max_categorical_features'");
          Log.warn(" *) Run h2o.interaction(...,pairwise=F) on high-cardinality categorical columns to limit the factor count, see http://learn.h2o.ai");
        }
        Log.warn("===================================================================================================================================");
      }

      // weights (to connect layers)
      dense_row_weights = new Neurons.DenseRowMatrix[layers+1];
      dense_col_weights = new Neurons.DenseColMatrix[layers+1];

      // decide format of weight matrices row-major or col-major
      if (get_params()._col_major) dense_col_weights[0] = new Neurons.DenseColMatrix(units[1], units[0]);
      else dense_row_weights[0] = new Neurons.DenseRowMatrix(units[1], units[0]);
      for (int i = 1; i <= layers; ++i)
        dense_row_weights[i] = new Neurons.DenseRowMatrix(units[i + 1] /*rows*/, units[i] /*cols*/);

      // biases (only for hidden layers and output layer)
      biases = new Neurons.DenseVector[layers+1];
      for (int i=0; i<=layers; ++i) biases[i] = new Neurons.DenseVector(units[i+1]);
      // average activation (only for hidden layers)
      if (get_params()._autoencoder && get_params()._sparsity_beta > 0) {
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

    public TwoDimTable createSummaryTable() {
      Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(this);
      TwoDimTable table = new TwoDimTable(
              "Status of Neuron Layers",
              new String[neurons.length],
              new String[]{"#", "Units", "Type", "Dropout", "L1", "L2",
                      (get_params()._adaptive_rate ? "Rate (Mean,RMS)" : "Rate"),
                      (get_params()._adaptive_rate ? "" : "Momentum"),
                      "Weight (Mean,RMS)",
                      "Bias (Mean,RMS)"
              },
              new String[]{"integer", "integer", "string", "double", "double", "double",
                           "string", "string", "string", "string"},
              new String[]{"%d", "%d", "%s", "%2.2f %%", "%5f", "%5f", "%s", "%s", "%s", "%s"},
              "");

      final String format = "%7g";
      for (int i = 0; i < neurons.length; ++i) {
        table.set(i, 0, i + 1);
        table.set(i, 1, neurons[i].units);
        table.set(i, 2, neurons[i].getClass().getSimpleName());

        if (i == 0) {
          table.set(i, 3, neurons[i].params._input_dropout_ratio);
          continue;
        } else if (i < neurons.length - 1) {
          if (neurons[i].params._hidden_dropout_ratios == null) {
            table.set(i, 3, 0);
          } else {
            table.set(i, 3, neurons[i].params._hidden_dropout_ratios[i - 1]);
          }
        }
        table.set(i, 4, neurons[i].params._l1);
        table.set(i, 5, neurons[i].params._l2);
        table.set(i, 6, (get_params()._adaptive_rate ? (" (" + String.format(format, mean_rate[i]) + ", " + String.format(format, rms_rate[i]) + ")")
                : (String.format("%10g", neurons[i].rate(get_processed_total())))));
        table.set(i, 7, get_params()._adaptive_rate ? "" : String.format("%5f", neurons[i].momentum(get_processed_total())));
        table.set(i, 8, " (" + String.format(format, mean_weight[i])
                + ", " + String.format(format, rms_weight[i]) + ")");
        table.set(i, 9, " (" + String.format(format, mean_bias[i])
                + ", " + String.format(format, rms_bias[i]) + ")");
      }
      summaryTable = table;
      return summaryTable;
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      if (get_params()._diagnostics && !get_params()._quiet_mode) {
        sb.append("Number of hidden layers is " + get_params()._hidden.length + " \n");
        if (get_params()._sparsity_beta > 0) {
          for (int k = 0; k < get_params()._hidden.length; k++)
            sb.append("Average activation in hidden layer " + k + " is  " + mean_a[k] + " \n");
        }
        if (summaryTable == null) createSummaryTable();
        sb.append(summaryTable.toString(1));
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
      for (int i=0; i<get_params()._hidden.length; ++i) {
        if (get_params()._activation == DeepLearningParameters.Activation.Rectifier
                || get_params()._activation == DeepLearningParameters.Activation.RectifierWithDropout
                || get_params()._activation == DeepLearningParameters.Activation.Maxout
                || get_params()._activation == DeepLearningParameters.Activation.MaxoutWithDropout
                ) {
//          Arrays.fill(biases[i], 1.); //old behavior
          Arrays.fill(biases[i].raw(), i == 0 ? 0.5f : 1f); //new behavior, might be slightly better
        }
        else if (get_params()._activation == DeepLearningParameters.Activation.Tanh || get_params()._activation == DeepLearningParameters.Activation.TanhWithDropout) {
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
        final Random rng = water.util.RandomUtils.getDeterRNG(get_params()._seed + 0xBAD5EED + w+1); //to match NeuralNet behavior
        final double range = Math.sqrt(6. / (units[w] + units[w+1]));
        for( int i = 0; i < get_weights(w).rows(); i++ ) {
          for( int j = 0; j < get_weights(w).cols(); j++ ) {
            if (get_params()._initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.UniformAdaptive) {
              // cf. http://machinelearning.wustl.edu/mlpapers/paper_files/AISTATS2010_GlorotB10.pdf
              if (w==dense_row_weights.length-1 && _classification)
                get_weights(w).set(i,j, (float)(4.*uniformDist(rng, -range, range))); //Softmax might need an extra factor 4, since it's like a sigmoid
              else
                get_weights(w).set(i,j, (float)uniformDist(rng, -range, range));
            }
            else if (get_params()._initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.Uniform) {
              get_weights(w).set(i,j, (float)uniformDist(rng, -get_params()._initial_weight_scale, get_params()._initial_weight_scale));
            }
            else if (get_params()._initial_weight_distribution == DeepLearningParameters.InitialWeightDistribution.Normal) {
              get_weights(w).set(i,j, (float)(rng.nextGaussian() * get_params()._initial_weight_scale));
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
      float[][] rate = get_params()._adaptive_rate ? new float[units.length-1][] : null;

      if (get_params()._autoencoder && get_params()._sparsity_beta > 0) {
        for (int k = 0; k < get_params()._hidden.length; k++) {
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
            final float RMS_dx = MathUtils.approxSqrt(get_ada_dx_g(y-1).raw()[2*u]+(float)get_params()._epsilon);
            final float invRMS_g = MathUtils.approxInvSqrt(get_ada_dx_g(y-1).raw()[2*u+1]+(float)get_params()._epsilon);
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

  /** Constructor to restart from a checkpointed model
   * @param destKey New destination key for the model
   *  @param cp Checkpoint to restart from
   * @param store_best_model Store only the best model instead of the latest one  */
  public DeepLearningModel(final Key destKey, final DeepLearningModel cp, final boolean store_best_model, final DataInfo dataInfo) {
    super(destKey, (DeepLearningParameters)cp._parms.clone(), (DeepLearningModelOutput)cp._output.clone());
    if (store_best_model) {
      model_info = cp.model_info.deep_clone(); //don't want to interfere with model being built, just make a deep copy and store that
      model_info.data_info = dataInfo.deep_clone(); //replace previous data_info with updated version that's passed in (contains enum for classification)
    } else {
      model_info = (DeepLearningModelInfo) cp.model_info.clone(); //shallow clone is ok (won't modify the Checkpoint in K-V store during checkpoint restart)
      model_info.data_info = dataInfo; //shallow clone is ok
      // Ok to modify (the normally immutable read-only) parameters, because
      // this is a private copy just cloned above in the super() call.
      _parms._checkpoint = cp._key; //it's only a "real" checkpoint if job != null, otherwise a best model copy
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
    _output.errors = last_scored();
    _output.scoringHistory = createScoringHistoryTable(errors);
    _output.variableImportances = calcVarImp(last_scored().variable_importances);

    // set proper timing
    _timeLastScoreEnter = System.currentTimeMillis();
    _timeLastScoreStart = 0;
    _timeLastScoreEnd = 0;
    _timeLastPrintStart = 0;
    assert(Arrays.equals(_key._kb, destKey._kb));
  }

  public DeepLearningModel(final Key destKey, final DeepLearningParameters parms, final DeepLearningModelOutput output, Frame train, Frame valid) {
    super(destKey, parms, output);
    boolean classification = train.lastVec().isEnum();
    final DataInfo dinfo = new DataInfo(Key.make(), train, valid, parms._autoencoder ? 0 : 1, parms._autoencoder || parms._use_all_factor_levels, //use all FactorLevels for auto-encoder
            parms._autoencoder ? DataInfo.TransformType.NORMALIZE : DataInfo.TransformType.STANDARDIZE, //transform predictors
            classification    ? DataInfo.TransformType.NONE      : DataInfo.TransformType.STANDARDIZE, _parms._missing_values_handling == DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip);
    output._names  = train._names   ; // Since changed by DataInfo, need to be reflected in the Model output as well
    output._domains= train.domains();
    DKV.put(dinfo._key,dinfo);
    model_info = new DeepLearningModelInfo(parms, dinfo, classification, train, valid);
    actual_best_model_key = Key.makeUserHidden(Key.make());
    if (parms._n_folds != 0) actual_best_model_key = null;
    if (!parms._autoencoder) {
      errors = new DeepLearningScoring[1];
      errors[0] = new DeepLearningScoring();
      errors[0].validation = (parms._valid != null);
      errors[0].num_folds = parms._n_folds;
      _output.errors = last_scored();
      _output.scoringHistory = createScoringHistoryTable(errors);
      _output.variableImportances = calcVarImp(last_scored().variable_importances);
    }
    run_time = 0;
    start_time = System.currentTimeMillis();
    _timeLastScoreEnter = start_time;
    assert _key.equals(destKey);
  }

  public long _timeLastScoreEnter; //not transient: needed for HTML display page
  transient private long _timeLastScoreStart;
  transient private long _timeLastScoreEnd;
  transient private long _timeLastPrintStart;
  /**
   *
   * @param ftrain potentially downsampled training data for scoring
   * @param ftest  potentially downsampled validation data for scoring
   * @param job_key key of the owning job
   * @param progressKey key of the progress
   * @return true if model building is ongoing
   */
  boolean doScoring(Frame ftrain, Frame ftest, Key job_key, Key progressKey) {
    boolean keep_running;
    try {
      final long now = System.currentTimeMillis();
      epoch_counter = (float)model_info().get_processed_total()/training_rows;
      final double time_last_iter_millis = Math.max(5,now-_timeLastScoreEnter);

      // Auto-tuning
      // if multi-node and auto-tuning and at least 10 ms for communication (to avoid doing thins on multi-JVM on same node),
      // then adjust the auto-tuning parameter 'actual_train_samples_per_iteration' such that the targeted ratio of comm to comp is achieved
      // Note: actual communication time is estimated by the NetworkTest's collective test.
      if (H2O.CLOUD.size() > 1 && get_params()._train_samples_per_iteration == -2 && time_for_communication_us > 1e4) {
//        Log.info("Time taken for communication: " + PrettyPrint.usecs((long)time_for_communication_us));
//        Log.info("Time taken for Map/Reduce iteration: " + PrettyPrint.msecs((long)time_last_iter_millis, true));
        final double comm_to_work_ratio = (time_for_communication_us *1e-3) / time_last_iter_millis;
//        Log.info("Ratio of network communication to computation: " + String.format("%.3f", comm_to_work_ratio));
//        Log.info("target_comm_to_work: " + get_params().target_ratio_comm_to_comp);
        final double correction = get_params()._target_ratio_comm_to_comp / comm_to_work_ratio;
//        Log.warn("Suggested value for train_samples_per_iteration: " + get_params().actual_train_samples_per_iteration/correction);
        actual_train_samples_per_iteration /= correction;
        actual_train_samples_per_iteration = Math.max(1, actual_train_samples_per_iteration);
      }

      run_time += time_last_iter_millis;
      _timeLastScoreEnter = now;
      keep_running = (epoch_counter < get_params()._epochs);
      final long sinceLastScore = now -_timeLastScoreStart;
      final long sinceLastPrint = now -_timeLastPrintStart;
      final long samples = model_info().get_processed_total();
      if (!keep_running || sinceLastPrint > get_params()._score_interval *1000) {
        _timeLastPrintStart = now;
//        Log.info("Training time: " + PrettyPrint.msecs(run_time, true)
//                + ". Processed " + String.format("%,d", samples) + " samples" + " (" + String.format("%.3f", epoch_counter) + " epochs)."
//                + " Speed: " + String.format("%.3f", 1000.*samples/run_time) + " samples/sec.");
      }

      // this is potentially slow - only do every so often
      if( !keep_running ||
              (sinceLastScore > get_params()._score_interval *1000 //don't score too often
                      &&(double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < get_params()._score_duty_cycle) ) { //duty cycle
        if (progressKey != null) {
          new Job.ProgressUpdate("Scoring on " + ftrain.numRows() + " training samples" +
                  (ftest != null ? (", " + ftest.numRows() + " validation samples)") : ")")
          ).fork(progressKey);
        }
        final boolean printme = !get_params()._quiet_mode;
        _timeLastScoreStart = now;
        if (get_params()._diagnostics) model_info().computeStats();
        DeepLearningScoring err = new DeepLearningScoring();
        err.training_time_ms = run_time;
        err.epoch_counter = epoch_counter;
        err.training_samples = model_info().get_processed_total();
        err.validation = ftest != null;
        err.score_training_samples = ftrain.numRows();
        err.classification = _output.isClassifier();

        if (get_params()._autoencoder) {
          if (printme) Log.info("Scoring the auto-encoder.");
          // training
          {
            final Frame mse_frame = scoreAutoEncoder(ftrain, Key.make());
            final Vec l2 = mse_frame.anyVec();
            Log.info("Mean reconstruction error on training data: " + l2.mean() + "\n");
            err.train_mse = l2.mean();
            mse_frame.delete();
          }
        } else {
          if (printme) Log.info("Scoring the model.");
          // compute errors
          final String m = model_info().toString();
          if (m.length() > 0) Log.info(m);
          final Frame trainPredict = score(ftrain);
          trainPredict.delete();

          hex.ModelMetricsSupervised mm1 = (ModelMetricsSupervised)ModelMetrics.getFromDKV(this,ftrain);
          if (mm1 instanceof ModelMetricsBinomial) {
            ModelMetricsBinomial mm = (ModelMetricsBinomial)(mm1);
            err.trainAUC = mm._aucdata;
            err.train_confusion_matrix = mm._cm;
            err.train_err = mm._cm.err();
          }
          else if (mm1 instanceof ModelMetricsMultinomial) {
            ModelMetricsMultinomial mm = (ModelMetricsMultinomial)(mm1);
            err.train_confusion_matrix = mm._cm;
            err.train_err = mm._cm.err();
            err.train_hitratio = mm._hit_ratios;
          }
          err.train_mse = mm1._mse;
          err.train_r2 = mm1.r2();
          _output.trainMetrics = mm1;
          _output.run_time = run_time;

          if (ftest != null) {
            Frame validPred = score(ftest);
            validPred.delete();
            hex.ModelMetricsSupervised mm2 = (ModelMetricsSupervised)hex.ModelMetrics.getFromDKV(this, ftest);
            if (mm2 != null) {
              if (mm2 instanceof ModelMetricsBinomial) {
                ModelMetricsBinomial mm = (ModelMetricsBinomial) (mm2);
                err.validAUC = mm._aucdata;
                err.valid_confusion_matrix = mm._cm;
                err.valid_err = mm._cm.err();
              } else if (mm2 instanceof ModelMetricsMultinomial) {
                ModelMetricsMultinomial mm = (ModelMetricsMultinomial) (mm2);
                err.valid_confusion_matrix = mm._cm;
                err.valid_err = mm._cm.err();
                err.valid_hitratio = mm._hit_ratios;
              }
              err.valid_mse = mm2._mse;
              err.valid_r2 = mm2.r2();
              _output.validMetrics = mm2;
            }
          }
        }
        if (get_params()._variable_importances) {
          if (!get_params()._quiet_mode) Log.info("Computing variable importances.");
          final float[] vi = model_info().computeVariableImportances();
          err.variable_importances = new VarImp(vi, Arrays.copyOfRange(model_info().data_info().coefNames(), 0, vi.length));
        }

        _timeLastScoreEnd = System.currentTimeMillis();
        err.scoring_time = System.currentTimeMillis() - now;
        // enlarge the error array by one, push latest score back
        if (errors == null) {
          errors = new DeepLearningScoring[]{err};
        } else {
          DeepLearningScoring[] err2 = new DeepLearningScoring[errors.length + 1];
          System.arraycopy(errors, 0, err2, 0, errors.length);
          err2[err2.length - 1] = err;
          errors = err2;
        }
        _output.errors = last_scored();
        _output.scoringHistory = createScoringHistoryTable(errors);
        _output.variableImportances = calcVarImp(last_scored().variable_importances);
        if (_output.modelSummary == null)
          _output.modelSummary = model_info.createSummaryTable();

        if (!get_params()._autoencoder) {
          // always keep a copy of the best model so far (based on the following criterion)
          if (actual_best_model_key != null && get_params()._override_with_best_model && (
                  // if we have a best_model in DKV, then compare against its error() (unless it's a different model as judged by the network size)
                  (DKV.get(actual_best_model_key) != null && (error() < DKV.get(actual_best_model_key).<DeepLearningModel>get().error() || !Arrays.equals(model_info().units, DKV.get(actual_best_model_key).<DeepLearningModel>get().model_info().units)))
                          ||
                          // otherwise, compare against our own _bestError
                          (DKV.get(actual_best_model_key) == null && error() < _bestError)
          ) ) {
            if (!get_params()._quiet_mode)
              Log.info("Error reduced from " + _bestError + " to " + error() + ". Storing best model so far under key " + actual_best_model_key.toString() + ".");
            _bestError = error();
            putMeAsBestModel(actual_best_model_key);

            // debugging check
            //if (false) {
            //  DeepLearningModel bestModel = DKV.get(actual_best_model_key).get();
            //  final Frame fr = ftest != null ? ftest : ftrain;
            //  final Frame bestPredict = bestModel.score(fr);
            //  final Frame hitRatio_bestPredict = new Frame(bestPredict);
            //  final double err3 = calcError(fr, fr.lastVec(), bestPredict, hitRatio_bestPredict, "cross-check",
            //    printme, get_params()._max_confusion_matrix_size, new hex.ConfusionMatrix2(), _output.isClassifier() && _output.nclasses() == 2 ? new AUC(null,null) : null, null);
            //  if (_output.isClassifier())
            //    assert (ftest != null ? Math.abs(err.valid_err - err3) < 1e-5 : Math.abs(err.train_err - err3) < 1e-5);
            //  else
            //    assert (ftest != null ? Math.abs(err.valid_mse - err3) < 1e-5 : Math.abs(err.train_mse - err3) < 1e-5);
            //  bestPredict.delete();
            //}
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
          if (keep_running)
            for (String s : toString().split("\n")) Log.info(s);
          if (printme) Log.info("Time taken for scoring and diagnostics: " + PrettyPrint.msecs(err.scoring_time, true));
        }
      }
      if (model_info().unstable()) {
        Log.warn(unstable_msg);
        keep_running = false;
      } else if ( (_output.isClassifier() && last_scored().train_err <= get_params()._classification_stop)
              || (!_output.isClassifier() && last_scored().train_mse <= get_params()._regression_stop) ) {
        Log.info("Achieved requested predictive accuracy on the training data. Model building completed.");
        keep_running = false;
      }
      update(job_key);
    }
    catch (Exception ex) {
      //ex.printStackTrace();
      throw new RuntimeException(ex);
//      return false;
    }
    return keep_running;
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
    sb.append("Training time: " + PrettyPrint.msecs(run_time, true)
            + ". Processed " + String.format("%,d", model_info().get_processed_total()) + " samples" + " (" + String.format("%.3f", epoch_counter) + " epochs)."
            + " Speed: " + String.format("%.3f", 1000.*model_info().get_processed_total()/run_time) + " samples/sec.\n");
    sb.append(model_info.toString());
    //sb.append(last_scored().toString());
    sb.append(_output.scoringHistory.toString());
    if (_output.variableImportances != null) {
      for (String s : Arrays.asList(_output.variableImportances.toString().split("\n")).subList(0, 12))
        sb.append(s).append("\n");
    }
    return sb.toString();
  }

  /** Make either a prediction or a reconstruction.
   * @param orig Test dataset
   * @param adaptedFr Test dataset, adapted to the model
   * @return A frame containing the prediction or reconstruction
   */
  @Override protected Frame scoreImpl(Frame orig, Frame adaptedFr, String destination_key) {
    if (!get_params()._autoencoder) {
      return super.scoreImpl(orig,adaptedFr,destination_key);
    } else {
      // Reconstruction

      final int len = model_info().data_info().fullN();
      String prefix = "reconstr_";
      assert(model_info().data_info()._responses == 0);
      String[] coefnames = model_info().data_info().coefNames();
      assert(len == coefnames.length);
      Frame adaptFrm = new Frame(adaptedFr);
      for( int c=0; c<len; c++ )
        adaptFrm.add(prefix+coefnames[c],adaptFrm.anyVec().makeZero());
      new MRTask() {
        @Override public void map( Chunk chks[] ) {
          double tmp [] = new double[_output._names.length];
          float preds[] = new float [len];
          final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
          for( int row=0; row<chks[0]._len; row++ ) {
            float p[] = score_autoencoder(chks, row, tmp, preds, neurons);
            for( int c=0; c<preds.length; c++ )
              chks[_output._names.length+c].set(row,p[c]);
          }
        }
      }.doAll(adaptFrm);

      // Return the predicted columns
      int x=_output._names.length, y=adaptFrm.numCols();
      Frame f = adaptFrm.extractFrame(x, y); //this will call vec_impl() and we cannot call the delete() below just yet

      if (destination_key != null) {
        Key k = Key.make(destination_key);
        f = new Frame(k, f.names(), f.vecs());
        DKV.put(k, f);
      }
      makeMetricBuilder(null).makeModelMetrics(this,f,Double.NaN);

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
  public Frame scoreAutoEncoder(Frame frame, Key destination_key) {
    if (!get_params()._autoencoder)
      throw new H2OIllegalArgumentException("Only for AutoEncoder Deep Learning model.", "");
    final int len = _output._names.length;
    Frame adaptFrm = new Frame(frame);
    Vec v0 = adaptFrm.anyVec().makeZero();
    Scope.enter();
    adaptTestForTrain(adaptFrm,true);
    adaptFrm.add("Reconstruction.MSE", v0);
    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        double tmp [] = new double[len];
        final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
        for( int row=0; row<chks[0]._len; row++ ) {
          for( int i=0; i<len; i++ )
            tmp[i] = chks[i].atd(row);
          //store the per-row reconstruction error (MSE) in the last column
          chks[len].set(row, score_autoencoder(tmp, null, neurons));
        }
      }
    }.doAll(adaptFrm);
    Scope.exit();

    Frame res = adaptFrm.extractFrame(len, adaptFrm.numCols());
    res = new Frame(destination_key, res.names(), res.vecs());
    DKV.put(res);
    makeMetricBuilder(null).makeModelMetrics(this, res, res.vecs()[0].mean());
    return res;
  }

  @Override public Frame score(Frame fr, String destination_key) {
    if (!_parms._autoencoder)
      return super.score(fr, destination_key);
    else {
      Frame adaptFr = new Frame(fr);
      adaptTestForTrain(adaptFr, true);   // Adapt
      Frame output = scoreImpl(fr, adaptFr, destination_key); // Score

      Vec[] vecs = adaptFr.vecs();
      for (int i = 0; i < vecs.length; i++)
        if (fr.find(vecs[i]) != -1) // Exists in the original frame?
          vecs[i] = null;            // Do not delete it
      adaptFr.delete();
      return output;
    }
  }

   /**
   * Score auto-encoded reconstruction (on-the-fly, without allocating the reconstruction as done in Frame score(Frame fr))
   * @param frame Original data (can contain response, will be ignored)
   * @return Frame containing one Vec with reconstruction error (MSE) of each reconstructed row, caller is responsible for deletion
   */
  public Frame scoreDeepFeatures(Frame frame, final int layer) {
    if (layer < 0 || layer >= model_info().get_params()._hidden.length)
      throw new H2OIllegalArgumentException("hidden layer (index) to extract must be between " + 0 + " and " + (model_info().get_params()._hidden.length-1),"");
    final int len = _output.nfeatures();
    Vec resp = null;
    if (isSupervised()) {
      int ridx = frame.find(_output.responseName());
      if (ridx != -1) { // drop the response for scoring!
        frame = new Frame(frame);
        resp = frame.vecs()[ridx];
        frame.remove(ridx);
      }
    }
    Frame adaptFrm = new Frame(frame);
    //create new features, will be dense
    final int features = model_info().get_params()._hidden[layer];
    Vec[] vecs = adaptFrm.anyVec().makeZeros(features);

    Scope.enter();
    adaptTestForTrain(adaptFrm,true);
    for (int j=0; j<features; ++j) {
      adaptFrm.add("DF.C" + (j+1), vecs[j]);
    }
    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        double tmp [] = new double[len];
        float df[] = new float [features];
        final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
        for( int row=0; row<chks[0]._len; row++ ) {
          for( int i=0; i<len; i++ )
            tmp[i] = chks[i].atd(row);
          ((Neurons.Input)neurons[0]).setInput(-1, tmp);
          DeepLearningTask.step(-1, neurons, model_info, false, null);
          float[] out = neurons[layer+1]._a.raw(); //extract the layer-th hidden feature
          for( int c=0; c<df.length; c++ )
            chks[_output._names.length+c].set(row,out[c]);
        }
      }
    }.doAll(adaptFrm);

    // Return just the output columns
    int x=_output._names.length, y=adaptFrm.numCols();
    Frame ret = adaptFrm.extractFrame(x, y);
    if (resp != null) ret.prepend(_output.responseName(), resp);
    Scope.exit();
    return ret;
  }


  // Make (potentially expanded) reconstruction
  private float[] score_autoencoder(Chunk[] chks, int row_in_chunk, double[] tmp, float[] preds, Neurons[] neurons) {
    assert(get_params()._autoencoder);
    assert(tmp.length == _output._names.length);
    for( int i=0; i<tmp.length; i++ )
      tmp[i] = chks[i].atd(row_in_chunk);
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
    assert(model_info().get_params()._autoencoder);
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

  /**
   * Compute quantile-based threshold (in reconstruction error) to find outliers
   * @param mse Vector containing reconstruction errors
   * @param quantile Quantile for cut-off
   * @return Threshold in MSE value for a point to be above the quantile
   */
  double calcOutlierThreshold(Vec mse, double quantile) {
    Frame mse_frame = new Frame(Key.make(), new String[]{"Reconstruction.MSE"}, new Vec[]{mse});
    DKV.put(mse_frame._key, mse_frame);

    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    parms._train = mse_frame._key;
    parms._probs = new double[]{quantile};
    Quantile job = new Quantile(parms).trainModel();
    QuantileModel kmm = job.get();
    job.remove();
    double q = kmm._output._quantiles[0][0];
    kmm.delete();
    DKV.remove(mse_frame._key);
    return q;
  }

  // helper to push this model to another key (for keeping good models)
  private void putMeAsBestModel(Key bestModelKey) {
    DeepLearningModel bestModel = new DeepLearningModel(bestModelKey, this, true, model_info().data_info());
    DKV.put(bestModel._key, bestModel);
    assert (DKV.get(bestModelKey) != null);
    assert (bestModel.compareTo(this) <= 0);
    assert (((DeepLearningModel) DKV.get(bestModelKey).get()).error() == _bestError);
  }

  void delete_best_model( ) {
    if (actual_best_model_key != null && actual_best_model_key != _key) DKV.remove(actual_best_model_key);
  }

  void delete_xval_models( ) {
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

