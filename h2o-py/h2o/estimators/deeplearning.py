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
               fold_assignment=None, keep_cross_validation_predictions=None,
               stopping_rounds=None, stopping_metric=None, stopping_tolerance=None):
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
    stopping_rounds : int
      Early stopping based on convergence of stopping_metric.
      Stop if simple moving average of length k of the stopping_metric does not improve
      (by stopping_tolerance) for k=stopping_rounds scoring events.
      Can only trigger after at least 2k scoring events. Use 0 to disable.
    stopping_metric : str
      Metric to use for convergence checking, only for _stopping_rounds > 0
      Can be one of "AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification".
    stopping_tolerance : float
      Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)
    quiet_mode : bool
      Enable quiet mode for less output to standard output
    max_confusion_matrix_size : int
      Max. size (number of classes) for confusion matrices to be shown
    max_hit_ratio_k : float
      Max number (top K) of predictions to use for hit ratio computation
      (for multi-class only, 0 to disable)
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
      Number of folds for cross-validation. If nfolds >= 2, then validation must remain
      empty.
    fold_assignment : str
      Cross-validation fold assignment scheme, if fold_column is not specified
      Must be "AUTO", "Random" or "Modulo"
    keep_cross_validation_predictions : bool
      Whether to keep the predictions of the cross-validation models

    Examples
    --------
      >>> import h2o as ml
      >>> from h2o.estimators.deeplearning import H2ODeepLearningEstimator
      >>> ml.init()
      >>> rows=[[1,2,3,4,0],[2,1,2,4,1],[2,1,4,2,1],[0,1,2,34,1],[2,3,4,1,0]]*50
      >>> fr = ml.H2OFrame(rows)
      >>> fr[4] = fr[4].asfactor()
      >>> model = H2ODeepLearningEstimator()
      >>> model.train(x=range(4), y=4, training_frame=fr)
    """
    super(H2ODeepLearningEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k:v for k,v in self._parms.iteritems() if k!="self"}
    self._parms["autoencoder"] = isinstance(self, H2OAutoEncoderEstimator)

  @property
  def overwrite_with_best_model(self):
    return self._parms["overwrite_with_best_model"]

  @overwrite_with_best_model.setter
  def overwrite_with_best_model(self, value):
    self._parms["overwrite_with_best_model"] = value

  @property
  def checkpoint(self):
    return self._parms["checkpoint"]

  @checkpoint.setter
  def checkpoint(self, value):
    self._parms["checkpoint"] = value

  @property
  def use_all_factor_levels(self):
    return self._parms["use_all_factor_levels"]

  @use_all_factor_levels.setter
  def use_all_factor_levels(self, value):
    self._parms["use_all_factor_levels"] = value

  @property
  def activation(self):
    return self._parms["activation"]

  @activation.setter
  def activation(self, value):
    self._parms["activation"] = value

  @property
  def hidden(self):
    return self._parms["hidden"]

  @hidden.setter
  def hidden(self, value):
    self._parms["hidden"] = value

  @property
  def epochs(self):
    return self._parms["epochs"]

  @epochs.setter
  def epochs(self, value):
    self._parms["epochs"] = value

  @property
  def train_samples_per_iteration(self):
    return self._parms["train_samples_per_iteration"]

  @train_samples_per_iteration.setter
  def train_samples_per_iteration(self, value):
    self._parms["train_samples_per_iteration"] = value

  @property
  def seed(self):
    return self._parms["seed"]

  @seed.setter
  def seed(self, value):
    self._parms["seed"] = value

  @property
  def adaptive_rate(self):
    return self._parms["adaptive_rate"]

  @adaptive_rate.setter
  def adaptive_rate(self, value):
    self._parms["adaptive_rate"] = value

  @property
  def rho(self):
    return self._parms["rho"]

  @rho.setter
  def rho(self, value):
    self._parms["rho"] = value

  @property
  def epsilon(self):
    return self._parms["epsilon"]

  @epsilon.setter
  def epsilon(self, value):
    self._parms["epsilon"] = value

  @property
  def rate(self):
    return self._parms["rate"]

  @rate.setter
  def rate(self, value):
    self._parms["rate"] = value

  @property
  def rate_annealing(self):
    return self._parms["rate_annealing"]

  @rate_annealing.setter
  def rate_annealing(self, value):
    self._parms["rate_annealing"] = value

  @property
  def rate_decay(self):
    return self._parms["rate_decay"]

  @rate_decay.setter
  def rate_decay(self, value):
    self._parms["rate_decay"] = value

  @property
  def momentum_start(self):
    return self._parms["momentum_start"]

  @momentum_start.setter
  def momentum_start(self, value):
    self._parms["momentum_start"] = value

  @property
  def momentum_ramp(self):
    return self._parms["momentum_ramp"]

  @momentum_ramp.setter
  def momentum_ramp(self, value):
    self._parms["momentum_ramp"] = value

  @property
  def momentum_stable(self):
    return self._parms["momentum_stable"]

  @momentum_stable.setter
  def momentum_stable(self, value):
    self._parms["momentum_stable"] = value

  @property
  def nesterov_accelerated_gradient(self):
    return self._parms["nesterov_accelerated_gradient"]

  @nesterov_accelerated_gradient.setter
  def nesterov_accelerated_gradient(self, value):
    self._parms["nesterov_accelerated_gradient"] = value

  @property
  def input_dropout_ratio(self):
    return self._parms["input_dropout_ratio"]

  @input_dropout_ratio.setter
  def input_dropout_ratio(self, value):
    self._parms["input_dropout_ratio"] = value

  @property
  def hidden_dropout_ratios(self):
    return self._parms["hidden_dropout_ratios"]

  @hidden_dropout_ratios.setter
  def hidden_dropout_ratios(self, value):
    self._parms["hidden_dropout_ratios"] = value

  @property
  def l1(self):
    return self._parms["l1"]

  @l1.setter
  def l1(self, value):
    self._parms["l1"] = value

  @property
  def l2(self):
    return self._parms["l2"]

  @l2.setter
  def l2(self, value):
    self._parms["l2"] = value

  @property
  def max_w2(self):
    return self._parms["max_w2"]

  @max_w2.setter
  def max_w2(self, value):
    self._parms["max_w2"] = value

  @property
  def initial_weight_distribution(self):
    return self._parms["initial_weight_distribution"]

  @initial_weight_distribution.setter
  def initial_weight_distribution(self, value):
    self._parms["initial_weight_distribution"] = value

  @property
  def initial_weight_scale(self):
    return self._parms["initial_weight_scale"]

  @initial_weight_scale.setter
  def initial_weight_scale(self, value):
    self._parms["initial_weight_scale"] = value

  @property
  def loss(self):
    return self._parms["loss"]

  @loss.setter
  def loss(self, value):
    self._parms["loss"] = value

  @property
  def distribution(self):
    return self._parms["distribution"]

  @distribution.setter
  def distribution(self, value):
    self._parms["distribution"] = value

  @property
  def tweedie_power(self):
    return self._parms["tweedie_power"]

  @tweedie_power.setter
  def tweedie_power(self, value):
    self._parms["tweedie_power"] = value

  @property
  def score_interval(self):
    return self._parms["score_interval"]

  @score_interval.setter
  def score_interval(self, value):
    self._parms["score_interval"] = value

  @property
  def score_training_samples(self):
    return self._parms["score_training_samples"]

  @score_training_samples.setter
  def score_training_samples(self, value):
    self._parms["score_training_samples"] = value

  @property
  def score_validation_samples(self):
    return self._parms["score_validation_samples"]

  @score_validation_samples.setter
  def score_validation_samples(self, value):
    self._parms["score_validation_samples"] = value

  @property
  def score_duty_cycle(self):
    return self._parms["score_duty_cycle"]

  @score_duty_cycle.setter
  def score_duty_cycle(self, value):
    self._parms["score_duty_cycle"] = value

  @property
  def classification_stop(self):
    return self._parms["classification_stop"]

  @classification_stop.setter
  def classification_stop(self, value):
    self._parms["classification_stop"] = value

  @property
  def regression_stop(self):
    return self._parms["regression_stop"]

  @regression_stop.setter
  def regression_stop(self, value):
    self._parms["regression_stop"] = value

  @property
  def stopping_rounds(self):
    return self._parms["stopping_rounds"]

  @stopping_rounds.setter
  def stopping_rounds(self, value):
    self._parms["stopping_rounds"] = value

  @property
  def stopping_metric(self):
    return self._parms["stopping_metric"]

  @stopping_metric.setter
  def stopping_metric(self, value):
    self._parms["stopping_metric"] = value

  @property
  def stopping_tolerance(self):
    return self._parms["stopping_tolerance"]

  @stopping_tolerance.setter
  def stopping_tolerance(self, value):
    self._parms["stopping_tolerance"] = value

  @property
  def quiet_mode(self):
    return self._parms["quiet_mode"]

  @quiet_mode.setter
  def quiet_mode(self, value):
    self._parms["quiet_mode"] = value

  @property
  def max_confusion_matrix_size(self):
    return self._parms["max_confusion_matrix_size"]

  @max_confusion_matrix_size.setter
  def max_confusion_matrix_size(self, value):
    self._parms["max_confusion_matrix_size"] = value

  @property
  def max_hit_ratio_k(self):
    return self._parms["max_hit_ratio_k"]

  @max_hit_ratio_k.setter
  def max_hit_ratio_k(self, value):
    self._parms["max_hit_ratio_k"] = value

  @property
  def balance_classes(self):
    return self._parms["balance_classes"]

  @balance_classes.setter
  def balance_classes(self, value):
    self._parms["balance_classes"] = value

  @property
  def class_sampling_factors(self):
    return self._parms["class_sampling_factors"]

  @class_sampling_factors.setter
  def class_sampling_factors(self, value):
    self._parms["class_sampling_factors"] = value

  @property
  def max_after_balance_size(self):
    return self._parms["max_after_balance_size"]

  @max_after_balance_size.setter
  def max_after_balance_size(self, value):
    self._parms["max_after_balance_size"] = value

  @property
  def score_validation_sampling(self):
    return self._parms["score_validation_sampling"]

  @score_validation_sampling.setter
  def score_validation_sampling(self, value):
    self._parms["score_validation_sampling"] = value

  @property
  def diagnostics(self):
    return self._parms["diagnostics"]

  @diagnostics.setter
  def diagnostics(self, value):
    self._parms["diagnostics"] = value

  @property
  def variable_importances(self):
    return self._parms["variable_importances"]

  @variable_importances.setter
  def variable_importances(self, value):
    self._parms["variable_importances"] = value

  @property
  def fast_mode(self):
    return self._parms["fast_mode"]

  @fast_mode.setter
  def fast_mode(self, value):
    self._parms["fast_mode"] = value

  @property
  def ignore_const_cols(self):
    return self._parms["ignore_const_cols"]

  @ignore_const_cols.setter
  def ignore_const_cols(self, value):
    self._parms["ignore_const_cols"] = value

  @property
  def force_load_balance(self):
    return self._parms["force_load_balance"]

  @force_load_balance.setter
  def force_load_balance(self, value):
    self._parms["force_load_balance"] = value

  @property
  def replicate_training_data(self):
    return self._parms["replicate_training_data"]

  @replicate_training_data.setter
  def replicate_training_data(self, value):
    self._parms["replicate_training_data"] = value

  @property
  def single_node_mode(self):
    return self._parms["single_node_mode"]

  @single_node_mode.setter
  def single_node_mode(self, value):
    self._parms["single_node_mode"] = value

  @property
  def shuffle_training_data(self):
    return self._parms["shuffle_training_data"]

  @shuffle_training_data.setter
  def shuffle_training_data(self, value):
    self._parms["shuffle_training_data"] = value

  @property
  def sparse(self):
    return self._parms["sparse"]

  @sparse.setter
  def sparse(self, value):
    self._parms["sparse"] = value

  @property
  def col_major(self):
    return self._parms["col_major"]

  @col_major.setter
  def col_major(self, value):
    self._parms["col_major"] = value

  @property
  def average_activation(self):
    return self._parms["average_activation"]

  @average_activation.setter
  def average_activation(self, value):
    self._parms["average_activation"] = value

  @property
  def sparsity_beta(self):
    return self._parms["sparsity_beta"]

  @sparsity_beta.setter
  def sparsity_beta(self, value):
    self._parms["sparsity_beta"] = value

  @property
  def max_categorical_features(self):
    return self._parms["max_categorical_features"]

  @max_categorical_features.setter
  def max_categorical_features(self, value):
    self._parms["max_categorical_features"] = value

  @property
  def reproducible(self):
    return self._parms["reproducible"]

  @reproducible.setter
  def reproducible(self, value):
    self._parms["reproducible"] = value

  @property
  def export_weights_and_biases(self):
    return self._parms["export_weights_and_biases"]

  @export_weights_and_biases.setter
  def export_weights_and_biases(self, value):
    self._parms["export_weights_and_biases"] = value

  @property
  def nfolds(self):
    return self._parms["nfolds"]

  @nfolds.setter
  def nfolds(self, value):
    self._parms["nfolds"] = value

  @property
  def fold_assignment(self):
    return self._parms["fold_assignment"]

  @fold_assignment.setter
  def fold_assignment(self, value):
    self._parms["fold_assignment"] = value

  @property
  def keep_cross_validation_predictions(self):
    return self._parms["keep_cross_validation_predictions"]

  @keep_cross_validation_predictions.setter
  def keep_cross_validation_predictions(self, value):
    self._parms["keep_cross_validation_predictions"] = value


class H2OAutoEncoderEstimator(H2ODeepLearningEstimator):
  """
  Examples
  --------
    >>> import h2o as ml
    >>> from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
    >>> ml.init()
    >>> rows=[[1,2,3,4,0]*50,[2,1,2,4,1]*50,[2,1,4,2,1]*50,[0,1,2,34,1]*50,[2,3,4,1,0]*50]
    >>> fr = ml.H2OFrame(rows)
    >>> fr[4] = fr[4].asfactor()
    >>> model = H2OAutoEncoderEstimator()
    >>> model.train(x=range(4), training_frame=fr)
  """
  pass