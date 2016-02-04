package water.api;

import hex.Model;
import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.util.HttpResponseStatus;
import water.util.PojoUtils;
import java.util.Properties;

public class ModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends Handler {
  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  @Override Schema handle(int version, Route route, Properties parms) throws Exception {
    // Peek out the desired algo from the URL
    String ss[] = route._url_pattern_raw.split("/");
    String algoURLName = ss[3]; // {}/{3}/{ModelBuilders}/{gbm}/{parameters}
    String algoName = ModelBuilder.algoName(algoURLName); // gbm -> GBM; deeplearning -> DeepLearning
    String schemaDir = ModelBuilder.schemaDirectory(algoURLName);

    // Build a Model Schema and a ModelParameters Schema
    String schemaName = schemaDir+algoName+"V"+version;
    ModelBuilderSchema schema = (ModelBuilderSchema) TypeMap.newFreezable(schemaName);
    schema.init_meta();
    String parmName = schemaDir+algoName+"V"+version+"$"+algoName+"ParametersV"+version;
    ModelParametersSchema parmSchema = (ModelParametersSchema)TypeMap.newFreezable(parmName);
    schema.parameters = parmSchema;

    // Only here for train or validate-parms
    String handlerName = route._handler_method.getName();
    boolean doTrain = handlerName.equals("train");
    assert doTrain || handlerName.equals("validate_parameters");

    // User specified key, or make a default?
    String model_id = parms.getProperty("model_id");
    Key<Model> key = doTrain ? (Key<Model>)(model_id==null ? ModelBuilder.defaultKey(algoName) : Key.<Model>make(model_id)) : null;
    // Default Job for just this training
    Job job = doTrain ? new Job<>(key,ModelBuilder.javaName(algoURLName),algoName) : null;
    // ModelBuilder
    B builder = ModelBuilder.make(algoURLName,job,key);
    schema.parameters.fillFromImpl(builder._parms); // Defaults for this builder into schema
    schema.parameters.fillFromParms(parms);         // Overwrite with user parms
    schema.parameters.fillImpl(builder._parms);     // Merged parms back over Model.Parameter object
    builder.init(false);          // validate parameters

    _t_start = System.currentTimeMillis();
    if( doTrain ) builder.trainModel();
    _t_stop  = System.currentTimeMillis();

    schema.fillFromImpl(builder); // Fill in the result Schema with the Job at least, plus any extra trainModel errors
    PojoUtils.copyProperties(schema.parameters, builder._parms, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, null, new String[] { "error_count", "messages" });
    schema.setHttpStatus(HttpResponseStatus.OK.getCode());
    return schema;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public S train(int version, S schema) { throw H2O.fail(); }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public S validate_parameters(int version, S schema) { throw H2O.fail(); }
}
