package hex.schemas;

import hex.Distribution;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.KeyV3;

public class DeepLearningV3 extends ModelBuilderSchema<DeepLearning,DeepLearningV3,DeepLearningV3.DeepLearningParametersV3> {

  public static final class DeepLearningParametersV3 extends ModelParametersSchemaV3<DeepLearningParameters, DeepLearningParametersV3> {

    // Determines the order of parameters in the GUI
    public static String[] fields = {
        "model_id",
        "training_frame",
        "validation_frame",
        "nfolds",
        "keep_cross_validation_models",
        "keep_cross_validation_predictions",
        "keep_cross_validation_fold_assignment",
        "fold_assignment",
        "fold_column",
        "response_column",
        "ignored_columns",
        "ignore_const_cols",
        "score_each_iteration",
        "weights_column",
        "offset_column",
        "balance_classes",
        "class_sampling_factors",
        "max_after_balance_size",
        "max_confusion_matrix_size",
        "checkpoint",
        "pretrained_autoencoder",
        "overwrite_with_best_model",
        "use_all_factor_levels",
        "standardize",
        "activation",
        "hidden",
        "epochs",
        "train_samples_per_iteration",
        "target_ratio_comm_to_comp",
        "seed",
        "adaptive_rate",
        "rho",
        "epsilon",
        "rate",
        "rate_annealing",
        "rate_decay",
        "momentum_start",
        "momentum_ramp",
        "momentum_stable",
        "nesterov_accelerated_gradient",
        "input_dropout_ratio",
        "hidden_dropout_ratios",
        "l1",
        "l2",
        "max_w2",
        "initial_weight_distribution",
        "initial_weight_scale",
        "initial_weights",
        "initial_biases",
        "loss",
        "distribution",
        "quantile_alpha",
        "tweedie_power",
        "huber_alpha",
        "score_interval",
        "score_training_samples",
        "score_validation_samples",
        "score_duty_cycle",
        "classification_stop",
        "regression_stop",
        "stopping_rounds",
        "stopping_metric",
        "stopping_tolerance",
        "max_runtime_secs",
        "score_validation_sampling",
        "diagnostics",
        "fast_mode",
        "force_load_balance",
        "variable_importances",
        "replicate_training_data",
        "single_node_mode",
        "shuffle_training_data",
        "missing_values_handling",
        "quiet_mode",
        "autoencoder",
        "sparse",
        "col_major",
        "average_activation",
        "sparsity_beta",
        "max_categorical_features",
        "reproducible",
        "export_weights_and_biases",
        "mini_batch_size",
        "categorical_encoding",
        "elastic_averaging",
        "elastic_averaging_moving_rate",
        "elastic_averaging_regularization",
        "export_checkpoints_dir", 
        "auc_type", 
        "custom_metric_func",
    };


    /* Imbalanced Classes */

    /**
     * For imbalanced data, balance training data class counts via
     * over/under-sampling. This can result in improved predictive accuracy.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Balance training data class counts via over/under-sampling (for imbalanced data).")
    public boolean balance_classes;

    /**
     * Desired over/under-sampling ratios per class (lexicographic order).
     * Only when balance_classes is enabled.
     * If not specified, they will be automatically computed to obtain class balance during training.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling " +
            "factors will be automatically computed to obtain class balance during training. Requires balance_classes.")
    public float[] class_sampling_factors;

    /**
     * When classes are balanced, limit the resulting dataset size to the
     * specified multiple of the original dataset size.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = false,
        help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0). " +
            "Requires balance_classes.")
    public float max_after_balance_size;

    /** For classification models, the maximum size (in terms of classes) of
     *  the confusion matrix for it to be printed. This option is meant to
     *  avoid printing extremely large confusion matrices.
     *  */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = false,
        help = "[Deprecated] Maximum size (# classes) for confusion matrices to be printed in the Logs.")
    public int max_confusion_matrix_size;


    /* Neural Net Topology */

