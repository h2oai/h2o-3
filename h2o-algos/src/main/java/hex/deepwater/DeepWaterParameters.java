package hex.deepwater;

import hex.Model;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import water.H2O;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.Log;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;

import static hex.deepwater.DeepWaterParameters.ProblemType.auto;

/**
 * Parameters for a Deep Water image classification model
 */
public class DeepWaterParameters extends Model.Parameters {
  public String algoName() { return "DeepWater"; }
  public String fullName() { return "Deep Water"; }
  public String javaName() { return DeepWaterModel.class.getName(); }
  @Override protected double defaultStoppingTolerance() { return 0; }
  public DeepWaterParameters() {
    super();
    _stopping_rounds = 5;
  }
  @Override
  public long progressUnits() {
    if (train()==null) return 1;
    return (long)Math.ceil(_epochs*train().numRows());
  }
  public float learningRate(double n) { return (float)(_learning_rate / (1 + _learning_rate_annealing * n)); }
  final public float momentum(double n) {
    double m = _momentum_start;
    if( _momentum_ramp > 0 ) {
      if( n >= _momentum_ramp)
        m = _momentum_stable;
      else
        m += (_momentum_stable - _momentum_start) * n / _momentum_ramp;
    }
    return (float)m;
  }

  public enum Network {
    auto, user, lenet, alexnet, vgg, googlenet, inception_bn, resnet,
  }

  public enum Backend {
    unknown,
    mxnet, caffe, tensorflow, // C++
    xgrpc // anything that speaks grpc
  }

  public enum ProblemType {
    auto, image, text, dataset
  }

  public double _clip_gradient = 10.0;
  public boolean _gpu = true;
  public int[] _device_id = new int[]{0};

  public Network _network = Network.auto;
  public Backend _backend = Backend.mxnet;
  public String _network_definition_file;
  public String _network_parameters_file;
  public String _export_native_parameters_prefix;

  public ProblemType _problem_type = auto;

  // specific parameters for image_classification
  public int[] _image_shape = new int[]{0,0}; //width x height
  public int _channels = 3; //either 1 (monochrome) or 3 (RGB)
  public String _mean_image_file; //optional file with mean image (backend specific)

  /**
   * If enabled, store the best model under the destination key of this model at the end of training.
   * Only applicable if training is not cancelled.
   */
  public boolean _overwrite_with_best_model = true;

  public boolean _autoencoder = false;

  public boolean _sparse = false;

  public boolean _use_all_factor_levels = true;

  public enum MissingValuesHandling {
    MeanImputation, Skip
  }

  public MissingValuesHandling _missing_values_handling = MissingValuesHandling.MeanImputation;

  /**
   * If enabled, automatically standardize the data. If disabled, the user must provide properly scaled input data.
   */
  public boolean _standardize = true;

  /**
   * The number of passes over the training dataset to be carried out.
   * It is recommended to start with lower values for initial experiments.
   * This value can be modified during checkpoint restarts and allows continuation
   * of selected models.
   */
  public double _epochs = 10;

  /**
   * Activation functions
   */
  public enum Activation {
    Rectifier, Tanh
  }

  /**
   * The activation function (non-linearity) to be used the neurons in the hidden layers.
   * Tanh: Hyperbolic tangent function (same as scaled and shifted sigmoid).
   * Rectifier: Chooses the maximum of (0, x) where x is the input value.
   */
  public Activation _activation = null;

  /**
   * The number and size of each hidden layer in the model.
   * For example, if a user specifies "100,200,100" a model with 3 hidden
   * layers will be produced, and the middle hidden layer will have 200
   * neurons.
   */
  public int[] _hidden = null;

  /**
   * A fraction of the features for each training row to be omitted from training in order
   * to improve generalization (dimension sampling).
   */
  public double _input_dropout_ratio = 0.0f;

  /**
   * A fraction of the inputs for each hidden layer to be omitted from training in order
   * to improve generalization. Defaults to 0.5 for each hidden layer if omitted.
   */
  public double[] _hidden_dropout_ratios = null;

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
  public double _learning_rate = 1e-3;

