package hex.schemas;

import hex.svm.SVMModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class SVMModelV3 extends ModelSchemaV3<SVMModel, SVMModelV3, SVMModel.SVMParameters, SVMV3.SVMParametersV3, SVMModel.SVMModelOutput, SVMModelV3.SVMModelOutputV3> {

  public static final class SVMModelOutputV3 extends ModelOutputSchemaV3<SVMModel.SVMModelOutput, SVMModelV3.SVMModelOutputV3> {
    @API(help = "Total number of support vectors")
    public long svs_count;
    @API(help = "Number of bounded support vectors")
    public long bsv_count;
    @API(help = "B")
    public double intersect;
    @API(help = "Weights of support vectors")
    public KeyV3.FrameKeyV3 alpha_key;
  } // SVMModelOutputV3

  //==========================
  // Custom adapters go here
  public SVMV3.SVMParametersV3 createParametersSchema() { return new SVMV3.SVMParametersV3(); }
  public SVMModelV3.SVMModelOutputV3 createOutputSchema() { return new SVMModelV3.SVMModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public SVMModel createImpl() {
    SVMModel.SVMParameters parms = parameters.createImpl();
    return new SVMModel( model_id.key(), parms, null);
  }

}
