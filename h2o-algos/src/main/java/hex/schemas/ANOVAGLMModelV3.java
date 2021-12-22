package hex.schemas;

import hex.anovaglm.ANOVAGLMModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;
import water.fvec.Frame;
import water.util.TwoDimTable;

import static hex.gam.MatrixFrameUtils.GAMModelUtils.copyTwoDimTable;

public class ANOVAGLMModelV3 extends ModelSchemaV3<ANOVAGLMModel, ANOVAGLMModelV3, ANOVAGLMModel.ANOVAGLMParameters,
        ANOVAGLMV3.ANOVAGLMParametersV3, ANOVAGLMModel.ANOVAGLMModelOutput, ANOVAGLMModelV3.ANOVAGLMModelOutputV3> {
  public static final class ANOVAGLMModelOutputV3 extends ModelOutputSchemaV3<ANOVAGLMModel.ANOVAGLMModelOutput, ANOVAGLMModelOutputV3> {
    @API(help="Table of Coefficients")
    TwoDimTableV3[] coefficients_table; // from all models

    @API(help="AnovaGLM transformed predictor frame key.  For debugging purposes only")
    KeyV3.FrameKeyV3 transformed_columns_key;

    @API(help="ANOVA table frame key containing Type III SS calculation, degree of freedom, F-statistics and " +
            "p-values.  This frame content is repeated in the model summary.")
    KeyV3.FrameKeyV3 result_frame_key;

    @Override
    public ANOVAGLMModelOutputV3 fillFromImpl(ANOVAGLMModel.ANOVAGLMModelOutput impl) {
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
  public ANOVAGLMV3.ANOVAGLMParametersV3 createParametersSchema() { return new ANOVAGLMV3.ANOVAGLMParametersV3();}
  public ANOVAGLMModelOutputV3 createOutputSchema() { return new ANOVAGLMModelOutputV3();}
  
  
  @Override
  public ANOVAGLMModel createImpl() {
    ANOVAGLMModel.ANOVAGLMParameters parms = parameters.createImpl();
    return new ANOVAGLMModel(model_id.key(), parms, null);
  }
}
