package water.api;

import hex.Model;
import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Job;
import water.Key;
import water.TypeMap;
import water.api.schemas3.ModelParametersSchemaV3;
import water.util.HttpResponseStatus;
import water.util.Log;
import water.util.PojoUtils;

import java.util.Properties;

public class ModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchemaV3> extends Handler {
  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  @Override
  public S handle(int version, Route route, Properties parms, String postBody) throws Exception {
    // Peek out the desired algo from the URL
    String ss[] = route._url.split("/");
    String algoURLName = ss[3]; // {}/{3}/{ModelBuilders}/{gbm}/{parameters}
    String algoName = ModelBuilder.algoName(algoURLName); // gbm -> GBM; deeplearning -> DeepLearning
    String schemaDir = ModelBuilder.schemaDirectory(algoURLName);

    // Build a Model Schema and a ModelParameters Schema
    String schemaName = schemaDir+algoName+"V"+version;
    S schema = (S) TypeMap.newFreezable(schemaName);
    schema.init_meta();
    String parmName = schemaDir+algoName+"V"+version+"$"+algoName+"ParametersV"+version;
    P parmSchema = (P)TypeMap.newFreezable(parmName);
    schema.parameters = parmSchema;

    // Only here for train or validate-parms
    String handlerName = route._handler_method.getName();
    boolean doTrain = handlerName.equals("train");
    assert doTrain || handlerName.equals("validate_parameters");

    // User specified key, or make a default?
    String model_id = parms.getProperty("model_id");
    String warningStr = null;
    if ((model_id != null) && (model_id.contains("/"))) { // found / in model_id, replace with _ and set warning
      String tempName = model_id;
      model_id = model_id.replaceAll("/", "_");
      warningStr = "Bad model_id: slash (/) found and replaced with _.  " + "Original model_id "+tempName +
              " is now "+model_id+".";
      Log.warn("model_id", warningStr);
    }
    Key<Model> key = doTrain ? (model_id==null ? ModelBuilder.defaultKey(algoName) : Key.<Model>make(model_id)) : null;
    // Default Job for just this training
    Job job = doTrain ? (warningStr!=null ? new Job<>(key,ModelBuilder.javaName(algoURLName),algoName, warningStr) :
            new Job<>(key,ModelBuilder.javaName(algoURLName),algoName)) : null;
    // ModelBuilder
    B builder = ModelBuilder.make(algoURLName,job,key);

    schema.parameters.fillFromImpl(builder._parms); // Defaults for this builder into schema
    schema.parameters.fillFromParms(parms);         // Overwrite with user parms
    schema.parameters.fillImpl(builder._parms);     // Merged parms back over Model.Parameter object
    builder.init(false);          // validate parameters
    schema.fillFromImpl(builder); // Fill in the result Schema with the Job at least, plus any extra trainModel errors
    PojoUtils.copyProperties(schema.parameters, builder._parms, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, null, new String[] { "error_count", "messages" });
    schema.setHttpStatus(HttpResponseStatus.OK.getCode());
    if( doTrain ) schema.job.fillFromImpl(builder.trainModelOnH2ONode());
    return schema;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public S train(int version, S schema) { throw H2O.fail(); }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public S validate_parameters(int version, S schema) { throw H2O.fail(); }
}
