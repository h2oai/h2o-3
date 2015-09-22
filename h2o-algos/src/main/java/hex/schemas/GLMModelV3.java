package hex.schemas;

import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

import water.util.ArrayUtils;
import water.api.TwoDimTableBase;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Comparator;
//import water.util.DocGen.HTML;

public class GLMModelV3 extends ModelSchema<GLMModel, GLMModelV3, GLMModel.GLMParameters, GLMV3.GLMParametersV3, GLMModel.GLMOutput, GLMModelV3.GLMModelOutputV3> {

  public static final class GLMModelOutputV3 extends ModelOutputSchema<GLMModel.GLMOutput, GLMModelOutputV3> {

    @API(help="Table of Coefficients")
    TwoDimTableBase coefficients_table;

    @API(help="Standardized Coefficient Magnitudes")
    TwoDimTableBase standardized_coefficient_magnitudes;



    private GLMModelOutputV3 fillMultinomial(GLMOutput impl) {
      String [] names = impl.coefficientNames().clone();
      // put intercept as the first
      String [] ns = ArrayUtils.append(new String[]{"Intercept"},Arrays.copyOf(names,names.length-1));
      coefficients_table = new TwoDimTableBase();
      if(impl.isStandardized()){

        // coefficients_table = new TwoDimTable("Coefficients",impl._names,impl.isNormalized()? new String[]{"Coefficients, Normalized Coefficients"}: new String[]{"Coefficients"});
        String [] colTypes = new String[]{"double","double"};
        String [] colFormats = new String[]{"%5f", "%5f"};
        int n = impl.nclasses();
        String [] cols = new String[n*2];
        for(int i = 0; i < n; ++i) {
          cols[i] = "Coefs_class_" + i;
          cols[n+i] = "Std_Coefs_class_" + i;
        }
        colTypes = new String[cols.length];
        Arrays.fill(colTypes, "double");
        colFormats = new String[cols.length];
        Arrays.fill(colFormats,"%5f");

        TwoDimTable tdt = new TwoDimTable("Coefficients","glm multinomial coefficients", ns, cols, colTypes, colFormats, "names");
        double [][] betaNorm = impl.getNormBetaMultinomial();

        for(int c = 0; c < n; ++c) {
          double [] beta = impl.get_global_beta_multinomial()[c];
          tdt.set(0,c,beta[beta.length-1]);
          tdt.set(0,n + c,betaNorm[c][beta.length-1]);
          for(int i = 0; i < beta.length-1; ++i) {
            tdt.set(i + 1, c, beta[i]);
            tdt.set(i + 1, n + c, betaNorm[c][i]);
          }
        }
        coefficients_table.fillFromImpl(tdt);
        final double [] magnitudes = new double[betaNorm[0].length];
        for(int i = 0; i < betaNorm.length; ++i) {
          for (int j = 0; j < betaNorm[i].length; ++j) {
            double d = betaNorm[i][j];
            magnitudes[j] += d < 0 ? -d : d;
          }
        }
        Integer [] indices = new Integer[magnitudes.length-1];
        for(int i = 0; i < indices.length; ++i)
          indices[i] = i;
        Arrays.sort(indices, new Comparator<Integer>() {
          @Override
          public int compare(Integer o1, Integer o2) {
            if(magnitudes[o1] < magnitudes[o2]) return +1;
            if(magnitudes[o1] > magnitudes[o2]) return -1;
            return 0;
          }
        });
        String [] names2 = new String[names.length];
        for(int i = 0; i < names2.length-1; ++i)
          names2[i] = names[indices[i]];
        tdt = new TwoDimTable("Standardized Coefficient Magnitudes", "standardized coefficient magnitudes", names2, new String[]{"Coefficients", "Sign"}, new String[]{"double", "string"}, new String[]{"%5f", "%s"}, "names");
        for (int i = 0; i < magnitudes.length - 1; ++i) {
          tdt.set(i, 0, magnitudes[indices[i]]);
          tdt.set(i, 1, "POS");
        }
        standardized_coefficient_magnitudes = new TwoDimTableBase();
        standardized_coefficient_magnitudes.fillFromImpl(tdt);
      } //todo
//      else {
//        // coefficients_table = new TwoDimTable("Coefficients",impl._names,impl.isNormalized()? new String[]{"Coefficients, Normalized Coefficients"}: new String[]{"Coefficients"});
//        String [] colTypes = new String[]{"double"};
//        String [] colFormats = new String[]{"%5f"};
//        TwoDimTable tdt;
//        if(impl._multinomial) {
//          int n = impl.nclasses();
//          String [] cols = new String[n];
//          for(int i = 0; i < n; ++i)
//            cols[i] = "Coefficients." + i;
//          colTypes = new String[cols.length];
//          Arrays.fill(colTypes,"double");
//          colFormats = new String[cols.length];
//          Arrays.fill(colFormats,"%5f");
//          tdt = new TwoDimTable("Coefficients","glm multinomial coefficients", ns, cols, colTypes, colFormats, "names");
//        } else {
//          tdt = new TwoDimTable("Coefficients", "glm coefficients", ns, new String[]{"Coefficients"}, colTypes, colFormats, "names");
//          tdt.set(0, 0, beta[beta.length - 1]);
//          for (int i = 0; i < beta.length - 1; ++i) {
//            tdt.set(i + 1, 0, beta[i]);
//          }
//        }
//        coefficients_table.fillFromImpl(tdt);
//        magnitudes = beta.clone();
//        for(int i = 0; i < magnitudes.length-1; ++i)
//          if(magnitudes[i] < 0) magnitudes[i] *= -1;
//      }
      return this;
    }
    @Override
    public GLMModelOutputV3 fillFromImpl(GLMModel.GLMOutput impl) {
      super.fillFromImpl(impl);
      if(impl._multinomial)
        return fillMultinomial(impl);
      String [] names = impl.coefficientNames().clone();
      // put intercept as the first
      String [] ns = ArrayUtils.append(new String[]{"Intercept"},Arrays.copyOf(names,names.length-1));
      coefficients_table = new TwoDimTableBase();
      final double [] magnitudes;
      double [] beta = impl.beta();
      if(impl.isStandardized()){
        double [] norm_beta = impl.getNormBeta();
        // coefficients_table = new TwoDimTable("Coefficients",impl._names,impl.isNormalized()? new String[]{"Coefficients, Normalized Coefficients"}: new String[]{"Coefficients"});
        String [] colTypes = new String[]{"double","double"};
        String [] colFormats = new String[]{"%5f", "%5f"};
        TwoDimTable tdt = new TwoDimTable("Coefficients","glm coefficients", ns, new String[]{"Coefficients", "Standardized Coefficients"}, colTypes, colFormats, "names");
        tdt.set(0,0,beta[beta.length-1]);
        tdt.set(0,1,norm_beta[norm_beta.length-1]);
        for(int i = 0; i < beta.length-1; ++i) {
          tdt.set(i+1, 0, beta[i]);
          tdt.set(i+1, 1, norm_beta[i]);
        }
        coefficients_table.fillFromImpl(tdt);
        magnitudes = norm_beta.clone();
        for(int i = 0; i < magnitudes.length; ++i)
          if(magnitudes[i] < 0) magnitudes[i] *= -1;
        Integer [] indices = new Integer[magnitudes.length-1];
        for(int i = 0; i < indices.length; ++i)
          indices[i] = i;
        Arrays.sort(indices, new Comparator<Integer>() {
          @Override
          public int compare(Integer o1, Integer o2) {
            if(magnitudes[o1] < magnitudes[o2]) return +1;
            if(magnitudes[o1] > magnitudes[o2]) return -1;
            return 0;
          }
        });
        String [] names2 = new String[names.length];
        for(int i = 0; i < names2.length-1; ++i)
          names2[i] = names[indices[i]];
        tdt = new TwoDimTable("Standardized Coefficient Magnitudes", "standardized coefficient magnitudes", names2, new String[]{"Coefficients", "Sign"}, new String[]{"double", "string"}, new String[]{"%5f", "%s"}, "names");
        for (int i = 0; i < beta.length - 1; ++i) {
          tdt.set(i, 0, magnitudes[indices[i]]);
          tdt.set(i, 1, beta[indices[i]] < 0 ? "NEG" : "POS");
        }
        standardized_coefficient_magnitudes = new TwoDimTableBase();
        standardized_coefficient_magnitudes.fillFromImpl(tdt);
      } else {
        // coefficients_table = new TwoDimTable("Coefficients",impl._names,impl.isNormalized()? new String[]{"Coefficients, Normalized Coefficients"}: new String[]{"Coefficients"});
        String [] colTypes = new String[]{"double"};
        String [] colFormats = new String[]{"%5f"};
        TwoDimTable tdt;
        if(impl._multinomial) {
          int n = impl.nclasses();
          String [] cols = new String[n];
          for(int i = 0; i < n; ++i)
            cols[i] = "Coefficients." + i;
          colTypes = new String[cols.length];
          Arrays.fill(colTypes,"double");
          colFormats = new String[cols.length];
          Arrays.fill(colFormats,"%5f");
          tdt = new TwoDimTable("Coefficients","glm multinomial coefficients", ns, cols, colTypes, colFormats, "names");
        } else {
          tdt = new TwoDimTable("Coefficients", "glm coefficients", ns, new String[]{"Coefficients"}, colTypes, colFormats, "names");
          tdt.set(0, 0, beta[beta.length - 1]);
          for (int i = 0; i < beta.length - 1; ++i) {
            tdt.set(i + 1, 0, beta[i]);
          }
        }
        coefficients_table.fillFromImpl(tdt);
        magnitudes = beta.clone();
        for(int i = 0; i < magnitudes.length-1; ++i)
          if(magnitudes[i] < 0) magnitudes[i] *= -1;
      }
      return this;
    }
  } // GLMModelOutputV2

  public GLMV3.GLMParametersV3 createParametersSchema() { return new GLMV3.GLMParametersV3(); }
  public GLMModelOutputV3 createOutputSchema() { return new GLMModelOutputV3(); }

  @Override public GLMModel createImpl() {
    GLMModel.GLMParameters parms = parameters.createImpl();
    return new GLMModel( model_id.key(), parms, null, new double[]{0.0}, 0.0, 0.0, 0, false, false);
  }
}
