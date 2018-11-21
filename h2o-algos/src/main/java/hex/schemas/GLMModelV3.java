package hex.schemas;

import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import water.MemoryManager;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

import water.api.schemas3.TwoDimTableV3;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Comparator;
//import water.util.DocGen.HTML;

public class GLMModelV3 extends ModelSchemaV3<GLMModel, GLMModelV3, GLMModel.GLMParameters, GLMV3.GLMParametersV3, GLMOutput, GLMModelV3.GLMModelOutputV3> {

  public static final class GLMModelOutputV3 extends ModelOutputSchemaV3<GLMOutput, GLMModelOutputV3> {

    @API(help="Table of Coefficients")
    TwoDimTableV3 coefficients_table;

    @API(help="Table of Coefficients with coefficients denoted with class names for GLM multinonimals only.")
    TwoDimTableV3 coefficients_table_multinomials_with_class_names;  // same as coefficients_table but with real class names.

    @API(help="Standardized Coefficient Magnitudes")
    TwoDimTableV3 standardized_coefficient_magnitudes;

    @API(help="Lambda minimizing the objective value, only applicable with lambd search")
    double lambda_best;

    @API(help="Lambda best + 1 standard error. Only applicable with lambda search and cross-validation")
    double lambda_1se;

    private GLMModelOutputV3 fillMultinomial(GLMOutput impl) {
      if(impl.get_global_beta_multinomial() == null)
        return this; // no coefificients yet
      String [] names = impl.coefficientNames().clone();
      // put intercept as the first
      String [] ns = ArrayUtils.append(new String[]{"Intercept"},Arrays.copyOf(names,names.length-1));

      coefficients_table = new TwoDimTableV3();
      if (impl.nclasses() > 2) // only change coefficient names for multinomials
        coefficients_table_multinomials_with_class_names = new TwoDimTableV3();

      if(impl.isStandardized()){
        int n = impl.nclasses();
        String[] cols = new String[n*2];
        String[] cols2=null;
        if (n>2) {
          cols2 = new String[n*2];
          String[] classNames = impl._domains[impl.responseIdx()];
          for (int i = 0; i < n; ++i) {
            cols2[i] = "coefs_class_" + classNames[i];
            cols2[n + i] = "std_coefs_class_" + classNames[i];
          }
        }
        for (int i = 0; i < n; ++i) {
          cols[i] = "coefs_class_" +i;
          cols[n + i] = "std_coefs_class_" +i;
        }

        String [] colTypes = new String[cols.length];
        Arrays.fill(colTypes, "double");
        String [] colFormats = new String[cols.length];
        Arrays.fill(colFormats,"%5f");

        double [][] betaNorm = impl.getNormBetaMultinomial();
        if(betaNorm != null) {
          TwoDimTable tdt = new TwoDimTable("Coefficients", "glm multinomial coefficients", ns, cols, colTypes, colFormats, "names");
          for (int c = 0; c < n; ++c) {
            double[] beta = impl.get_global_beta_multinomial()[c];
            tdt.set(0, c, beta[beta.length - 1]);
            tdt.set(0, n + c, betaNorm[c][beta.length - 1]);
            for (int i = 0; i < beta.length - 1; ++i) {
              tdt.set(i + 1, c, beta[i]);
              tdt.set(i + 1, n + c, betaNorm[c][i]);
            }
          }
          coefficients_table.fillFromImpl(tdt);

          if (n>2) {  // restore column names from pythonized ones
            coefficients_table_multinomials_with_class_names.fillFromImpl(tdt);
            revertCoeffNames(cols2, n, coefficients_table_multinomials_with_class_names);
          }
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
          standardized_coefficient_magnitudes = new TwoDimTableV3();
          standardized_coefficient_magnitudes.fillFromImpl(tdt);
        }
      } else {
        int n = impl.nclasses();
        String [] cols = new String[n];
        String [] cols2 = null;
        if (n>2) {
          String[] classNames = impl._domains[impl.responseIdx()];
          cols2 = new String[n];
          for (int i = 0; i < n; ++i) {
            cols2[i] = "coefs_class_" + classNames[i];
          }
        }
          for (int i = 0; i < n; ++i) {
            cols[i] = "coefs_class_" +i;
          }

        String [] colTypes = new String[cols.length];
        Arrays.fill(colTypes, "double");
        String [] colFormats = new String[cols.length];
        Arrays.fill(colFormats,"%5f");

        TwoDimTable tdt = new TwoDimTable("Coefficients","glm multinomial coefficients", ns, cols, colTypes, colFormats, "names");

        for(int c = 0; c < n; ++c) {
          double [] beta = impl.get_global_beta_multinomial()[c];
          tdt.set(0,c,beta[beta.length-1]);
          for(int i = 0; i < beta.length-1; ++i)
            tdt.set(i + 1, c, beta[i]);
        }
        coefficients_table.fillFromImpl(tdt);
        if (n>2) {  // restore column names from pythonized ones
          coefficients_table_multinomials_with_class_names.fillFromImpl(tdt);
          revertCoeffNames(cols2, n, coefficients_table_multinomials_with_class_names);
        }
      }

      return this;
    }

