package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.Job;

/**
 * Job which includes the standard validation error fields, to allow us to capture
 * validation and other errors after the job building task has been forked.  Some of
 * these will come from init(true); others may after the model build really begins.
 * @see H2OModelBuilderErrorV3
 */
public class ModelBuilderJobV3<J extends ModelBuilder, S extends ModelBuilderJobV3<J, S>> extends JobV3<J, S> {
  @API(help="Model builder parameters.", direction = API.Direction.OUTPUT)
  public ModelParametersSchema parameters;
  
  @Override
  public S fillFromImpl(ModelBuilder builder) {
    super.fillFromImpl((Job)builder);

    this.messages = new ValidationMessageBase[builder._messages.length];
    int i = 0;
    for( ModelBuilder.ValidationMessage vm : builder._messages ) {
      this.messages[i++] = new ValidationMessageV3().fillFromImpl(vm); // TODO: version // Note: does default field_name mapping
    }
    // default fieldname hacks
    ValidationMessageBase.mapValidationMessageFieldNames(this.messages, new String[]{"_train", "_valid"}, new String[]{"training_frame", "validation_frame"});
    this.error_count = builder.error_count();

    ModelBuilderSchema s = (ModelBuilderSchema)Schema.schema(this.getSchemaVersion(), builder).fillFromImpl(builder);
    parameters = s.parameters;
    return (S) this;
  }
}
