package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.Job;
import water.api.JobsHandler.Jobs;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.util.HttpResponseStatus;

abstract public class ModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends Handler {
  /**
   * Create a model by launching a ModelBuilder algo.  If the model
   * parameters pass validation this returns a Job schema; if not it
   * returns a ModelParametersSchema containing the validation messages.
   */
  public JobsV2 do_train(int version, S builderSchema) {
    B builder = builderSchema.createAndFillImpl();
    if (builder.error_count() > 0) {
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(builder);
    }

    Job j = builder.trainModel();
    return new JobsV2().fillFromImpl(new Jobs(j)); // TODO: version
  }

  public S do_validate_parameters(int version, S builderSchema) {
    B builder = builderSchema.createAndFillImpl();
    S builder_schema = (S) builder.schema().fillFromImpl(builder);
    builder_schema.setHttpStatus(HttpResponseStatus.OK.getCode());
    return builder_schema;
  }
}
