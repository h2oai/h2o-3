package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.api.JobsHandler.Jobs;
import water.Job;

abstract public class ModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /**
   * Create a model by launching a ModelBuilder algo.  If the model
   * parameters pass validation this returns a Job schema; if not it
   * returns a ModelParametersSchema containing the validation messages.
   */
  public Schema do_train(int version, S builderSchema) {
    B builder = builderSchema.createAndFillImpl();
    if (builder.error_count() > 0) {
      S errors = (S) Schema.schema(version, builder).fillFromImpl(builder);
      return errors;
    }

    Job j = builder.trainModel();
    return new JobsV2().fillFromImpl(new Jobs(j)); // TODO: version
  }

  public S do_validate_parameters(int version, S builderSchema) {
    B builder = builderSchema.createAndFillImpl();
    S builder_schema = (S) builder.schema().fillFromImpl(builder);
    return builder_schema;
  }
}
