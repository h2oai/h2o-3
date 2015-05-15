package water.api;

import hex.SupervisedModel;
import water.api.FrameV3.ColSpecifierV3;

/**
 * An instance of a SupervisedModelParameters schema contains the common SupervisedModel build parameters (e.g., response_column).
 */
public class SupervisedModelParametersSchema<P extends SupervisedModel.SupervisedParameters, S extends SupervisedModelParametersSchema<P, S>> extends ModelParametersSchema<P, S> {

  static public String[] own_fields = new String[]{"response_column", "balance_classes", "class_sampling_factors", "max_after_balance_size", "max_confusion_matrix_size", "max_hit_ratio_k"};

  // TODO: pass these as a new helper class that contains frame and vec; right now we have no automagic way to
  // know which frame a Vec name corresponds to, so there's hardwired logic in the adaptor which knows that these
  // column names are related to training_frame.
  @API(help = "Response column", is_member_of_frames = {"training_frame", "validation_frame"}, is_mutually_exclusive_with = {"ignored_columns"}, direction = API.Direction.INOUT)
  public ColSpecifierV3 response_column;

  /*Imbalanced Classes*/
  /**
   * For imbalanced data, balance training data class counts via
   * over/under-sampling. This can result in improved predictive accuracy.
   */
  @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data).", level = API.Level.secondary, direction = API.Direction.INOUT)
  public boolean balance_classes;

  /**
   * Desired over/under-sampling ratios per class (lexicographic order).
   * Only when balance_classes is enabled.
   * If not specified, they will be automatically computed to obtain class balance during training.
   */
  @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will be automatically computed to obtain class balance during training. Requires balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT)
  public float[] class_sampling_factors;

  /**
   * When classes are balanced, limit the resulting dataset size to the
   * specified multiple of the original dataset size.
   */
  @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0). Requires balance_classes.", /* dmin=1e-3, */ level = API.Level.expert, direction = API.Direction.INOUT)
  public float max_after_balance_size;

  /** For classification models, the maximum size (in terms of classes) of
   *  the confusion matrix for it to be printed. This option is meant to
   *  avoid printing extremely large confusion matrices.  */
  @API(help = "Maximum size (# classes) for confusion matrices to be printed in the Logs", level = API.Level.secondary, direction = API.Direction.INOUT)
  public int max_confusion_matrix_size;

  /**
   * The maximum number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)
   */
  @API(help = "Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT)
  public int max_hit_ratio_k;
}