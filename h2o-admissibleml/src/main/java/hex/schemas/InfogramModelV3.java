package hex.schemas;

import hex.Infogram.InfogramModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;


public class InfogramModelV3 extends ModelSchemaV3<InfogramModel, InfogramModelV3, InfogramModel.InfogramParameters,
        InfogramV3.InfogramParametersV3, InfogramModel.InfogramModelOutput, InfogramModelV3.InfogramModelOutputV3> {
  public static final class InfogramModelOutputV3 extends ModelOutputSchemaV3<InfogramModel.InfogramModelOutput, InfogramModelOutputV3> {
    @API(help="Array of conditional mutual information for admissible features normalized to 0.0 and 1.0", 
            direction = API.Direction.OUTPUT)
    public double[] admissible_cmi;  // conditional mutual info for admissible features in _admissible_features

    @API(help="Array of conditional mutual information for admissible features raw and not normalized to 0.0 and 1.0",
            direction = API.Direction.OUTPUT)
    public double[] admissible_cmi_raw;  // raw conditional mutual info for admissible features in _admissible_features

    @API(help="Array of variable importance for admissible features", direction = API.Direction.OUTPUT)
    public double[] admissible_relevance;  // varimp values for admissible features in _admissible_features

    @API(help="Array containing names of admissible features for the user", direction = API.Direction.OUTPUT)
    public String[] admissible_features; // predictors chosen that exceeds both conditional_info and varimp thresholds
      
    @API(help="Array containing names of admissible features for the user from the validation dataset.", 
            direction = API.Direction.OUTPUT)
    public String[] admissible_features_valid; // predictors chosen that exceeds both conditional_info and varimp thresholds    

    @API(help="Array containing names of admissible features for the user from cross-validation.",
            direction = API.Direction.OUTPUT)
    public String[] admissible_features_xval; // predictors chosen that exceeds both conditional_info and varimp thresholds 
      
    @API(help="Array of raw conditional mutual information for all features excluding sensitive attributes if " +
            "applicable", direction = API.Direction.OUTPUT)
    public double[] cmi_raw; // cmi before normalization and for all predictors

    @API(help="Array of conditional mutual information for all features excluding sensitive attributes if applicable " +
            "normalized to 0.0 and 1.0", direction = API.Direction.OUTPUT)
    public double[] cmi;

    @API(help="Array containing names of all features excluding sensitive attributes if applicable corresponding to CMI" +
            " and relevance", direction = API.Direction.OUTPUT)
    public String[] all_predictor_names;

    @API(help="Array of variable importance for all features excluding sensitive attributes if applicable", 
            direction = API.Direction.OUTPUT)
    public double[] relevance; // variable importance for all predictors
      
    @API(help="Frame key that stores the predictor names, net CMI and relevance.", direction = API.Direction.OUTPUT)
    KeyV3.FrameKeyV3 relevance_cmi_key;

    @API(help="Frame key that stores the predictor names, net CMI and relevance calculated from validation dataset.",
            direction = API.Direction.OUTPUT)
    KeyV3.FrameKeyV3 relevance_cmi_key_valid;

    @API(help="Frame key that stores the predictor names, net CMI and relevance from cross-validation.", 
            direction = API.Direction.OUTPUT)
    KeyV3.FrameKeyV3 relevance_cmi_key_xval;
  }

  public InfogramV3.InfogramParametersV3 createParametersSchema() { return new InfogramV3.InfogramParametersV3(); }

  public InfogramModelOutputV3 createOutputSchema() { return new InfogramModelOutputV3(); }

  @Override
  public InfogramModel createImpl() {
    InfogramModel.InfogramParameters parms = parameters.createImpl();
    return new InfogramModel(model_id.key(), parms, null);
  }
}
