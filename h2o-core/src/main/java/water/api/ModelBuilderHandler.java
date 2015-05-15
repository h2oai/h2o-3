package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.Job;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.util.HttpResponseStatus;
import water.util.PojoUtils;

abstract public class ModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends Handler {
  /**
   * Create a model by launching a ModelBuilder algo.  If the model
   * parameters pass validation this returns a Job schema; if not it
   * returns a ModelParametersSchema containing the validation messages.
   */
  public S do_train(int version, S builderSchema) {
    // Note: the create can detect errors through init(false), OR the trainModel() call
    // can throw an exception deep inside a ForkJoin task
    // (cf. DeepLearningDriver.compute2()).  Both are to be handled here, the first by
    // throwing if there are errors from the fill, and the second if the model build
    // throws.
    B builder = builderSchema.createAndFillImpl();
    if (builder.error_count() > 0) {
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(builder);
    }

    Job j = builder.trainModel();
    builderSchema.job = (JobV3) Schema.schema(version, Job.class).fillFromImpl(j); // TODO: version

    // copy warnings and infos; errors will cause an H2OModelBuilderIllegalArgumentException to be thrown above,
    // resulting in an H2OErrorVx to be returned.
    PojoUtils.copyProperties(builderSchema.parameters, builder._parms, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, null, new String[] { "error_count", "messages" });
    builderSchema.setHttpStatus(HttpResponseStatus.OK.getCode());
    return builderSchema;
  }

  public S do_validate_parameters(int version, S builderSchema) {
    B builder = builderSchema.createAndFillImpl();
    S builder_schema = (S) builder.schema().fillFromImpl(builder);
    builder_schema.setHttpStatus(HttpResponseStatus.OK.getCode());
    return builder_schema;
  }
}
