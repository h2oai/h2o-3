package hex.schemas;

import hex.anovaglm.AnovaGLMModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;
import water.util.TwoDimTable;

import static hex.gam.MatrixFrameUtils.GAMModelUtils.copyTwoDimTable;

public class AnovaGLMModelV3 extends ModelSchemaV3<AnovaGLMModel, AnovaGLMModelV3, AnovaGLMModel.AnovaGLMParameters,
        AnovaGLMV3.AnovaGLMParametersV3, AnovaGLMModel.AnovaGLMModelOutput, AnovaGLMModelV3.AnovaGLMModelOutputV3> {
  public static final class AnovaGLMModelOutputV3 extends ModelOutputSchemaV3<AnovaGLMModel.AnovaGLMModelOutput, AnovaGLMModelOutputV3> {
    @API(help="Table of Coefficients")
    TwoDimTableV3[] coefficients_table; // from all models

    @API(help="AnovaGLM transformed predictor frame key.  For debugging purposes only")
    String transformed_columns_key;

    @Override
    public AnovaGLMModelOutputV3 fillFromImpl(AnovaGLMModel.AnovaGLMModelOutput impl) {
      super.fillFromImpl(impl);
      coefficients_table = new TwoDimTableV3[impl._coefficients_table.length];
      for (int index = 0; index < coefficients_table.length; index++) {
        TwoDimTable temp = copyTwoDimTable(impl._coefficients_table[index], 
                impl._coefficients_table[index].getTableHeader());
        coefficients_table[index] = new TwoDimTableV3();
        coefficients_table[index].fillFromImpl(temp);
      }
      return this;
    }
  }
  public AnovaGLMV3.AnovaGLMParametersV3 createParametersSchema() { return new AnovaGLMV3.AnovaGLMParametersV3();}
  public AnovaGLMModelOutputV3 createOutputSchema() { return new AnovaGLMModelOutputV3();}
  
  
  @Override
  public AnovaGLMModel createImpl() {
    AnovaGLMModel.AnovaGLMParameters parms = parameters.createImpl();
    return new AnovaGLMModel(model_id.key(), parms, null);
  }
}
