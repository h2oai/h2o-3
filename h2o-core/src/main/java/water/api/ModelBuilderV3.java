package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;

/**
 * Job which includes the standard validation error fields, to allow us to capture
 * validation and other errors after the job building task has been forked.  Some of
 * these will come from init(true); others may after the model build really begins.
 * @see H2OModelBuilderErrorV3
 */
public class ModelBuilderV3<J extends ModelBuilder, S extends ModelBuilderV3<J, S>> extends SchemaV3<J, S> {
  @API(help="Model builder parameters.", direction = API.Direction.OUTPUT)
  public ModelParametersSchema parameters;
  
  @API(help="Info, warning and error messages; NOTE: can be appended to while the Job is running", direction=API.Direction.OUTPUT)
  public ValidationMessageBase messages[];

  @API(help="Count of error messages", direction=API.Direction.OUTPUT)
  public int error_count;

  @Override
  public S fillFromImpl(J builder) {
    super.fillFromImpl(builder);

    ModelBuilder.ValidationMessage[] vms = builder._messages;
    this.messages = new ValidationMessageBase[vms.length];
    for( int i=0; i<vms.length; i++ )
      this.messages[i] = new ValidationMessageV3().fillFromImpl(vms[i]); // TODO: version // Note: does default field_name mapping
    // default fieldname hacks
    ValidationMessageBase.mapValidationMessageFieldNames(this.messages, new String[]{"_train", "_valid"}, new String[]{"training_frame", "validation_frame"});
    this.error_count = builder.error_count();

    ModelBuilderSchema s = (ModelBuilderSchema)SchemaServer.schema(this.getSchemaVersion(), builder).fillFromImpl(builder);
    parameters = s.parameters;
    return (S) this;
  }
}
