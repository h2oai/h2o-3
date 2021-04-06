package hex.schemas;

import hex.infogram.InfoGramModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class InfoGramModelV99 extends ModelSchemaV3<InfoGramModel, InfoGramModelV99, InfoGramModel.InfoGramParameter, 
        InfoGramV99.INFOGRAMParametersV99, InfoGramModel.InfoGramOutput, InfoGramModelV99.InfoGramOutputV99> {
  public static final class InfoGramOutputV99 extends ModelOutputSchemaV3<InfoGramModel.InfoGramOutput, InfoGramOutputV99> {
    @API(help="Array of conditional mutual information for admissible features normalized to 0.0 and 1.0")
    public double[] admissible_cmi;  // conditional mutual info for admissible features in _admissible_features
    
    @API(help="Array of raw conditional mutual information for admissible features")    
    public double[] admissible_cmi_raw; // raw conditional mutual info for admissible features in _admissible_features

    @API(help="Array of raw conditional mutual information predictor names")
    public String[] admissible_cmi_predictors;  // conditional info for admissible features in _admissible_features
    
    @API(help="Array of variable importance for admissible features")
    public double[] admissible_relevance;  // varimp values for admissible features in _admissible_features
    
    @API(help="Array containing names of admissible features for the user")
    public String[] admissible_features; // predictors chosen that exceeds both conditional_info and varimp thresholds
    
    @API(help="frame key that stores the predictor names, net CMI and relevance")
    String cmi_relevance_key;
  }
  
  public InfoGramV99.INFOGRAMParametersV99 createparametersSchema() { return new InfoGramV99.INFOGRAMParametersV99(); }
  
  public InfoGramOutputV99 createOutputSchema() { return new InfoGramOutputV99(); }
  
  @Override
  public InfoGramModel createImpl() {
    InfoGramModel.InfoGramParameter parms = parameters.createImpl();
    return new InfoGramModel(model_id.key(), parms, null);
  }
}
