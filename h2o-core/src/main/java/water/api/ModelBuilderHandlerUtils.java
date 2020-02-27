package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.TypeMap;
import water.api.schemas3.ModelParametersSchemaV3;

import java.util.Properties;

public class ModelBuilderHandlerUtils {

  @SuppressWarnings("unchecked")
  static <B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchemaV3> S makeBuilderSchema(
          int version, String algoURLName, Properties parms, B builder
  ) {
    String algoName = ModelBuilder.algoName(algoURLName); // gbm -> GBM; deeplearning -> DeepLearning
    String schemaDir = ModelBuilder.schemaDirectory(algoURLName);

    // Build a Model Schema and a ModelParameters Schema
    String schemaName = schemaDir + algoName + "V" + version;
    S schema = (S) TypeMap.newFreezable(schemaName);
    schema.init_meta();
    String parmName = schemaDir + algoName + "V" + version + "$" + algoName + "ParametersV" + version;
    schema.parameters = (P) TypeMap.newFreezable(parmName);
    schema.parameters.fillFromImpl(builder._parms); // Defaults for this builder into schema
    schema.parameters.fillFromParms(parms);         // Overwrite with user parms
    schema.parameters.fillImpl(builder._parms);     // Merged parms back over Model.Parameter object
    return schema;
  }

  static <B extends ModelBuilder> B makeBuilder(int version, String algoURLName, Properties parms) {
    B builder = ModelBuilder.make(algoURLName, null, null);
    // used for the side effect of populating Parameter object in Builder
    ModelBuilderHandlerUtils.makeBuilderSchema(version, algoURLName, parms, builder);
    return builder;
  }
  
  static String parseAlgoURLName(Route route) {
    // Peek out the desired algo from the URL
    String ss[] = route._url.split("/");
    return ss[3]; // {}/{3}/{ModelBuilders}/{gbm}/{parameters}
  }

}
