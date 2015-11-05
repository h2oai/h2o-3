package hex.deeplearning;

import hex.Distribution;
import hex.Model;
import hex.ScoreKeeper;
import water.H2O;
import static water.H2O.technote;
import water.exceptions.H2OIllegalArgumentException;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Deep Learning Parameters
 */
public class DeepLearningParameters extends Model.Parameters {
  @Override protected double defaultStoppingTolerance() { return 0; }
  public DeepLearningParameters() {
    super();
    _stopping_rounds = 5;
  }

  @Override
  public double missingColumnsType() {
    return _sparse ? 0 : Double.NaN;
  }

  /**
   * If enabled, store the best model under the destination key of this model at the end of training.
   * Only applicable if training is not cancelled.
   */
  public boolean _overwrite_with_best_model = true;

  public boolean _autoencoder = false;

  public boolean _use_all_factor_levels = true;

/*Neural Net Topology*/
  /**
   * The activation function (non-linearity) to be used the neurons in the hidden layers.
   * Tanh: Hyperbolic tangent function (same as scaled and shifted sigmoid).
   * Rectifier: Chooses the maximum of (0, x) where x is the input value.
   * Maxout: Choose the maximum coordinate of the input vector.
   * With Dropout: Zero out a random user-given fraction of the
   * incoming weights to each hidden layer during training, for each
   * training row. This effectively trains exponentially many models at
   * once, and can improve generalization.
   */
  public Activation _activation = Activation.Rectifier;

  /**
   * The number and size of each hidden layer in the model.
   * For example, if a user specifies "100,200,100" a model with 3 hidden
   * layers will be produced, and the middle hidden layer will have 200
   * neurons.
   */
  public int[] _hidden = new int[]{200, 200};

  /**
   * The number of passes over the training dataset to be carried out.
   * It is recommended to start with lower values for initial experiments.
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

  public double _target_ratio_comm_to_comp = 0.05;

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
  public long _seed = RandomUtils.getRNG(System.nanoTime()).nextLong();

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
   * A regularization method that constrains the sum of the squared
   * weights. This method introduces bias into parameter estimates, but
   * frequently produces substantial gains in modeling as estimate variance is
   * reduced.
   */
  public double _l2 = 0.0;

  /**
   * A maximum on the sum of the squared incoming weights into
   * any one neuron. This tuning parameter is especially useful for unbound
   * activation functions such as Rectifier.
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
   * is close to N times the dataset size, where all nodes train with (almost) all
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

  public boolean _export_weights_and_biases = false;

  public boolean _elastic_averaging = false;
  public double _elastic_averaging_moving_rate = 0.9;
  public double _elastic_averaging_regularization = 1e-3;

  // stochastic gradient descent: mini-batch size = 1
  // batch gradient descent: mini-batch size = # training rows
  public int _mini_batch_size = 1;

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
   * Absolute, Quadratic, Huber for regression
   * Absolute, Quadratic, Huber or CrossEntropy for classification
   */
  public enum Loss {
    Automatic, Quadratic, CrossEntropy, Huber, Absolute
  }