    public void revertCoeffNames(String[] colNames, int nclass, TwoDimTableV3 coeffs_table) {
      String newName = coeffs_table.name+" with class names";
      coeffs_table.name = newName;
      boolean bothCoeffStd = colNames.length==(2*nclass);
      for (int tableIndex = 1; tableIndex <= nclass;  tableIndex++) {
        coeffs_table.columns[tableIndex].name = new String(colNames[tableIndex-1]);
        if (bothCoeffStd)
          coeffs_table.columns[tableIndex+nclass].name = new String(colNames[tableIndex-1+nclass]);
      }
    }

    @Override
    public GLMModelOutputV3 fillFromImpl(GLMModel.GLMOutput impl) {
      super.fillFromImpl(impl);
      lambda_1se = impl.lambda_1se();
      lambda_best = impl.lambda_best();
      if(impl._multinomial || impl._ordinal)
        return fillMultinomial(impl);
      String [] names = impl.coefficientNames().clone();
      // put intercept as the first
      String [] ns = ArrayUtils.append(new String[]{"Intercept"},Arrays.copyOf(names,names.length-1));
      coefficients_table = new TwoDimTableV3();
      final double [] magnitudes;
      double [] beta = impl.beta();
      if(beta == null) beta = MemoryManager.malloc8d(names.length);
      String [] colTypes = new String[]{"double"};
      String [] colFormats = new String[]{"%5f"};
      String [] colnames = new String[]{"Coefficients"};

      if(impl.hasPValues()){
        colTypes = new String[]{"double","double","double","double"};
        colFormats = new String[]{"%5f","%5f","%5f","%5f"};
        colnames = new String[]{"Coefficients","Std. Error","z value","p value"};
      }
      int stdOff = colnames.length;
      if(impl.isStandardized()) {
        colTypes = ArrayUtils.append(colTypes,"double");
        colFormats = ArrayUtils.append(colFormats,"%5f");
        colnames = ArrayUtils.append(colnames,"Standardized Coefficients");
      }
      TwoDimTable tdt = new TwoDimTable("Coefficients","glm coefficients", ns, colnames, colTypes, colFormats, "names");
      // fill in coefficients

      tdt.set(0, 0, beta[beta.length - 1]);
      for (int i = 0; i < beta.length - 1; ++i) {
        tdt.set(i + 1, 0, beta[i]);
      }
      double[] norm_beta = null;
      if(impl.isStandardized() &&impl.beta() != null) {
        norm_beta = impl.getNormBeta();
        tdt.set(0, stdOff, norm_beta[norm_beta.length - 1]);
        for (int i = 0; i < norm_beta.length - 1; ++i)
          tdt.set(i + 1, stdOff, norm_beta[i]);
      }
      if(impl.hasPValues()) { // fill in p values
        double [] stdErr = impl.stdErr();
        double [] zVals = impl.zValues();
        double [] pVals = impl.pValues();
        tdt.set(0, 1, stdErr[stdErr.length - 1]);
        tdt.set(0, 2, zVals[zVals.length - 1]);
        tdt.set(0, 3, pVals[pVals.length - 1]);
        for(int i = 0; i < stdErr.length - 1; ++i) {
          tdt.set(i + 1, 1, stdErr[i]);
          tdt.set(i + 1, 2, zVals[i]);
          tdt.set(i + 1, 3, pVals[i]);
        }
      }
      coefficients_table.fillFromImpl(tdt);
      if(impl.isStandardized() && impl.beta() != null) {
        magnitudes = norm_beta.clone();
        for (int i = 0; i < magnitudes.length; ++i)
          if (magnitudes[i] < 0) magnitudes[i] *= -1;
        Integer[] indices = new Integer[magnitudes.length - 1];
        for (int i = 0; i < indices.length; ++i)
          indices[i] = i;
        Arrays.sort(indices, new Comparator<Integer>() {
          @Override
          public int compare(Integer o1, Integer o2) {
            if (magnitudes[o1] < magnitudes[o2]) return +1;
            if (magnitudes[o1] > magnitudes[o2]) return -1;
            return 0;
          }
        });
        String[] names2 = new String[names.length];
        for (int i = 0; i < names2.length - 1; ++i)
          names2[i] = names[indices[i]];
        tdt = new TwoDimTable("Standardized Coefficient Magnitudes", "standardized coefficient magnitudes", names2, new String[]{"Coefficients", "Sign"}, new String[]{"double", "string"}, new String[]{"%5f", "%s"}, "names");
        for (int i = 0; i < beta.length - 1; ++i) {
          tdt.set(i, 0, magnitudes[indices[i]]);
          tdt.set(i, 1, beta[indices[i]] < 0 ? "NEG" : "POS");
        }
        standardized_coefficient_magnitudes = new TwoDimTableV3();
        standardized_coefficient_magnitudes.fillFromImpl(tdt);
      }
      return this;
    }
  } // GLMModelOutputV2

  public GLMV3.GLMParametersV3 createParametersSchema() { return new GLMV3.GLMParametersV3(); }
  public GLMModelOutputV3 createOutputSchema() { return new GLMModelOutputV3(); }

  @Override public GLMModel createImpl() {
    GLMModel.GLMParameters parms = parameters.createImpl();
    return new GLMModel( model_id.key(), parms, null, new double[]{0.0}, 0.0, 0.0, 0);
  }
}
