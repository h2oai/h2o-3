package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Job;
import water.TypeMap;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.util.HttpResponseStatus;
import water.util.PojoUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

public class ModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends Handler {
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

  
    // CNC: Override this in BuilderHandler,
    // (1) peel out the 'this' class: GBMBuilderHandler,
    // (2) Peel out the "GBM"
    // (3) Make a GBMV3 via TypeMap
    // (4) Either finish out here, or call fillFromParms, then GBMV3.method
  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  @Override Schema handle(int version, Route route, Properties parms) throws Exception {
    // Peek out the desired algo from the URL
    String ss[] = route._url_pattern_raw.split("/");
    String algoURLName = ss[3]; // {}/{3}/{ModelBuilders}/{gbm}/{parameters}
    String algoJavaName = ModelBuilder.algoJavaName(algoURLName); // gbm -> GBM; deeplearning -> DeepLearning
    String schemaName = "hex.schemas."+algoJavaName+"V"+version;
    ModelBuilderSchema schema = (ModelBuilderSchema) TypeMap.newFreezable(schemaName);
    String parmName = "hex.schemas."+algoJavaName+"V"+version+"$"+algoJavaName+"ParametersV"+version;
    ModelParametersSchema parmSchema = (ModelParametersSchema)TypeMap.newFreezable(parmName);
    schema.parameters = parmSchema;

    //water.Iced defaults = schema.createImpl();
    //schema.fillFromImpl(defaults);
    schema = schema.fillFromParms(parms);

    // Run the Handler in the GUI Thread (nano does not grok CPS!)
    // NOTE! The handler method is free to modify the input schema and hand it back.
    _t_start = System.currentTimeMillis();
    try {
      route._handler_method.setAccessible(true);
      return (Schema)route._handler_method.invoke(this, version, schema);
    }
    // Exception throws out of the invoked method turn into InvocationTargetException
    // rather uselessly.  Peel out the original exception & throw it.
    catch( InvocationTargetException ite ) {
      Throwable t = ite.getCause();
      if( t instanceof RuntimeException ) throw (RuntimeException)t;
      if( t instanceof Error ) throw (Error)t;
      throw new RuntimeException(t);
    } finally {
      _t_stop  = System.currentTimeMillis();
    }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public void train(int version, S schema) {
    throw H2O.unimpl();
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public S validate_parameters(int version, S schema) {
    B builder = schema.createAndFillImpl();
    S builder_schema = (S) builder.schema().fillFromImpl(builder);
    builder_schema.setHttpStatus(HttpResponseStatus.OK.getCode());
    return builder_schema;
  }
}