  /**
   * Learning rate annealing reduces the learning rate to "freeze" into
   * local minima in the optimization landscape.  The annealing rate is the
   * inverse of the number of training samples it takes to cut the learning rate in half
   * (e.g., 1e-6 means that it takes 1e6 training samples to halve the learning rate).
   * This parameter is only active if adaptive learning rate is disabled.
   */
  public double _learning_rate_annealing = 1e-6;

  /**
   * The momentum_start parameter controls the amount of momentum at the beginning of training.
   * This parameter is only active if adaptive learning rate is disabled.
   */
  public double _momentum_start = 0.9;

  /**
   * The momentum_ramp parameter controls the amount of learning for which momentum increases
   * (assuming momentum_stable is larger than momentum_start). The ramp is measured in the number
   * of training samples.
   * This parameter is only active if adaptive learning rate is disabled.
   */
  public double _momentum_ramp = 1e4;

  /**
   * The momentum_stable parameter controls the final momentum value reached after momentum_ramp training samples.
   * The momentum used for training will remain the same for training beyond reaching that point.
   * This parameter is only active if adaptive learning rate is disabled.
   */
  public double _momentum_stable = 0.9;


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
   * Enable quiet mode for less output to standard output.
   */
  public boolean _quiet_mode = false;

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
  public boolean _shuffle_training_data = true;

  public int _mini_batch_size = 32;

  public boolean _cache_data = true;