    /**
     * The activation function (non-linearity) to be used by the neurons in the hidden layers.
     * Tanh: Hyperbolic tangent function (same as scaled and shifted sigmoid).
     * Rectifier: Rectifier Linear Unit: Chooses the maximum of (0, x) where x is the input value.
     * Maxout: Choose the maximum coordinate of the input vector.
     * ExpRectifier: Exponential Rectifier Linear Unit function (http://arxiv.org/pdf/1511.07289v2.pdf)
     * With Dropout: Zero out a random user-given fraction of the
     *      incoming weights to each hidden layer during training, for each
     *      training row. This effectively trains exponentially many models at
     *      once, and can improve generalization.
     */
    @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
        values = {"Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"},
        help = "Activation function.")
    public DeepLearningParameters.Activation activation;

    /**
     * The number and size of each hidden layer in the model.
     * For example, if a user specifies "100,200,100" a model with 3 hidden
     * layers will be produced, and the middle hidden layer will have 200
     * neurons.
     */
    @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
        help = "Hidden layer sizes (e.g. [100, 100]).")
    public int[] hidden;

    /**
     * The number of passes over the training dataset to be carried out.
     * It is recommended to start with lower values for initial grid searches.
     * This value can be modified during checkpoint restarts and allows continuation
     * of selected models.
     */
    @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
        help = "How many times the dataset should be iterated (streamed), can be fractional.")
    public double epochs;

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
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Number of training samples (globally) per MapReduce iteration. Special values are 0: one epoch, -1: " +
            "all available data (e.g., replicated training data), -2: automatic.")
    public long train_samples_per_iteration;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Target ratio of communication overhead to computation. Only for multi-node operation and " +
            "train_samples_per_iteration = -2 (auto-tuning).")
    public double target_ratio_comm_to_comp;

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
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Seed for random numbers (affects sampling) - Note: only reproducible when running single threaded.")
    public long seed;


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
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Adaptive learning rate.")
    public boolean adaptive_rate;

    /**
     * The first of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to momentum and relates to the memory to prior weight updates.
     * Typical values are between 0.9 and 0.999.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Adaptive learning rate time decay factor (similarity to prior updates).")
    public double rho;

    /**
     * The second of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to learning rate annealing during initial training
     * and momentum at later stages where it allows forward progress.
     * Typical values are between 1e-10 and 1e-4.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Adaptive learning rate smoothing factor (to avoid divisions by zero and allow progress).")
    public double epsilon;


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
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Learning rate (higher => less stable, lower => slower convergence).")
    public double rate;

    /**
     * Learning rate annealing reduces the learning rate to "freeze" into
     * local minima in the optimization landscape.  The annealing rate is the
     * inverse of the number of training samples it takes to cut the learning rate in half
     * (e.g., 1e-6 means that it takes 1e6 training samples to halve the learning rate).
     * This parameter is only active if adaptive learning rate is disabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Learning rate annealing: rate / (1 + rate_annealing * samples).")
    public double rate_annealing;

    /**
     * The learning rate decay parameter controls the change of learning rate across layers.
     * For example, assume the rate parameter is set to 0.01, and the rate_decay parameter is set to 0.5.
     * Then the learning rate for the weights connecting the input and first hidden layer will be 0.01,
     * the learning rate for the weights connecting the first and the second hidden layer will be 0.005,
     * and the learning rate for the weights connecting the second and third hidden layer will be 0.0025, etc.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Learning rate decay factor between layers (N-th layer: rate * rate_decay ^ (n - 1).")
    public double rate_decay;


    /*Momentum*/

    /**
     * The momentum_start parameter controls the amount of momentum at the beginning of training.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Initial momentum at the beginning of training (try 0.5).")
    public double momentum_start;

    /**
     * The momentum_ramp parameter controls the amount of learning for which momentum increases
     * (assuming momentum_stable is larger than momentum_start). The ramp is measured in the number
     * of training samples.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Number of training samples for which momentum increases.")
    public double momentum_ramp;

    /**
     * The momentum_stable parameter controls the final momentum value reached after momentum_ramp training samples.
     * The momentum used for training will remain the same for training beyond reaching that point.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Final momentum after the ramp is over (try 0.99).")
    public double momentum_stable;

    /**
     * The Nesterov accelerated gradient descent method is a modification to
     * traditional gradient descent for convex functions. The method relies on
     * gradient information at various points to build a polynomial approximation that
     * minimizes the residuals in fewer iterations of the descent.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Use Nesterov accelerated gradient (recommended).")
    public boolean nesterov_accelerated_gradient;


    /*Regularization*/

    /**
     * A fraction of the features for each training row to be omitted from training in order
     * to improve generalization (dimension sampling).
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Input layer dropout ratio (can improve generalization, try 0.1 or 0.2).")
    public double input_dropout_ratio;

    /**
     * A fraction of the inputs for each hidden layer to be omitted from training in order
     * to improve generalization. Defaults to 0.5 for each hidden layer if omitted.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Hidden layer dropout ratios (can improve generalization), specify one value per hidden layer, " +
            "defaults to 0.5.")
    public double[] hidden_dropout_ratios;

    /**
     * A regularization method that constrains the absolute value of the weights and
     * has the net effect of dropping some weights (setting them to zero) from a model
     * to reduce complexity and avoid overfitting.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "L1 regularization (can add stability and improve generalization, causes many weights to become 0).")
    public double l1;

    /**
     *  A regularization method that constrains the sum of the squared
     * weights. This method introduces bias into parameter estimates, but
     * frequently produces substantial gains in modeling as estimate variance is
     * reduced.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "L2 regularization (can add stability and improve generalization, causes many weights to be small.")
    public double l2;

    /**
     *  A maximum on the sum of the squared incoming weights into
     * any one neuron. This tuning parameter is especially useful for unbound
     * activation functions such as Maxout or Rectifier.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Constraint for squared sum of incoming weights per unit (e.g. for Rectifier).")
    public float max_w2;


    /*Initialization*/

    /**
     * The distribution from which initial weights are to be drawn. The default
     * option is an optimized initialization that considers the size of the network.
     * The "uniform" option uses a uniform distribution with a mean of 0 and a given
     * interval. The "normal" option draws weights from the standard normal
     * distribution with a mean of 0 and given standard deviation.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        values = {"UniformAdaptive", "Uniform", "Normal"},
        help = "Initial weight distribution.")
    public DeepLearningParameters.InitialWeightDistribution initial_weight_distribution;

    /**
     * The scale of the distribution function for Uniform or Normal distributions.
     * For Uniform, the values are drawn uniformly from -initial_weight_scale...initial_weight_scale.
     * For Normal, the values are drawn from a Normal distribution with a standard deviation of initial_weight_scale.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Uniform: -value...value, Normal: stddev.")
    public double initial_weight_scale;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable=true,
        help = "A list of H2OFrame ids to initialize the weight matrices of this model with.")
    public KeyV3.FrameKeyV3[] initial_weights;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable=true,
        help = "A list of H2OFrame ids to initialize the bias vectors of this model with.")
    public KeyV3.FrameKeyV3[] initial_biases;

    /**
     * The loss (error) function to be minimized by the model.
     * CrossEntropy loss is used when the model output consists of independent
     * hypotheses, and the outputs can be interpreted as the probability that each
     * hypothesis is true. Cross entropy is the recommended loss function when the
     * target values are class labels, and especially for imbalanced data.
     * It strongly penalizes error in the prediction of the actual class label.
     * Quadratic loss is used when the model output are continuous real values, but can
     * be used for classification as well (where it emphasizes the error on all
     * output classes, not just for the actual class).
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true, required = false,
        values = {"Automatic", "CrossEntropy", "Quadratic", "Huber", "Absolute", "Quantile"},
        help = "Loss function.")
    public DeepLearningParameters.Loss loss;

    /*Scoring*/

    /**
     * The minimum time (in seconds) to elapse between model scoring. The actual
     * interval is determined by the number of training samples per iteration and the scoring duty cycle.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Shortest time interval (in seconds) between model scoring.")
    public double score_interval;

    /**
     * The number of training dataset points to be used for scoring. Will be
     * randomly sampled. Use 0 for selecting the entire training dataset.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Number of training set samples for scoring (0 for all).")
    public long score_training_samples;

    /**
     * The number of validation dataset points to be used for scoring. Can be
     * randomly sampled or stratified (if "balance classes" is set and "score
     * validation sampling" is set to stratify). Use 0 for selecting the entire
     * training dataset.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Number of validation set samples for scoring (0 for all).")
    public long score_validation_samples;

    /**
     * Maximum fraction of wall clock time spent on model scoring on training and validation samples,
     * and on diagnostics such as computation of feature importances (i.e., not on training).
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Maximum duty cycle fraction for scoring (lower: more training, higher: more scoring).")
    public double score_duty_cycle;

    /**
     * The stopping criteria in terms of classification error (1-accuracy) on the
     * training data scoring dataset. When the error is at or below this threshold,
     * training stops.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Stopping criterion for classification error fraction on training data (-1 to disable).")
    public double classification_stop;

    /**
     * The stopping criteria in terms of regression error (MSE) on the training
     * data scoring dataset. When the error is at or below this threshold, training
     * stops.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Stopping criterion for regression error (MSE) on training data (-1 to disable).")
    public double regression_stop;

    /**
     * Enable quiet mode for less output to standard output.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Enable quiet mode for less output to standard output.")
    public boolean quiet_mode;

    /**
     * Method used to sample the validation dataset for scoring, see Score Validation Samples above.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        values = {"Uniform", "Stratified"},
        help = "Method used to sample validation dataset for scoring.")
    public DeepLearningParameters.ClassSamplingMethod score_validation_sampling;


    /* Miscellaneous */

    /**
     * If enabled, store the best model under the destination key of this model at the end of training.
     * Only applicable if training is not cancelled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "If enabled, override the final model with the best model found during training.")
    public boolean overwrite_with_best_model;

    @API(level = API.Level.secondary, direction = API.Direction.INOUT,
        help = "Auto-Encoder.")
    public boolean autoencoder;

    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Use all factor levels of categorical variables. Otherwise, the first factor level is omitted (without" +
            " loss of accuracy). Useful for variable importances and auto-enabled for autoencoder.")
    public boolean use_all_factor_levels;

    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "If enabled, automatically standardize the data. If disabled, the user must provide properly scaled " +
            "input data.")
    public boolean standardize;

    /**
     * Gather diagnostics for hidden layers, such as mean and RMS values of learning
     * rate, momentum, weights and biases.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT,
        help = "Enable diagnostics for hidden layers.")
    public boolean diagnostics;

    /**
     * Whether to compute variable importances for input features.
     * The implemented method (by Gedeon) considers the weights connecting the
     * input features to the first two hidden layers.
     */
    @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
        help = "Compute variable importances for input features (Gedeon method) - can be slow for large networks.")
    public boolean variable_importances;

    /**
     * Enable fast mode (minor approximation in back-propagation), should not affect results significantly.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Enable fast mode (minor approximation in back-propagation).")
    public boolean fast_mode;

    /**
     * Increase training speed on small datasets by splitting it into many chunks
     * to allow utilization of all cores.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Force extra load balancing to increase training speed for small datasets (to keep all cores busy).")
    public boolean force_load_balance;

    /**
     * Replicate the entire training dataset onto every node for faster training on small datasets.
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "Replicate the entire training dataset onto every node for faster training on small datasets.")
    public boolean replicate_training_data;

    /**
     * Run on a single node for fine-tuning of model parameters. Can be useful for
     * checkpoint resumes after training on multiple nodes for fast initial
     * convergence.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Run on a single node for fine-tuning of model parameters.")
    public boolean single_node_mode;

    /**
     * Enable shuffling of training data (on each node). This option is
     * recommended if training data is replicated on N nodes, and the number of training samples per iteration
     * is close to N times the dataset size, where all nodes train will (almost) all
     * the data. It is automatically enabled if the number of training samples per iteration is set to -1 (or to N
     * times the dataset size or larger).
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Enable shuffling of training data (recommended if training data is replicated and " +
            "train_samples_per_iteration is close to #nodes x #rows, of if using balance_classes).")
    public boolean shuffle_training_data;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        values = {"MeanImputation", "Skip"},
        help = "Handling of missing values. Either MeanImputation or Skip.")
    public DeepLearningParameters.MissingValuesHandling missing_values_handling;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Sparse data handling (more efficient for data with lots of 0 values).")
    public boolean sparse;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "#DEPRECATED Use a column major weight matrix for input layer. Can speed up forward propagation, but " +
            "might slow down backpropagation.")
    public boolean col_major;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Average activation for sparse auto-encoder. #Experimental")
    public double average_activation;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Sparsity regularization. #Experimental")
    public double sparsity_beta;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Max. number of categorical features, enforced via hashing. #Experimental")
    public int max_categorical_features;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Force reproducibility on small data (will be slow - only uses 1 thread).")
    public boolean reproducible;

    @API(level = API.Level.expert, direction=API.Direction.INOUT,
        help = "Whether to export Neural Network weights and biases to H2O Frames.")
    public boolean export_weights_and_biases;

    @API(level = API.Level.expert, direction=API.Direction.INOUT,
        help = "Mini-batch size (smaller leads to better fit, larger can speed up and generalize better).")
    public int mini_batch_size;

    @API(level = API.Level.expert, direction=API.Direction.INOUT, gridable = true,
        help = "Elastic averaging between compute nodes can improve distributed model convergence. #Experimental")
    public boolean elastic_averaging;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Elastic averaging moving rate (only if elastic averaging is enabled).")
    public double elastic_averaging_moving_rate;

    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Elastic averaging regularization strength (only if elastic averaging is enabled).")
    public double elastic_averaging_regularization;

    @API(level = API.Level.expert, direction = API.Direction.INOUT,
        help = "Pretrained autoencoder model to initialize this model with.")
    public KeyV3.ModelKeyV3 pretrained_autoencoder;
  }
}
