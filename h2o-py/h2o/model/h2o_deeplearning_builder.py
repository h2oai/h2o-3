"""
A Deeplearning model builder.
"""

from h2o_model_builder import H2OModelBuilder


class H2ODeeplearningMBuilder(H2OModelBuilder):
    """
    Build a new Deeplearning model.

    Example Usage:

        from h2o.model.h2o_deeplearning_builder import H2ODL   # import this builder

        my_gbm = H2ODL()                                       # create a new gbm object
        my_gbm.x = [0,1,2,3]                                   # fill in parameters:
        my_gbm.y = 4
        my_gbm.training_frame = <training_frame>
        my_gbm.ntrees = 100
        my_gbm.max_depth = 5
        my_gbm.learning_rate = 0.01

        my_gbm.fit()                                           # perform the model fit
    """

    SELF = "deeplearning"

    def __init__(self, x=None, y=None, training_frame=None, key=None,
                 override_with_best_model=True, n_folds=0, checkpoint=None,
                 autoencoder=False, use_all_factor_levels=True,
                 activation=("Rectifier", "Tanh", "TanhWithDropout",
                             "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),
                 hidden=(200, 200), epochs=10, train_samples_per_iteration=-2,
                 seed=None, adaptive_rate=True, rho=0.99, epsilon=1e-8, rate=0.005,
                 rate_annealing=1e-6, rate_decay=1.0, momentum_start=0, momentum_ramp=1e6,
                 momentum_stable=0, nesterov_accelerated_gradient=True,
                 input_dropout_ratio=0, hidden_dropout_ratios=None, l1=0, l2=0,
                 max_w2=float("inf"), validation_frame=None,
                 initial_weight_distribution=("UniformAdaptive", "Uniform", "Normal"),
                 initial_weight_scale=1, loss=("Automatic", "MeanSquare", "CrossEntropy"),
                 score_interval=5, score_training_samples=10000,
                 score_validation_samples=0, score_duty_cycle=0.1,
                 classification_stop=0, regression_stop=1e-6, quiet_mode=False,
                 max_confusion_matrix_size=None, max_hit_ratio_k=0,
                 balance_classes=False, max_after_balance_size=None,
                 score_validation_sampling=("Uniform", "Stratified"), diagnostics=True,
                 variable_importances=False, fast_mode=True, ignore_const_cols=True,
                 force_load_balance=True, replicate_training_data=True,
                 single_node_mode=False, shuffle_training_data=False, sparse=False,
                 col_major=False, **kwargs):
        """

        :param x: Predictor columns (may be indices or strings)
        :param y: Response column (may be an index or a string)
        :param training_frame: An object of type H2OFrame
        :param key: The output name of the model. If None, one will be generated.
        :param override_with_best_model: If True, then H2O will override the final model
                                         with the best model found during training.
        :param n_folds: Number of folds for cross-validation. If n_folds >= 2, then
                        validation_frame must be None.
        :param checkpoint: Either a model key or a fitted H2ODeeplearningBuilder object
                           to resume training with.
        :param autoencoder: Enable auto-encoder for model building
        :param use_all_factor_levels: Use all factor levels of categorical variance.
                                      Otherwise the first factor level is omitted
                                      (without loss of accuracy). Useful for variable
                                      importances and auto-enabled for autoencoder.
        :param activation: A string indicating which activation function to use.
        :param hidden: A list/tuple indicating the sizes of hidden layers.
        :param epochs: How many times the dataset should be iterated (streamed), may be
                       fractional.
        :param train_samples_per_iteration: Number of training samples (globally) per
                                            MapReduce iteration.
                                            Special values are:
                                            0 for one epoch;
                                           -1 for all available data
                                               (e.g., replicated training data); or
                                           -2 for auto-tuning (default)
        :param seed: Seed for random numbers (affects sampling).
                     Note: only reproducible when running single threaded.
        :param adaptive_rate: Adaptive learning rate. (ADADELTA)
        :param rho: Adaptive learning rate time decay factor (similarity to prior updates)
        :param epsilon: Threshold.
        :param rate: Learning rate (higher => less stable, lower => slower convergence).
        :param rate_annealing: Learning rate annealing:(rate)/(1 + rate_annealing*samples)
        :param rate_decay: Learning rate decay between layers (layer N: rate*alpha^(N-1)).
        :param momentum_start: Initial momentum at the beginning of training (try 0.5).
        :param momentum_ramp: Number of training samples for which momentum increases.
        :param momentum_stable: Final momentum after their amp is over (try 0.99).
        :param nesterov_accelerated_gradient: Use Nesterov's accelerated gradient.
        :param input_dropout_ratio: A fraction of the features for each training row to
                                    be omitted from training in order to improve
                                    generalization (dimension sampling).
        :param hidden_dropout_ratios: A fraction of the inputs for each hidden layer to be
                                      omitted from training in order to improve
                                      generalization. Defaults to 0.5 for each hidden
                                      layer if omitted.
        :param l1: A regularization method that constrains the absolute value of the
                   weights and has the net effect of dropping some weights (setting them
                   to zero) from a model to reduce complexity and avoid overfitting.
        :param l2: A regularization method that constrains the sum of the squared
                   weights. This method introduces bias into parameter estimates, but
                   frequently produces substantial gains in modeling as estimate variance
                   is reduced.
        :param max_w2: A maximum on the sum of the squared incoming weights into any one
                       neuron. This tuning parameter is especially useful for unbound
                       activation functions such as Maxout or Rectifier.
        :param validation_frame:  An object of type H2OFrame.
        :param initial_weight_distribution: The distribution from which initial weights
                                            are to be drawn. The default option is an
                                            optimized initialization that considers the
                                            size of the network. The "uniform" option uses
                                            a uniform distribution with a mean of 0 and a
                                            given interval. The "normal" option draws
                                            weights from the standard normal distribution
                                            with a mean of 0 and given standard deviation.

        :param initial_weight_scale: The scale of the distribution function for Uniform or
                                     Normal distributions. For Uniform, the values are
                                     drawn uniformly from
                                     -initial_weight_scale...initial_weight_scale. For
                                     Normal, the values are drawn from a Normal
                                     distribution with a standard deviation of
                                    initial_weight_scale.
        :param loss: The loss (error) function to be minimized by the model. Cross Entropy
                     loss is used when the model output consists of independent
                     hypotheses, and the outputs can be interpreted as the probability
                     that each hypothesis is true. Cross entropy is the recommended loss
                     function when the target values are class labels, and especially for
                     imbalanced data. It strongly penalizes error in the prediction of the
                     actual class label. Mean Square loss is used when the model output
                     are continuous real values, but can be used for classification as
                     well (where it emphasizes the error on all output classes, not just
                     for the actual class).
        :param score_interval: The minimum time (in seconds) to elapse between model
                               scoring. The actual interval is determined by the number of
                               training samples per iteration and the scoring duty cycle.
        :param score_training_samples: The number of training dataset points to be used
                                       for scoring. Will be randomly sampled. Use 0 for
                                       selecting the entire training dataset.
        :param score_validation_samples: The number of validation dataset points to be
                                         used for scoring. Can be randomly sampled or
                                         stratified (if "balance classes" is set and
                                         "score validation sampling" is set to stratify).
                                         Use 0 for selecting the entire training dataset.
        :param score_duty_cycle: Maximum fraction of wall clock time spent on model
                                 scoring on training and validation samples, and on
                                 diagnostics such as computation of feature importances
                                 (i.e., not on training).
        :param classification_stop: The stopping criteria in terms of classification error
                                    (1-accuracy) on the training data scoring dataset.
                                    When the error is at or below this threshold, training
                                    stops.
        :param regression_stop: The stopping criteria in terms of regression error (MSE)
                                on the training data scoring dataset. When the error is at
                                or below this threshold, training stops.
        :param quiet_mode: Enable quiet mode for less output to standard output.
        :param max_confusion_matrix_size:
        :param max_hit_ratio_k: Max number (top K) of predictions to use for hit ration
                                computation(for multi-class only, 0 to disable)
        :param balance_classes: Balance training data class counts via over/under-sampling
                                (for imbalanced data).
        :param max_after_balance_size: Maximum relative size of the training data after
                                       balancing class counts (can be less than 1.0).
        :param score_validation_sampling: Method used to sample the validation dataset for
                                          scoring, see Score Validation Samples above.
        :param diagnostics: Gather diagnostics for hidden layers, such as mean and RMS
                            values of learning rate, momentum, weights and biases
        :param variable_importances: Compute variable importances for input features
                                     (Gedeon method) - can be slow for large networks).
        :param fast_mode: Enable fast mode (minor approximation in back-propagation),
                          should not affect results significantly.
        :param ignore_const_cols: Ignore constant training columns.
        :param force_load_balance: Increase training speed on small datasets by splitting
                                   it into many chunks to allow utilization of all cores.
        :param replicate_training_data: Replicate the entire training dataset onto every
                                        node for faster training on small datasets.
        :param single_node_mode: Run on a single node for fine-tuning of model parameters.
                                 Can be useful for checkpoint resumes after training on
                                 multiple nodes for fast initial convergence.
        :param shuffle_training_data: Enable shuffling of training data (on each node).
                                      This option is recommended if training data is
                                      replicated on N nodes, and the number of training
                                      samples per iteration is close to N times the
                                      dataset size, where all nodes train will (almost)
                                      all the data. It is automatically enabled if the
                                      number of training samples per iteration is set to
                                      -1 (or to N times the dataset size or larger).
        :param sparse: Sparse data handling (experimental).
        :param col_major: Use a column major weight matrix for input layer. Can speed up
                          forward propagation, but might slow down back-propagation
                          (Experimental).
        :param kwargs: Any additional arguments to pass.
        :return: A new H2ODeeplearningBuilder object
        """
        super(H2ODeeplearningMBuilder, self).__init__(locals(), self.SELF, training_frame)
        self.__dict__.update(locals())

        # deal with "tuple" defaults
        self.activation = "Rectifier" if isinstance(activation, tuple) else activation
        self.initial_weight_distribution = "UniformAdaptive" \
            if isinstance(initial_weight_distribution, tuple) \
            else initial_weight_distribution
        self.loss = "Automatic" if isinstance(loss, tuple) else loss
        self.score_validation_sampling = "Uniform" \
            if isinstance(score_validation_sampling, tuple) \
            else score_validation_sampling