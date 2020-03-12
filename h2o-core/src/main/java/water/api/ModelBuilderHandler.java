package water.api;

import hex.Model;
import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Job;
import water.Key;
import water.api.schemas3.ModelParametersSchemaV3;
import water.util.HttpResponseStatus;
import water.util.Log;
import water.util.PojoUtils;

import java.util.Properties;

public class ModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchemaV3> extends Handler {
  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  @Override
  public S handle(int version, Route route, Properties parms, String postBody) throws Exception {
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

    String algoURLName = ModelBuilderHandlerUtils.parseAlgoURLName(route);
    String algoName = ModelBuilder.algoName(algoURLName);

    // Default Job for just this training
    Key<Model> key = doTrain ? (model_id==null ? ModelBuilder.defaultKey(algoName) : Key.make(model_id)) : null;
    Job job = doTrain ? (warningStr!=null ? new Job<>(key, ModelBuilder.javaName(algoURLName), algoName, warningStr) :
            new Job<>(key, ModelBuilder.javaName(algoURLName),algoName)) : null;

    // ModelBuilder
    B builder = ModelBuilder.make(algoURLName,job,key);
    S schema = ModelBuilderHandlerUtils.makeBuilderSchema(version, algoURLName, parms, builder);
    builder.init(false); // Validate parameters
    schema.fillFromImpl(builder);  // Fill in the result Schema with the Job at least, plus any extra trainModel errors
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