  /**
   * Validate model parameters
   * @param dl DL Model Builder (Driver)
   * @param expensive (whether or not this is the "final" check)
   */
  void validate(DeepLearning dl, boolean expensive) {
    dl.hide("_score_each_iteration", "Not used by Deep Learning.");
    boolean classification = expensive || dl.nclasses() != 0 ? dl.isClassifier() : _loss == Loss.CrossEntropy;
    if (_hidden == null || _hidden.length == 0) dl.error("_hidden", "There must be at least one hidden layer.");

    for (int h : _hidden) if (h <= 0) dl.error("_hidden", "Hidden layer size must be positive.");
    if (_mini_batch_size < 1)
      dl.error("_mini_batch_size", "Mini-batch size must be >= 1");
    if (_mini_batch_size > 1)
      dl.error("_mini_batch_size", "Mini-batch size > 1 is not yet supported.");
    if (!_diagnostics)
      dl.warn("_diagnostics", "Deprecated option: Diagnostics are always enabled.");

    if (!_autoencoder) {
      if (_valid == null)
        dl.hide("_score_validation_samples", "score_validation_samples requires a validation frame.");

      if (classification) {
        dl.hide("_regression_stop", "regression_stop is used only with regression.");
      } else {
        dl.hide("_classification_stop", "classification_stop is used only with classification.");
//          dl.hide("_max_hit_ratio_k", "max_hit_ratio_k is used only with classification.");
//          dl.hide("_balance_classes", "balance_classes is used only with classification.");
      }
//        if( !classification || !_balance_classes )
//          dl.hide("_class_sampling_factors", "class_sampling_factors requires both classification and balance_classes.");
      if (!classification && _valid != null || _valid == null)
        dl.hide("_score_validation_sampling", "score_validation_sampling requires classification and a validation frame.");
    } else {
      if (_nfolds > 1) {
        dl.error("_nfolds", "N-fold cross-validation is not supported for Autoencoder.");
      }
    }

    if (_activation != Activation.TanhWithDropout && _activation != Activation.MaxoutWithDropout && _activation != Activation.RectifierWithDropout) {
      dl.hide("_hidden_dropout_ratios", "hidden_dropout_ratios requires a dropout activation function.");
    }
    if (_hidden_dropout_ratios != null) {
      if (_hidden_dropout_ratios.length != _hidden.length) {
        dl.error("_hidden_dropout_ratios", "Must have " + _hidden.length + " hidden layer dropout ratios.");
      } else if (_activation != Activation.TanhWithDropout && _activation != Activation.MaxoutWithDropout && _activation != Activation.RectifierWithDropout) {
        if (!_quiet_mode)
          dl.hide("_hidden_dropout_ratios", "Ignoring hidden_dropout_ratios because a non-dropout activation function was specified.");
      } else if (ArrayUtils.maxValue(_hidden_dropout_ratios) >= 1 || ArrayUtils.minValue(_hidden_dropout_ratios) < 0) {
        dl.error("_hidden_dropout_ratios", "Hidden dropout ratios must be >= 0 and <1.");
      }
    }
    if (_input_dropout_ratio < 0 || _input_dropout_ratio >= 1)
      dl.error("_input_dropout_ratio", "Input dropout must be >= 0 and <1.");
    if (_score_duty_cycle < 0 || _score_duty_cycle > 1)
      dl.error("_score_duty_cycle", "Score duty cycle must be >= 0 and <=1.");
    if (_l1 < 0)
      dl.error("_l1", "L1 penalty must be >= 0.");
    if (_l2 < 0)
      dl.error("_l2", "L2 penalty must be >= 0.");
    if (H2O.CLOUD.size() == 1 && _replicate_training_data)
      dl.hide("_replicate_training_data", "replicate_training_data is only valid with cloud size greater than 1.");
    if (_single_node_mode && (H2O.CLOUD.size() == 1 || !_replicate_training_data))
      dl.hide("_single_node_mode", "single_node_mode is only used with multi-node operation with replicated training data.");
    if (H2O.ARGS.client && _single_node_mode)
      dl.error("_single_node_mode", "Cannot run on a single node in client mode");
    if (_autoencoder)
      dl.hide("_use_all_factor_levels", "use_all_factor_levels is mandatory in combination with autoencoder.");
    if (_nfolds != 0)
      dl.hide("_overwrite_with_best_model", "overwrite_with_best_model is unsupported in combination with n-fold cross-validation.");
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
    if (_initial_weight_distribution == InitialWeightDistribution.UniformAdaptive) {
      dl.hide("_initial_weight_scale", "initial_weight_scale is not used if initial_weight_distribution == UniformAdaptive.");
    }
    if (_loss == null) {
      if (expensive || dl.nclasses() != 0) {
        dl.error("_loss", "Loss function must be specified. Try CrossEntropy for categorical response (classification), Quadratic, Absolute or Huber for numerical response (regression).");
      }
      //otherwise, we might not know whether classification=true or false (from R, for example, the training data isn't known when init(false) is called).
    } else {
      if (_autoencoder && _loss == Loss.CrossEntropy)
        dl.error("_loss", "Cannot use CrossEntropy loss for auto-encoder.");
      if (!classification && _loss == Loss.CrossEntropy)
        dl.error("_loss", technote(2, "For CrossEntropy loss, the response must be categorical."));
    }
    if (!classification && _loss == Loss.CrossEntropy)
      dl.error("_loss", "For CrossEntropy loss, the response must be categorical. Either select Automatic, Quadratic, Absolute or Huber loss for regression, or use a categorical response.");
    if (classification) {
      switch(_distribution) {
        case gaussian:
        case huber:
        case laplace:
        case tweedie:
        case gamma:
        case poisson:
          dl.error("_distribution", technote(2, _distribution  + " distribution is not allowed for classification."));
          break;
        case AUTO:
        case bernoulli:
        case multinomial:
        default:
          //OK
          break;
      }
    } else {
      switch(_distribution) {
        case multinomial:
        case bernoulli:
          dl.error("_distribution", technote(2, _distribution  + " distribution is not allowed for regression."));
          break;
        case tweedie:
        case gamma:
        case poisson:
          if (_loss != Loss.Automatic)
            dl.error("_distribution", "Only Automatic loss (deviance) is allowed for " + _distribution + " distribution.");
          break;
        case laplace:
          if (_loss != Loss.Absolute && _loss != Loss.Automatic)
            dl.error("_distribution", "Only Automatic or Absolute loss is allowed for " + _distribution + " distribution.");
          break;
        case huber:
          if (_loss != Loss.Huber && _loss != Loss.Automatic)
            dl.error("_distribution", "Only Automatic or Huber loss is allowed for " + _distribution + " distribution.");
          break;
        case AUTO:
        case gaussian:
        default:
          //OK
          break;
      }
    }
    if (expensive) dl.checkDistributions();

    if (_score_training_samples < 0)
      dl.error("_score_training_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
    if (_score_validation_samples < 0)
      dl.error("_score_validation_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
    if (_autoencoder && _sparsity_beta > 0) {
      if (_activation == Activation.Tanh || _activation == Activation.TanhWithDropout) {
        if (_average_activation >= 1 || _average_activation <= -1)
          dl.error("_average_activation", "Tanh average activation must be in (-1,1).");
      } else if (_activation == Activation.Rectifier || _activation == Activation.RectifierWithDropout) {
        if (_average_activation <= 0)
          dl.error("_average_activation", "Rectifier average activation must be positive.");
      }
    }
    if (!_autoencoder && _sparsity_beta != 0)
      dl.error("_sparsity_beta", "Sparsity beta can only be used for autoencoder.");
    if (classification && dl.hasOffsetCol())
      dl.error("_offset_column", "Offset is only supported for regression.");

    // reason for the error message below is that validation might not have the same horizontalized features as the training data (or different order)
    if (_autoencoder && _activation == Activation.Maxout)
      dl.error("_activation", "Maxout activation is not supported for auto-encoder.");
    if (_max_categorical_features < 1)
      dl.error("_max_categorical_features", "max_categorical_features must be at least 1.");
    if (_col_major)
      dl.error("_col_major", "Deprecated: Column major data handling not supported anymore - not faster.");
    if (!_sparse && _col_major) {
      dl.error("_col_major", "Cannot use column major storage for non-sparse data handling.");
    }
    if (_sparse && _elastic_averaging) {
      dl.error("_elastic_averaging", "Cannot use elastic averaging for sparse data handling.");
    }
    if (expensive) {
      if (!classification && _balance_classes) {
        dl.error("_balance_classes", "balance_classes requires classification.");
      }
      if (_class_sampling_factors != null && !_balance_classes) {
        dl.error("_class_sampling_factors", "class_sampling_factors requires balance_classes to be enabled.");
      }
      if (_replicate_training_data && null != train() && train().byteSize() > 1e10) {
        dl.error("_replicate_training_data", "Compressed training dataset takes more than 10 GB, cannot run with replicate_training_data.");
      }
    }
    if (!_elastic_averaging) {
      dl.hide("_elastic_averaging_moving_rate", "Elastic averaging is required for this parameter.");
      dl.hide("_elastic_averaging_regularization", "Elastic averaging is required for this parameter.");
    } else {
      if (_elastic_averaging_moving_rate > 1 || _elastic_averaging_moving_rate < 0)
        dl.error("_elastic_averaging_moving_rate", "Elastic averaging moving rate must be between 0 and 1.");
      if (_elastic_averaging_regularization < 0)
        dl.error("_elastic_averaging_regularization", "Elastic averaging regularization strength must be >= 0.");
    }
    if (_autoencoder && _stopping_metric != ScoreKeeper.StoppingMetric.AUTO && _stopping_metric != ScoreKeeper.StoppingMetric.MSE) {
      dl.error("_stopping_metric", "Stopping metric must either be AUTO or MSE for autoencoder.");
    }
  }

  static class Sanity {
    // the following parameters can be modified when restarting from a checkpoint
    transient static private final String[] cp_modifiable = new String[]{
            "_seed",
            "_checkpoint",
            "_epochs",
            "_score_interval",
            "_train_samples_per_iteration",
            "_target_ratio_comm_to_comp",
            "_score_duty_cycle",
            "_score_training_samples",
            "_score_validation_samples",
            "_score_validation_sampling",
            "_classification_stop",
            "_regression_stop",
            "_stopping_rounds",
            "_stopping_metric",
            "_stopping_tolerance",
            "_quiet_mode",
            "_max_confusion_matrix_size",
            "_max_hit_ratio_k",
            "_diagnostics",
            "_variable_importances",
            "_initial_weight_distribution", //will be ignored anyway
            "_initial_weight_scale", //will be ignored anyway
            "_force_load_balance",
            "_replicate_training_data",
            "_shuffle_training_data",
            "_single_node_mode",
            "_fast_mode",
            // Allow modification of the regularization parameters after a checkpoint restart
            "_l1",
            "_l2",
            "_max_w2",
            "_input_dropout_ratio",
            "_hidden_dropout_ratios",
            "_loss",
            "_overwrite_with_best_model",
            "_missing_values_handling",
            "_average_activation",
            "_reproducible",
            "_export_weights_and_biases",
            "_elastic_averaging",
            "_elastic_averaging_moving_rate",
            "_elastic_averaging_regularization",
            "_mini_batch_size"
    };

    // the following parameters must not be modified when restarting from a checkpoint
    transient static private final String[] cp_not_modifiable = new String[]{
            "_drop_na20_cols",
            "_response_column",
            "_activation",
            "_use_all_factor_levels",
            "_adaptive_rate",
            "_autoencoder",
            "_rho",
            "_epsilon",
            "_sparse",
            "_sparsity_beta",
            "_col_major",
            "_rate",
            "_rate_annealing",
            "_rate_decay",
            "_momentum_start",
            "_momentum_ramp",
            "_momentum_stable",
            "_nesterov_accelerated_gradient",
            "_ignore_const_cols",
            "_max_categorical_features",
            "_nfolds",
            "_distribution",
            "_tweedie_power"
    };

    static void checkCompleteness() {
      for (Field f : DeepLearningParameters.class.getDeclaredFields())
        if (!ArrayUtils.contains(cp_not_modifiable, f.getName())
                &&
                !ArrayUtils.contains(cp_modifiable, f.getName())
                ) {
          if (f.getName().equals("_hidden")) continue;
          if (f.getName().equals("_ignored_columns")) continue;
          throw H2O.unimpl("Please add " + f.getName() + " to either cp_modifiable or cp_not_modifiable");
        }
    }

    /**
     * Check that checkpoint continuation is possible
     *
     * @param oldP old DL parameters (from checkpoint)
     * @param newP new DL parameters (user-given, to restart from checkpoint)
     */
    static void checkpoint(final DeepLearningParameters oldP, final DeepLearningParameters newP) {
      checkCompleteness();
      if (newP._nfolds != 0)
        throw new UnsupportedOperationException("nfolds must be 0: Cross-validation is not supported during checkpoint restarts.");
      if ((newP._valid == null) != (oldP._valid == null)) {
        throw new H2OIllegalArgumentException("Presence of validation dataset must agree with the checkpointed model.");
      }
      if (!newP._autoencoder && (newP._response_column == null || !newP._response_column.equals(oldP._response_column))) {
        throw new H2OIllegalArgumentException("Response column (" + newP._response_column + ") is not the same as for the checkpointed model: " + oldP._response_column);
      }
      if (!Arrays.equals(newP._hidden, oldP._hidden)) {
        throw new H2OIllegalArgumentException("Hidden layers (" + Arrays.toString(newP._hidden) + ") is not the same as for the checkpointed model: " + Arrays.toString(oldP._hidden));
      }
      if (!Arrays.equals(newP._ignored_columns, oldP._ignored_columns)) {
        throw new H2OIllegalArgumentException("Ignored columns must be the same as for the checkpointed model.");
      }

      //compare the user-given parameters before and after and check that they are not changed
      for (Field fBefore : oldP.getClass().getFields()) {
        if (ArrayUtils.contains(cp_not_modifiable, fBefore.getName())) {
          for (Field fAfter : newP.getClass().getFields()) {
            if (fBefore.equals(fAfter)) {
              try {
                if (fAfter.get(newP) == null || fBefore.get(oldP) == null || !fBefore.get(oldP).toString().equals(fAfter.get(newP).toString())) { // if either of the two parameters is null, skip the toString()
                  if (fBefore.get(oldP) == null && fAfter.get(newP) == null)
                    continue; //if both parameters are null, we don't need to do anything
                  throw new H2OIllegalArgumentException("Cannot change parameter: '" + fBefore.getName() + "': " + fBefore.get(oldP) + " -> " + fAfter.get(newP));
                }
              } catch (IllegalAccessException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    }

    /**
     * Update the parameters from checkpoint to user-specified
     *
     * @param actualNewP parameters in the model (that will be trained from a checkpoint restart)
     * @param newP       user-specified parameters
     */
    static void update(DeepLearningParameters actualNewP, DeepLearningParameters newP, int nClasses) {
      for (Field fBefore : actualNewP.getClass().getDeclaredFields()) {
        if (ArrayUtils.contains(cp_modifiable, fBefore.getName())) {
          for (Field fAfter : newP.getClass().getDeclaredFields()) {
            if (fBefore.equals(fAfter)) {
              try {
                if (fAfter.get(newP) == null || fBefore.get(actualNewP) == null || !fBefore.get(actualNewP).toString().equals(fAfter.get(newP).toString())) { // if either of the two parameters is null, skip the toString()
                  if (fBefore.get(actualNewP) == null && fAfter.get(newP) == null)
                    continue; //if both parameters are null, we don't need to do anything
                  if (!actualNewP._quiet_mode)
                    Log.info("Applying user-requested modification of '" + fBefore.getName() + "': " + fBefore.get(actualNewP) + " -> " + fAfter.get(newP));
                  fBefore.set(actualNewP, fAfter.get(newP));
                }
              } catch (IllegalAccessException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
      // update parameters in place to set defaults etc.
      modifyParms(actualNewP, actualNewP, nClasses);
    }

    /**
     * Take user-given parameters and turn them into usable, fully populated parameters (e.g., to be used by Neurons during training)
     *
     * @param fromParms      raw user-given parameters from the REST API
     * @param toParms        modified set of parameters, with defaults filled in
     * @param nClasses       number of classes (1 for regression or autoencoder)
     */
    static void modifyParms(DeepLearningParameters fromParms, DeepLearningParameters toParms, int nClasses) {
      if (fromParms._hidden_dropout_ratios == null) {
        if (fromParms._activation == Activation.TanhWithDropout
                || fromParms._activation == Activation.MaxoutWithDropout
                || fromParms._activation == Activation.RectifierWithDropout) {
          toParms._hidden_dropout_ratios = new double[fromParms._hidden.length];
          if (!fromParms._quiet_mode)
            Log.info("_hidden_dropout_ratios: Automatically setting all hidden dropout ratios to 0.5.");
          Arrays.fill(toParms._hidden_dropout_ratios, 0.5);
        }
      } else {
        toParms._hidden_dropout_ratios = fromParms._hidden_dropout_ratios.clone();
      }
      if (H2O.CLOUD.size() == 1 && fromParms._replicate_training_data) {
        if (!fromParms._quiet_mode)
          Log.info("_replicate_training_data: Disabling replicate_training_data on 1 node.");
        toParms._replicate_training_data = false;
      }
      if (fromParms._single_node_mode && (H2O.CLOUD.size() == 1 || !fromParms._replicate_training_data)) {
        if (!fromParms._quiet_mode)
          Log.info("_single_node_mode: Disabling single_node_mode (only for multi-node operation with replicated training data).");
        toParms._single_node_mode = false;
      }
      if (!fromParms._use_all_factor_levels && fromParms._autoencoder) {
        if (!fromParms._quiet_mode)
          Log.info("_use_all_factor_levels: Automatically enabling all_factor_levels for auto-encoders.");
        toParms._use_all_factor_levels = true;
      }
      if (fromParms._overwrite_with_best_model && fromParms._nfolds != 0) {
        if (!fromParms._quiet_mode)
          Log.info("_overwrite_with_best_model: Disabling overwrite_with_best_model in combination with n-fold cross-validation.");
        toParms._overwrite_with_best_model = false;
      }
      if (fromParms._adaptive_rate) {
        if (!fromParms._quiet_mode)
          Log.info("_adaptive_rate: Using automatic learning rate. Ignoring the following input parameters: "
                  + "rate, rate_decay, rate_annealing, momentum_start, momentum_ramp, momentum_stable.");
        toParms._rate = 0;
        toParms._rate_decay = 0;
        toParms._rate_annealing = 0;
        toParms._momentum_start = 0;
        toParms._momentum_ramp = 0;
        toParms._momentum_stable = 0;
      } else {
        if (!fromParms._quiet_mode)
          Log.info("_adaptive_rate: Using manual learning rate. Ignoring the following input parameters: "
                  + "rho, epsilon.");
        toParms._rho = 0;
        toParms._epsilon = 0;
      }
      if (fromParms._activation == Activation.Rectifier || fromParms._activation == Activation.RectifierWithDropout) {
        if (fromParms._max_w2 == Float.POSITIVE_INFINITY) {
          if (!fromParms._quiet_mode)
            Log.info("_max_w2: Automatically setting max_w2 to 1000 to keep (unbounded) Rectifier activation in check.");
          toParms._max_w2 = 1e3f;
        }
      }
      if (fromParms._nfolds != 0) {
        if (fromParms._overwrite_with_best_model) {
          if (!fromParms._quiet_mode)
            Log.info("_overwrite_with_best_model: Automatically disabling overwrite_with_best_model, since the final model is the only scored model with n-fold cross-validation.");
          toParms._overwrite_with_best_model = false;
        }
      }
      if (fromParms._autoencoder && fromParms._stopping_metric == ScoreKeeper.StoppingMetric.AUTO) {
        if (!fromParms._quiet_mode)
          Log.info("_stopping_metric: Automatically setting stopping_metric to MSE for autoencoder.");
        toParms._stopping_metric = ScoreKeeper.StoppingMetric.MSE;
      }

      // Automatically set the distribution
      if (fromParms._distribution == Distribution.Family.AUTO) {
        // For classification, allow AUTO/bernoulli/multinomial with losses CrossEntropy/Quadratic/Huber/Absolute
        if (nClasses > 1) {
          toParms._distribution = nClasses == 2 ? Distribution.Family.bernoulli : Distribution.Family.multinomial;
        }
        else {
          //regression/autoencoder
          switch(fromParms._loss) {
            case Automatic:
            case Quadratic:
              toParms._distribution = Distribution.Family.gaussian;
              break;
            case Absolute:
              toParms._distribution = Distribution.Family.laplace;
              break;
            case Huber:
              toParms._distribution = Distribution.Family.huber;
              break;
            default:
              throw H2O.unimpl();
          }
        }
      }

      if (fromParms._loss == Loss.Automatic) {
        switch (toParms._distribution) {
          case gaussian:
            toParms._loss = Loss.Quadratic;
            break;
          case laplace:
            toParms._loss = Loss.Absolute;
            break;
          case huber:
            toParms._loss = Loss.Huber;
            break;
          case multinomial:
          case bernoulli:
            toParms._loss = Loss.CrossEntropy;
            break;
          case tweedie:
          case poisson:
          case gamma:
            toParms._loss = Loss.Automatic; //deviance
            break;
          default:
            throw H2O.unimpl();
        }
      }
      if (fromParms._reproducible) {
        if (!fromParms._quiet_mode)
          Log.info("_reproducibility: Automatically enabling force_load_balancing, disabling single_node_mode and replicate_training_data\n"
                  + "and setting train_samples_per_iteration to -1 to enforce reproducibility.");
        toParms._force_load_balance = true;
        toParms._single_node_mode = false;
        toParms._train_samples_per_iteration = -1;
        toParms._replicate_training_data = false; //there's no benefit from having multiple nodes compute the exact same thing, and then average it back to the same
      }
    }
  }

}
