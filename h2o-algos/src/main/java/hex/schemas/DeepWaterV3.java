package hex.schemas;

import hex.deepwater.DeepWater;
import hex.deepwater.DeepWaterParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class DeepWaterV3 extends ModelBuilderSchema<DeepWater,DeepWaterV3,DeepWaterV3.DeepWaterParametersV3> {

  public static final class DeepWaterParametersV3 extends ModelParametersSchemaV3<DeepWaterParameters, DeepWaterParametersV3> {

    // Determines the order of parameters in the GUI
    static public String[] fields = new String[] {
				"model_id",
        "checkpoint",
				"training_frame",
				"validation_frame",
        "nfolds",
        "keep_cross_validation_predictions",
        "keep_cross_validation_fold_assignment",
        "fold_assignment",
        "fold_column",
				"response_column",
				"ignored_columns",
				"score_each_iteration",
        "categorical_encoding",
        "overwrite_with_best_model",
        "epochs",
        "train_samples_per_iteration",
        "target_ratio_comm_to_comp",
        "seed",
        "standardize",
        "learning_rate",
        "learning_rate_annealing",
        "momentum_start",
        "momentum_ramp",
        "momentum_stable",
        "distribution",
        "score_interval",
        "score_training_samples",
        "score_validation_samples",
        "score_duty_cycle",
        "stopping_rounds",
        "stopping_metric",
        "stopping_tolerance",
        "max_runtime_secs",
        "replicate_training_data",
        "single_node_mode",
        "shuffle_training_data",
        "mini_batch_size",
        "clip_gradient",
        "network",
        "backend",
        "image_shape",
        "channels",
        "gpu",
        "device_id",
        "network_definition_file",
        "network_parameters_file",
        "mean_image_file",
        "export_native_model_prefix",
        "activation",
        "hidden",
        "input_dropout_ratio",
        "hidden_dropout_ratios",
    };

    /**
     * The activation function (non-linearity) to be used by the neurons in the hidden layers.
     * Rectifier: Rectifier Linear Unit: Chooses the maximum of (0, x) where x is the input value.
     * Tanh: Hyperbolic tangent function (same as scaled and shifted sigmoid).
     */
    @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
        values = {"Rectifier", "Tanh"}, help = "Activation function.")
    public DeepWaterParameters.Activation activation;

    /**
     * The number and size of each hidden layer in the model.
     * For example, if a user specifies "100,200,100" a model with 3 hidden
     * layers will be produced, and the middle hidden layer will have 200
     * neurons.
     */
    @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
        help = "Hidden layer sizes (e.g. [200, 200]).")
    public int[] hidden;

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

    /** For classification models, the maximum size (in terms of classes) of
     *  the confusion matrix for it to be printed. This option is meant to
     *  avoid printing extremely large confusion matrices.
     *  */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = false,
        help = "Maximum size (# classes) for confusion matrices to be printed in the Logs.")
    public int max_confusion_matrix_size;

    /**
     * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
     */
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = false,
        help = "Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to " +
            "disable).")
    public int max_hit_ratio_k;

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
    public double learning_rate;

    /**
     * Learning rate annealing reduces the learning rate to "freeze" into
     * local minima in the optimization landscape.  The annealing rate is the
     * inverse of the number of training samples it takes to cut the learning rate in half
     * (e.g., 1e-6 means that it takes 1e6 training samples to halve the learning rate).
     * This parameter is only active if adaptive learning rate is disabled.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Learning rate annealing: rate / (1 + rate_annealing * samples).")
    public double learning_rate_annealing;

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
     * Enable quiet mode for less output to standard output.
     */
    @API(level = API.Level.expert, direction = API.Direction.INOUT, gridable = true,
        help = "Enable quiet mode for less output to standard output.")
    public boolean quiet_mode;

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

    @API(level = API.Level.expert, direction=API.Direction.INOUT, gridable = true,
        help = "Mini-batch size (smaller leads to better fit, larger can speed up and generalize better).")
    public int mini_batch_size;

    @API(level = API.Level.expert, direction=API.Direction.INOUT, gridable = true,
        help = "Clip gradients once their absolute value is larger than this value.")
    public double clip_gradient;

    @API(level = API.Level.critical, direction=API.Direction.INOUT, gridable = true, values = {"auto","user","lenet","alexnet","vgg","googlenet","inception_bn","resnet"},
        help = "Network architecture.")
    public DeepWaterParameters.Network network;

    @API(level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true, values = {"auto","mxnet","caffe","tensorflow"},
        help = "Deep Learning Backend.")
    public DeepWaterParameters.Backend backend;

    @API(level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true,
        help = "Width and height of image.")
    public int[] image_shape;

    @API(level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true,
        help = "Number of (color) channels.")
    public int channels;

    @API(level = API.Level.expert, direction=API.Direction.INOUT,
        help = "Whether to use a GPU (if available).")
    public boolean gpu;

    @API(level = API.Level.expert, direction=API.Direction.INOUT,
        help = "Device ID (which GPU).")
    public int device_id;

    @API(level = API.Level.secondary, direction=API.Direction.INOUT,
        help = "Path of file containing network definition (graph, architecture).")
    public String network_definition_file;

    @API(level = API.Level.secondary, direction=API.Direction.INOUT,
        help = "Path of file containing network (initial) parameters (weights, biases).")
    public String network_parameters_file;

    @API(level = API.Level.secondary, direction=API.Direction.INOUT,
        help = "Path of file containing the mean image data for data normalization.")
    public String mean_image_file;

    @API(level = API.Level.secondary, direction=API.Direction.INOUT,
        help = "Path (prefix) where to export the native model parameters after every iteration.")
    public String export_native_parameters_prefix;

    @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
        help = "If enabled, automatically standardize the data. If disabled, the user must provide properly scaled input data.")
    public boolean standardize;
  }
}
