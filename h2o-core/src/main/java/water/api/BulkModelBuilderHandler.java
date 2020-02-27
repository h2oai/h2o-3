package water.api;

import hex.ModelBuilder;
import water.api.schemas3.SegmentModelsParametersV3;
import hex.segments.SegmentModels;
import hex.segments.SegmentModelsBuilder;
import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Job;
import water.api.schemas3.JobV3;
import water.api.schemas3.ModelParametersSchemaV3;

import java.util.Properties;

public class BulkModelBuilderHandler<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchemaV3> extends Handler {

  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  @Override
  public JobV3 handle(int version, Route route, Properties parms, String postBody) {
    if (! "bulk_train".equals(route._handler_method.getName())) {
      throw new IllegalStateException("Only supports `bulk_train` handler method");
    }

    Properties modelParms = new Properties();
    SegmentModelsBuilder.SegmentModelsParameters smParms = new SegmentModelsParametersV3()
            .fillFromParms(parms, modelParms, true)
            .fillImpl(new SegmentModelsBuilder.SegmentModelsParameters());
    
    final String algoURLName = ModelBuilderHandlerUtils.parseAlgoURLName(route);
    final B builder = ModelBuilderHandlerUtils.makeBuilder(version, algoURLName, modelParms);

    Job<SegmentModels> job = new SegmentModelsBuilder(smParms, builder._parms).buildSegmentModels();

    JobV3 schema = new JobV3();
    schema.fillFromImpl(job);
    return schema;
  }

  @SuppressWarnings("unused") // formally required but never actually called because handle() is overridden
  public S bulk_train(int version, S schema) { throw H2O.fail(); }

}