  /**
   * Validate model parameters
   * @param dl DL Model Builder (Driver)
   * @param expensive (whether or not this is the "final" check)
   */
  void validate(DeepWater dl, boolean expensive) {
    boolean classification = expensive || dl.nclasses() != 0 ? dl.isClassifier() : _distribution == DistributionFamily.bernoulli || _distribution == DistributionFamily.bernoulli;
    if (_mini_batch_size < 1)
      dl.error("_mini_batch_size", "Mini-batch size must be >= 1");

    if (_weights_column!=null && expensive) {
      Vec w = (train().vec(_weights_column));
      if (!w.isInt() || w.max() > 1 || w.min() < 0) {
        dl.error("_weights_column", "only supporting weights of 0 or 1 right now");
      }
    }

    if (_clip_gradient<=0)
      dl.error("_clip_gradient", "Clip gradient must be >= 0");

    if (_hidden != null && _network_definition_file != null && !_network_definition_file.isEmpty())
      dl.error("_hidden", "Cannot provide hidden layers and a network definition file at the same time.");

    if (_activation != null && _network_definition_file != null && !_network_definition_file.isEmpty())
      dl.error("_activation", "Cannot provide activation functions and a network definition file at the same time.");

    if (_problem_type == ProblemType.image) {
      if (_image_shape.length != 2)
        dl.error("_image_shape", "image_shape must have 2 dimensions (width, height)");
      if (_image_shape[0] < 0)
        dl.error("_image_shape", "image_shape[0] must be >=1 or automatic (0).");
      if (_image_shape[1] < 0)
        dl.error("_image_shape", "image_shape[1] must be >=1 or automatic (0).");
      if (_channels != 1 && _channels != 3)
        dl.error("_channels", "channels must be either 1 or 3.");
    } else if (_problem_type != auto) {
      dl.warn("_image_shape", "image shape is ignored, only used for image_classification");
      dl.warn("_channels", "channels shape is ignored, only used for image_classification");
      dl.warn("_mean_image_file", "mean_image_file shape is ignored, only used for image_classification");
    }
    if (_categorical_encoding==CategoricalEncodingScheme.Enum) {
      dl.error("_categorical_encoding", "categorical encoding scheme cannot be Enum: the neural network must have numeric columns as input.");
    }

    if (_autoencoder)
      dl.error("_autoencoder", "Autoencoder is not supported right now.");

    if (_network == Network.user) {
      if (_network_definition_file == null || _network_definition_file.isEmpty())
        dl.error("_network_definition_file", "network_definition_file must be provided if the network is user-specified.");
      else if (!new File(_network_definition_file).exists())
        dl.error("_network_definition_file", "network_definition_file " + _network_definition_file + " not found.");
    } else {
      if (_network_definition_file != null && !_network_definition_file.isEmpty() && _network != Network.auto)
        dl.error("_network_definition_file", "network_definition_file cannot be provided if a pre-defined network is chosen.");
    }
    if (_network_parameters_file != null && !_network_parameters_file.isEmpty()) {
      if (!DeepWaterModelInfo.paramFilesExist(_network_parameters_file)) {
        dl.error("_network_parameters_file", "network_parameters_file " + _network_parameters_file + " not found.");
      }
    }
    if (_checkpoint!=null) {
      DeepWaterModel other = (DeepWaterModel) _checkpoint.get();
      if (other == null)
        dl.error("_width", "Invalid checkpoint provided: width mismatch.");
      if (!Arrays.equals(_image_shape, other.get_params()._image_shape))
        dl.error("_width", "Invalid checkpoint provided: width mismatch.");
    }

    if (!_autoencoder) {
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
    if (expensive) dl.checkDistributions();

    if (_score_training_samples < 0)
      dl.error("_score_training_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
    if (_score_validation_samples < 0)
      dl.error("_score_validation_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
    if (classification && dl.hasOffsetCol())
      dl.error("_offset_column", "Offset is only supported for regression.");

    // reason for the error message below is that validation might not have the same horizontalized features as the training data (or different order)
    if (expensive) {
      if (!classification && _balance_classes) {
        dl.error("_balance_classes", "balance_classes requires classification.");
      }
      if (_class_sampling_factors != null && !_balance_classes) {
        dl.error("_class_sampling_factors", "class_sampling_factors requires balance_classes to be enabled.");
      }
      if (_replicate_training_data && null != train() && train().byteSize() > 0.9*H2O.CLOUD.free_mem()/H2O.CLOUD.size() && H2O.CLOUD.size() > 1) {
        dl.error("_replicate_training_data", "Compressed training dataset takes more than 90% of avg. free available memory per node (" + 0.9*H2O.CLOUD.free_mem()/H2O.CLOUD.size() + "), cannot run with replicate_training_data.");
      }
    }
    if (_autoencoder && _stopping_metric != ScoreKeeper.StoppingMetric.AUTO && _stopping_metric != ScoreKeeper.StoppingMetric.MSE) {
      dl.error("_stopping_metric", "Stopping metric must either be AUTO or MSE for autoencoder.");
    }
  }

  /**
   * Attempt to guess the problem type from the dataset
   * @return
   */
  ProblemType guessProblemType() {
    if (_problem_type == auto) {
      boolean image = false;
      boolean text = false;
      String first = null;
      Vec v = train().vec(0);
      if (v.isString() || v.isCategorical() /*small data parser artefact*/) {
        BufferedString bs = new BufferedString();
        first = v.atStr(bs, 0).toString();
        try {
          ImageIO.read(new File(first));
          image = true;
        } catch (Throwable t) {
        }
        try {
          ImageIO.read(new URL(first));
          image = true;
        } catch (Throwable t) {
        }
      }

      if (first != null) {
        if (!image && (first.endsWith(".jpg") || first.endsWith(".png") || first.endsWith(".tif"))) {
          image = true;
          Log.warn("Cannot read first image at " + first + " - Check data.");
        } else if (v.isString() && train().numCols() <= 4) { //at most text, label, fold_col, weight
          text = true;
        }
      }
      if (image) return ProblemType.image;
      else if (text) return ProblemType.text;
      else return ProblemType.dataset;
    } else {
      return _problem_type;
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
        "_quiet_mode",
        "_max_confusion_matrix_size",
        "_max_hit_ratio_k",
        "_diagnostics",
        "_variable_importances",
        "_replicate_training_data",
        "_shuffle_training_data",
        "_single_node_mode",
        "_overwrite_with_best_model",
        "_mini_batch_size",
        "_network_parameters_file",
        "_clip_gradient",
        "_learning_rate",
        "_learning_rate_annealing",
        "_gpu",
        "_sparse",
        "_device_id",
        "_cache_data",
        "_input_dropout_ratio",
        "_hidden_dropout_ratios",
        "_cache_data",
        "_export_native_parameters_prefix",
        "_image_shape", //since it's hard to do equals on this in the check - should not change between checkpoint restarts
    };

    // the following parameters must not be modified when restarting from a checkpoint
    transient static private final String[] cp_not_modifiable = new String[]{
        "_drop_na20_cols",
        "_missing_values_handling",
        "_response_column",
        "_activation",
        "_use_all_factor_levels",
        "_problem_type",
        "_channels",
        "_standardize",
        "_autoencoder",
        "_network",
        "_backend",
        "_momentum_start",
        "_momentum_ramp",
        "_momentum_stable",
        "_ignore_const_cols",
        "_max_categorical_features",
        "_nfolds",
        "_distribution",
        "_network_definition_file",
        "_mean_image_file"
    };

    static void checkCompleteness() {
      for (Field f : hex.deepwater.DeepWaterParameters.class.getDeclaredFields())
        if (!ArrayUtils.contains(cp_not_modifiable, f.getName())
            &&
            !ArrayUtils.contains(cp_modifiable, f.getName())
            ) {
          if (f.getName().equals("_hidden")) continue;
          if (f.getName().equals("_ignored_columns")) continue;
          if (f.getName().equals("$jacocoData")) continue; // If code coverage is enabled
          throw H2O.unimpl("Please add " + f.getName() + " to either cp_modifiable or cp_not_modifiable");
        }
    }

    /**
     * Check that checkpoint continuation is possible
     *
     * @param oldP old DL parameters (from checkpoint)
     * @param newP new DL parameters (user-given, to restart from checkpoint)
     */
    static void checkIfParameterChangeAllowed(final hex.deepwater.DeepWaterParameters oldP, final hex.deepwater.DeepWaterParameters newP) {
      checkCompleteness();
      if (newP._nfolds != 0)
        throw new UnsupportedOperationException("nfolds must be 0: Cross-validation is not supported during checkpoint restarts.");
      if ((newP._valid == null) != (oldP._valid == null)) {
        throw new H2OIllegalArgumentException("Presence of validation dataset must agree with the checkpointed model.");
      }
      if (!newP._autoencoder && (newP._response_column == null || !newP._response_column.equals(oldP._response_column))) {
        throw new H2OIllegalArgumentException("Response column (" + newP._response_column + ") is not the same as for the checkpointed model: " + oldP._response_column);
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
     * @param srcParms source: user-specified parameters
     * @param tgtParms target: parameters to be modified
     * @param doIt     whether to overwrite target parameters (or just print the message)
     * @param quiet    whether to suppress the notifications about parameter changes
     */
    static void updateParametersDuringCheckpointRestart(hex.deepwater.DeepWaterParameters srcParms, hex.deepwater.DeepWaterParameters tgtParms/*actually used during training*/, boolean doIt, boolean quiet) {
      for (Field fTarget : tgtParms.getClass().getFields()) {
        if (ArrayUtils.contains(cp_modifiable, fTarget.getName())) {
          for (Field fSource : srcParms.getClass().getFields()) {
            if (fTarget.equals(fSource)) {
              try {
                if (fSource.get(srcParms) == null || fTarget.get(tgtParms) == null || !fTarget.get(tgtParms).toString().equals(fSource.get(srcParms).toString())) { // if either of the two parameters is null, skip the toString()
                  if (fTarget.get(tgtParms) == null && fSource.get(srcParms) == null)
                    continue; //if both parameters are null, we don't need to do anything
                  if (!tgtParms._quiet_mode && !quiet)
                    Log.info("Applying user-requested modification of '" + fTarget.getName() + "': " + fTarget.get(tgtParms) + " -> " + fSource.get(srcParms));
                  if (doIt)
                    fTarget.set(tgtParms, fSource.get(srcParms));
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
     * Take user-given parameters and turn them into usable, fully populated parameters (e.g., to be used by Neurons during training)
     *
     * @param fromParms raw user-given parameters from the REST API (READ ONLY)
     * @param toParms   modified set of parameters, with defaults filled in (WILL BE MODIFIED)
     * @param nClasses  number of classes (1 for regression or autoencoder)
     */
    static void modifyParms(hex.deepwater.DeepWaterParameters fromParms, hex.deepwater.DeepWaterParameters toParms, int nClasses) {
      if (H2O.CLOUD.size() == 1 && fromParms._replicate_training_data) {
        if (!fromParms._quiet_mode)
          Log.info("_replicate_training_data: Disabling replicate_training_data on 1 node.");
        toParms._replicate_training_data = false;
      }
      // Automatically set the distribution
      if (fromParms._distribution == DistributionFamily.AUTO) {
        // For classification, allow AUTO/bernoulli/multinomial with losses CrossEntropy/Quadratic/Huber/Absolute
        if (nClasses > 1) {
          toParms._distribution = nClasses == 2 ? DistributionFamily.bernoulli : DistributionFamily.multinomial;
        } else {
          toParms._distribution = DistributionFamily.gaussian;
        }
      }
      if (fromParms._single_node_mode && (H2O.CLOUD.size() == 1 || !fromParms._replicate_training_data)) {
        if (!fromParms._quiet_mode)
          Log.info("_single_node_mode: Disabling single_node_mode (only for multi-node operation with replicated training data).");
        toParms._single_node_mode = false;
      }
      if (fromParms._overwrite_with_best_model && fromParms._nfolds != 0) {
        if (!fromParms._quiet_mode)
          Log.info("_overwrite_with_best_model: Disabling overwrite_with_best_model in combination with n-fold cross-validation.");
        toParms._overwrite_with_best_model = false;
      }
      // Automatically set the problem_type
      if (fromParms._problem_type == auto) {
        toParms._problem_type = fromParms.guessProblemType();
        if (!fromParms._quiet_mode)
          Log.info("_problem_type: Automatically selecting problem_type: " + toParms._problem_type.toString());
      }
      if (fromParms._categorical_encoding==CategoricalEncodingScheme.AUTO) {
        if (!fromParms._quiet_mode)
          Log.info("_categorical_encoding: Automatically enabling OneHotInternal categorical encoding.");
        toParms._categorical_encoding = CategoricalEncodingScheme.OneHotInternal;
      }
      if (fromParms._nfolds != 0) {
        if (fromParms._overwrite_with_best_model) {
          if (!fromParms._quiet_mode)
            Log.info("_overwrite_with_best_model: Automatically disabling overwrite_with_best_model, since the final model is the only scored model with n-fold cross-validation.");
          toParms._overwrite_with_best_model = false;
        }
      }
      // automatic selection
      if (fromParms._network == Network.auto || fromParms._network==null) {
        // if the user specified the network, then keep that
        if (fromParms._network_definition_file != null && !fromParms._network_definition_file.equals("")) {
          if (!fromParms._quiet_mode)
            Log.info("_network_definition_file: Automatically setting network type to 'user', since a network definition file was provided.");
          toParms._network = Network.user;
        } else {
          // pick something reasonable
          if (toParms._problem_type == ProblemType.image) toParms._network = Network.inception_bn;
          if (toParms._problem_type == ProblemType.text || toParms._problem_type == ProblemType.dataset) {
            toParms._network = null;
            if (fromParms._hidden == null) {
              toParms._hidden = new int[]{200, 200};
              toParms._activation = Activation.Rectifier;
              toParms._hidden_dropout_ratios = new double[toParms._hidden.length];
            }
          }
          if (!fromParms._quiet_mode && toParms._network != null && toParms._network != Network.user)
            Log.info("_network: Using " + toParms._network + " model by default.");
        }
      }
      if (fromParms._autoencoder && fromParms._stopping_metric == ScoreKeeper.StoppingMetric.AUTO) {
        if (!fromParms._quiet_mode)
          Log.info("_stopping_metric: Automatically setting stopping_metric to MSE for autoencoder.");
        toParms._stopping_metric = ScoreKeeper.StoppingMetric.MSE;
      }
      if (toParms._hidden!=null) {
        if (toParms._hidden_dropout_ratios==null) {
          if (!fromParms._quiet_mode)
            Log.info("_hidden_dropout_ratios: Automatically setting hidden_dropout_ratios to 0 for all layers.");
          toParms._hidden_dropout_ratios = new double[toParms._hidden.length];
        }
        if (toParms._activation==null) {
          toParms._activation = Activation.Rectifier;
          if (!fromParms._quiet_mode)
            Log.info("_activation: Automatically setting activation to " + toParms._activation + " for all layers.");
        }
        if (!fromParms._quiet_mode) {
          Log.info("Hidden layers: " + Arrays.toString(toParms._hidden));
          Log.info("Activation function: " + toParms._activation);
          Log.info("Input dropout ratio: " + toParms._input_dropout_ratio);
          Log.info("Hidden layer dropout ratio: " + Arrays.toString(toParms._hidden_dropout_ratios));
        }
      }
    }
  }
}
