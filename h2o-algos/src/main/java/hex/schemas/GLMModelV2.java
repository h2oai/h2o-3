package hex.schemas;

import hex.glm.GLMModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

import water.util.ArrayUtils;
import water.api.TwoDimTableBase;
import water.util.ModelUtils;
import water.util.TwoDimTable;

import java.util.Arrays;
//import water.util.DocGen.HTML;

public class GLMModelV2 extends ModelSchema<GLMModel, GLMModelV2, GLMModel.GLMParameters, GLMV2.GLMParametersV2, GLMModel.GLMOutput, GLMModelV2.GLMModelOutputV2> {

  public static final class GLMModelOutputV2 extends ModelOutputSchema<GLMModel.GLMOutput, GLMModelOutputV2> {
    // Output fields; input fields are in the parameters list

    // Submodel [] _submodels;

    @API(help="bets lambda if doing lambda search")
    int         best_lambda_idx;

    @API(help="The decision threshold to be used in classification")
    float       threshold;

//    @API(help="fill me in GLMModelOutputV2")
//    double   [] global_beta;
//
//    @API(help="fill me in GLMModelOutputV2")
//    String   [] coefficient_names;

    @API(help="Table of coefficients")
    TwoDimTableBase coefficients_table;

    @API(help="Coefficient magnitudes")
    TwoDimTableBase coefficients_magnitude;

    @API(help="Residual Deviance - Training")
    double      residual_deviance;
      
    @API(help="Null Deviance - Training")
    double      null_deviance;
      
    @API(help="Residual Degrees of Freedom - Training")
    double      residual_degrees_of_freedom;
      
    @API(help="Null Degrees of Freedom - Training")
    double      null_degrees_of_freedom;

    @API(help="AIC - Training")
    double      aic;

    @API(help="AUC - Training")
    double      auc;

//    @API(help="Residual Deviance - Validation")
//    double      validation_residual_deviance;
//
//    @API(help="Null Deviance - Validation")
//    double      validation_null_deviance;
//
//    @API(help="Residual Degrees of Freedom - Validation")
//    double      validation_residual_degrees_of_freedom;
//
//    @API(help="Null Degrees of Freedom - Validation")
//    double      validation_null_degrees_of_freedom;

    @API(help="fill me in GLMModelOutputV2; I think I'm redundant")
    boolean binomial; // TODO: isn't this redundant, given model_category?

    @API(help="fill me in GLMModelOutputV2")
    int rank;

    @Override
    public GLMModelOutputV2 fillFromImpl(GLMModel.GLMOutput impl) {
      super.fillFromImpl(impl);
      this.rank = impl.rank();
      GLMModel.Submodel sm = impl.bestSubmodel();
      String [] cnames = impl.coefficientNames();
      System.out.println("CNAMES = " + Arrays.toString(cnames));
      String [] names = sm.idxs == null?impl.coefficientNames().clone():ArrayUtils.select(impl.coefficientNames(), sm.idxs);
      coefficients_table = new TwoDimTableBase();
      coefficients_magnitude = new TwoDimTableBase();
      if(sm.norm_beta != null){
        // coefficients_table = new TwoDimTable("Coefficients",impl._names,impl.isNormalized()? new String[]{"Coefficients, Normalized Coefficients"}: new String[]{"Coefficients"});
        String [] colTypes = new String[]{"double","double"};
        String [] colFormats = new String[]{"%5f", "%5f"};
        TwoDimTable tdt = new TwoDimTable("Coefficients","glm coefficients", names, new String[]{"Coefficients", "Norm Coefficients"}, colTypes, colFormats, "names");
        for(int i = 0; i < sm.beta.length; ++i) {
          tdt.set(i, 0, sm.beta[i]);
          tdt.set(i, 1, sm.norm_beta[i]);
        }
        coefficients_table.fillFromImpl(tdt);
        double [] magnitudes = sm.norm_beta.clone();
        for(int i = 0; i < magnitudes.length; ++i)
          if(magnitudes[i] < 0) magnitudes[i] *= -1;
        coefficients_magnitude.fillFromImpl(new TwoDimTable("Coefficient Magnitudes","(standardized) coefficient magnitudes", names, new String[]{"Coefficients"},new String[]{"double"},new String[]{"%5f"},"names"));
      } else {
        // coefficients_table = new TwoDimTable("Coefficients",impl._names,impl.isNormalized()? new String[]{"Coefficients, Normalized Coefficients"}: new String[]{"Coefficients"});
        String [] colTypes = new String[]{"double"};
        String [] colFormats = new String[]{"%5f"};
        TwoDimTable tdt = new TwoDimTable("Coefficients","glm coefficients", names, new String[]{"Coefficients"}, colTypes, colFormats, "names");
        for(int i = 0; i < sm.beta.length; ++i)
          tdt.set(i,0,sm.beta[i]);

        coefficients_table.fillFromImpl(tdt);
        double [] magnitudes = sm.beta.clone();
        for(int i = 0; i < magnitudes.length; ++i)
          if(magnitudes[i] < 0) magnitudes[i] *= -1;
        coefficients_magnitude.fillFromImpl(new TwoDimTable("Coefficient Magnitudes","coefficient magnitudes", names, new String[]{"Coefficients"},colTypes,colFormats,"names"));
      }
      return this;
    }
  } // GLMModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public GLMV2.GLMParametersV2 createParametersSchema() { return new GLMV2.GLMParametersV2(); }
  public GLMModelOutputV2 createOutputSchema() { return new GLMModelOutputV2(); }

  // TODO: revisit; we want an empty impl here. . .
  @Override public GLMModel createImpl() {
    GLMModel.GLMParameters parms = parameters.createImpl();
    return new GLMModel( key.key(), parms, new GLMModel.GLMOutput(), null, 0.0, 0.0, 0, ModelUtils.DEFAULT_THRESHOLDS);
  }
}
