package water.api;

import hex.SupervisedModel;

/**
 * An instance of a SupervisedModelParameters schema contains the common SupervisedModel build parameters (e.g., response_column).
 */
abstract public class SupervisedModelParametersSchema<P extends SupervisedModel.SupervisedParameters, S extends SupervisedModelParametersSchema<P, S>> extends ModelParametersSchema<P, S> {

  static public String[] own_fields = new String[] { "response_column", "do_classification", "balance_classes", "max_after_balance_size" };

  // TODO: pass these as a new helper class that contains frame and vec; right now we have no automagic way to
  // know which frame a Vec name corresponds to, so there's hardwired logic in the adaptor which knows that these
  // column names are related to training_frame.
  @API(help="Response column", direction=API.Direction.INOUT)
  public String response_column;

  @API(help="Convert the response column to an enum (forcing a classification instead of a regression) if needed.", direction=API.Direction.INOUT)
  public boolean do_classification;

  @API(help="Upsample the minority classes to balance the class distribution?", direction=API.Direction.INOUT)
  public boolean balance_classes;

  @API(help="When classes are being balanced, limit the resulting dataset size to the specified multiple of the original dataset size.", direction=API.Direction.INOUT)
  public float max_after_balance_size;

  @Override
  public S fillFromImpl(P impl) {
    super.fillFromImpl(impl);

    this.do_classification = impl._convert_to_enum;
    return (S)this;
  }

  @Override
  public P fillImpl(P impl) {
    super.fillImpl(impl);

    impl._convert_to_enum = this.do_classification;
    return impl;
  }
}
