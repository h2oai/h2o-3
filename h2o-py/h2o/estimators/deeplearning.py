from .estimator_base import H2OEstimator


class H2ODeepLearningEstimator(H2OEstimator):
  def __init__(self, model_id=None, overwrite_with_best_model=None, checkpoint=None,
               use_all_factor_levels=None, activation=None, hidden=None, epochs=None,
               train_samples_per_iteration=None, seed=None, adaptive_rate=None, rho=None,
               epsilon=None, rate=None, rate_annealing=None, rate_decay=None,
               momentum_start=None, momentum_ramp=None, momentum_stable=None,
               nesterov_accelerated_gradient=None, input_dropout_ratio=None,
               hidden_dropout_ratios=None, l1=None, l2=None, max_w2=None,
               initial_weight_distribution=None, initial_weight_scale=None, loss=None,
               distribution=None, tweedie_power=None, score_interval=None,
               score_training_samples=None, score_validation_samples=None,
               score_duty_cycle=None, classification_stop=None, regression_stop=None,
               quiet_mode=None, max_confusion_matrix_size=None, max_hit_ratio_k=None,
               balance_classes=None, class_sampling_factors=None,
               max_after_balance_size=None, score_validation_sampling=None,
               diagnostics=None, variable_importances=None, fast_mode=None,
               ignore_const_cols=None, force_load_balance=None,
               replicate_training_data=None, single_node_mode=None,
               shuffle_training_data=None, sparse=None, col_major=None,
               average_activation=None, sparsity_beta=None, max_categorical_features=None,
               reproducible=None, export_weights_and_biases=None, nfolds=None,
               fold_assignment=None, keep_cross_validation_predictions=None):
    """
    Build a supervised Deep Learning model
    Performs Deep Learning neural networks on an H2OFrame

    Parameters
    ----------
    model_id : str, optional
      The unique id assigned to the resulting model. If none is given, an id will
      automatically be generated.
    overwrite_with_best_model : bool
      If True, overwrite the final model with the best model found during training.
      Defaults to True.
    checkpoint : H2ODeepLearningModel, optional
      Model checkpoint (either key or H2ODeepLearningModel) to resume training with.
    use_all_factor_levels : bool
      Use all factor levels of categorical variance. Otherwise the first factor level is
      omitted (without loss of accuracy). Useful for variable importances and auto-enabled
      for autoencoder.
    activation : str
      A string indicating the activation function to use.
      Must be either "Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout",
      "Maxout", or "MaxoutWithDropout"
    hidden : list
      Hidden layer sizes (e.g. [100,100])
    epochs : float
      How many times the dataset should be iterated (streamed), can be fractional
    train_samples_per_iteration : int
      Number of training samples (globally) per MapReduce iteration.
      Special values are: 0 one epoch; -1 all available data
      (e.g., replicated training data); or -2 auto-tuning (default)
    seed : int
      Seed for random numbers (affects sampling) - Note: only reproducible when
      running single threaded
    adaptive_rate : bool
      Adaptive learning rate (ADAELTA)
    rho : float
      Adaptive learning rate time decay factor (similarity to prior updates)
    epsilon : float
      Adaptive learning rate parameter, similar to learn rate annealing during initial
      training phase. Typical values are between 1.0e-10 and 1.0e-4
    rate : float
      Learning rate (higher => less stable, lower => slower convergence)
    rate_annealing : float
      Learning rate annealing: \eqn{(rate)/(1 + rate_annealing*samples)
    rate_decay : float
      Learning rate decay factor between layers (N-th layer: \eqn{rate*\alpha^(N-1))
    momentum_start : float
      Initial momentum at the beginning of training (try 0.5)
    momentum_ramp : float
      Number of training samples for which momentum increases
    momentum_stable : float
      Final momentum after the amp is over (try 0.99)
    nesterov_accelerated_gradient : bool
      Logical. Use Nesterov accelerated gradient (recommended)
    input_dropout_ratio : float
      A fraction of the features for each training row to be omitted from training in
      order to improve generalization (dimension sampling).
    hidden_dropout_ratios : float
      Input layer dropout ratio (can improve generalization) specify one value per hidden
      ayer, defaults to 0.5
    l1 : float
      L1 regularization (can add stability and improve generalization,
      causes many weights to become 0)
    l2 : float
      L2 regularization (can add stability and improve generalization,
      causes many weights to be small)
    max_w2 : float
      Constraint for squared sum of incoming weights per unit (e.g. Rectifier)
    initial_weight_distribution : str
      Can be "Uniform", "UniformAdaptive", or "Normal"
    initial_weight_scale : str
      Uniform: -value ... value, Normal: stddev
    loss : str
      Loss function: "Automatic", "CrossEntropy" (for classification only),
      "Quadratic", "Absolute" (experimental) or "Huber" (experimental)
    distribution : str
       A character string. The distribution function of the response.
       Must be "AUTO", "bernoulli", "multinomial", "poisson", "gamma",
       "tweedie", "laplace", "huber" or "gaussian"
    tweedie_power : float
      Tweedie power (only for Tweedie distribution, must be between 1 and 2)
    score_interval : int
      Shortest time interval (in secs) between model scoring
    score_training_samples : int
      Number of training set samples for scoring (0 for all)
    score_validation_samples : int
      Number of validation set samples for scoring (0 for all)
    score_duty_cycle : float
      Maximum duty cycle fraction for scoring (lower: more training, higher: more scoring)
    classification_stop : float
      Stopping criterion for classification error fraction on training data
      (-1 to disable)
    regression_stop : float
      Stopping criterion for regression error (MSE) on training data (-1 to disable)
    quiet_mode : bool
      Enable quiet mode for less output to standard output
    max_confusion_matrix_size : int
      Max. size (number of classes) for confusion matrices to be shown
    max_hit_ratio_k : float
      Max number (top K) of predictions to use for hit ratio computation(for multi-class only, 0 to disable)
    balance_classes : bool
      Balance training data class counts via over/under-sampling (for imbalanced data)
    class_sampling_factors : list
      Desired over/under-sampling ratios per class (in lexicographic order).
      If not specified, sampling factors will be automatically computed to obtain class
      balance during training. Requires balance_classes.
    max_after_balance_size : float
      Maximum relative size of the training data after balancing class counts
      (can be less than 1.0)
    score_validation_sampling :
      Method used to sample validation dataset for scoring
    diagnostics : bool
      Enable diagnostics for hidden layers
    variable_importances : bool
      Compute variable importances for input features (Gedeon method) - can be slow
      for large networks)
    fast_mode : bool
      Enable fast mode (minor approximations in back-propagation)
    ignore_const_cols : bool
      Ignore constant columns (no information can be gained anyway)
    force_load_balance : bool
      Force extra load balancing to increase training speed for small datasets
      (to keep all cores busy)
    replicate_training_data : bool
      Replicate the entire training dataset onto every node for faster training
    single_node_mode : bool
      Run on a single node for fine-tuning of model parameters
    shuffle_training_data : bool
      Enable shuffling of training data (recommended if training data is replicated and
      train_samples_per_iteration is close to \eqn{numRows*numNodes
    sparse : bool
      Sparse data handling (Experimental)
    col_major : bool
      Use a column major weight matrix for input layer. Can speed up forward propagation,
      but might slow down back propagation (Experimental)
    average_activation : float
      Average activation for sparse auto-encoder (Experimental)
    sparsity_beta : bool
      Sparsity regularization (Experimental)
    max_categorical_features : int
      Max. number of categorical features, enforced via hashing Experimental)
    reproducible : bool
      Force reproducibility on small data (will be slow - only uses 1 thread)
    export_weights_and_biases : bool
      Whether to export Neural Network weights and biases to H2O Frames"
    nfolds : int, optional
      Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
    fold_assignment : str
      Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
    keep_cross_validation_predictions : bool
      Whether to keep the predictions of the cross-validation models

    Returns
    -------
      Return a new classifier or regression model.
    """
    super(H2ODeepLearningEstimator, self).__init__()
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self.parms["algo"] = "deeplearning"
    self.parms["autoencoder"] = isinstance(self, H2OAutoEncoderEstimator)


class H2OAutoEncoderEstimator(H2ODeepLearningEstimator):
  pass