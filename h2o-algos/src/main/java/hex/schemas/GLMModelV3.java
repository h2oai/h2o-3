package hex.schemas;

import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import water.MemoryManager;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static water.util.ArrayUtils.sort;
//import water.util.DocGen.HTML;

public class GLMModelV3 extends ModelSchemaV3<GLMModel, GLMModelV3, GLMModel.GLMParameters, GLMV3.GLMParametersV3, GLMOutput, GLMModelV3.GLMModelOutputV3> {

  public static final class GLMModelOutputV3 extends ModelOutputSchemaV3<GLMOutput, GLMModelOutputV3> {

    @API(help="Table of Coefficients")
    TwoDimTableV3 coefficients_table;

    @API(help="Table of Random Coefficients for HGLM")
    TwoDimTableV3 random_coefficients_table;

    @API(help="Table of Coefficients with coefficients denoted with class names for GLM multinonimals only.")
    TwoDimTableV3 coefficients_table_multinomials_with_class_names;  // same as coefficients_table but with real class names.

    @API(help="Standardized Coefficient Magnitudes")
    TwoDimTableV3 standardized_coefficient_magnitudes;

    @API(help = "Variable Importances", direction = API.Direction.OUTPUT, level = API.Level.secondary)
    TwoDimTableV3 variable_importances;

    @API(help="Lambda minimizing the objective value, only applicable with lambda search or when arrays of alpha and " +
            "lambdas are provided")
    double lambda_best;

    @API(help="Alpha minimizing the objective value, only applicable when arrays of alphas are given ")
    double alpha_best;

    @API(help="submodel index minimizing the objective value, only applicable for arrays of alphas/lambda ")
    int best_submodel_index; // denote the submodel index that yields the best result

    @API(help="Lambda best + 1 standard error. Only applicable with lambda search and cross-validation")
    double lambda_1se;

    @API(help="Minimum lambda value calculated that may be used for lambda search.  Early-stop may happen and " +
            "the minimum lambda value will not be used in this case.")
    double lambda_min;

    @API(help="Starting lambda value used when lambda search is enabled.")
    double lambda_max;

    @API(help = "Dispersion parameter, only applicable to Tweedie family (input/output) and fractional Binomial (output only)")
    double dispersion;
    
    @API(help = "Predictor names where variable inflation factors are calculated.")
    String[] vif_predictor_names;

    @API(help = "GLM model coefficients names.")
    String[] coefficient_names;
    
    @API(help = "predictor variable inflation factors.")
    double[] variable_inflation_factors;
    
    @API(help = "Beta (if exists) and linear constraints states")
    String[] linear_constraint_states;
    
    @API(help = "Table of beta (if exists) and linear constraints values and status")
    TwoDimTableV3 linear_constraints_table;

    @API(help="Contains the original dataset and the dfbetas calculated for each predictor.")
    KeyV3.FrameKeyV3 regression_influence_diagnostics;
    
    @API(help="True if all constraints conditions are satisfied.  Otherwise, false.")
    boolean all_constraints_satisfied;

    private GLMModelOutputV3 fillMultinomial(GLMOutput impl) {
      if(impl.get_global_beta_multinomial() == null)
        return this; // no coefificients yet
      String [] names = impl.coefficientNames().clone();
      int len = names.length-1;
      String [] names2 = new String[len]; // this one decides the length of standardized table length
      int[] indices = new int[len];
      for (int i = 0; i < indices.length; ++i)
        indices[i] = i;
      // put intercept as the first
      String [] ns = ArrayUtils.append(new String[]{"Intercept"},Arrays.copyOf(names,names.length-1));

      coefficients_table = new TwoDimTableV3();
      if (impl.nclasses() > 2) // only change coefficient names for multinomials
        coefficients_table_multinomials_with_class_names = new TwoDimTableV3();
      
        int n = impl.nclasses();
        String[] cols = impl.hasVIF() ? new String[2*n+1] : new String[n*2]; // coefficients per class and standardized coefficients
        String[] cols2=null;
        if (n>2) {
          cols2 = impl.hasVIF() ? new String[n*2+1] : new String[n*2];
          String[] classNames = impl._domains[impl.responseIdx()];
          for (int i = 0; i < n; ++i) {
            cols2[i] = "coefs_class_" + classNames[i];
            cols2[n + i] = "std_coefs_class_" + classNames[i];
          }
          if (impl.hasVIF())
            cols2[2*n] = "variable_inflation_factor";
        }
        for (int i = 0; i < n; ++i) {
          cols[i] = "coefs_class_" +i;
          cols[n + i] = "std_coefs_class_" +i;
        }
        if (impl.hasVIF())
          cols[2*n] = "variable_inflation_factor";

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
          if (impl.hasVIF()) {
            List<String> vifPredictors = Stream.of(impl.getVIFPredictorNames()).collect(Collectors.toList());
            double[] varInFactors = impl.variableInflationFactors();
            for (int row=0; row < ns.length; row++) {
              if (vifPredictors.contains(ns[row])) {
                int index = vifPredictors.indexOf(ns[row]);
                tdt.set(row, 2*n, varInFactors[index]);
              } else {
                tdt.set(row, 2*n, Double.NaN);
              }
            }
          }
          coefficients_table.fillFromImpl(tdt);
          if (n>2) {  // restore column names from pythonized ones
            coefficients_table_multinomials_with_class_names.fillFromImpl(tdt);
            revertCoeffNames(cols2, n, coefficients_table_multinomials_with_class_names);
          }
          final double [] magnitudes = new double[betaNorm[0].length];
          calculateVarimpMultinomial(magnitudes, indices, betaNorm);

          for(int i = 0; i < len; ++i)
            names2[i] = names[indices[i]];
          tdt = new TwoDimTable("Standardized Coefficient Magnitudes", 
                  "standardized coefficient magnitudes", names2, new String[]{"Coefficients", "Sign"},
                  new String[]{"double", "string"}, new String[]{"%5f", "%s"}, "names");
          for (int i = 0; i < magnitudes.length - 1; ++i) {
            tdt.set(i, 0, magnitudes[indices[i]]);
            tdt.set(i, 1, "POS");
          }
          standardized_coefficient_magnitudes = new TwoDimTableV3();
          standardized_coefficient_magnitudes.fillFromImpl(tdt);
        }

      return this;
    }

    public static void calculateVarimpMultinomial(double[] magnitudes, int[] indices, double[][] betaNorm) {
      for (int i = 0; i < betaNorm.length; ++i) {
        for (int j = 0; j < betaNorm[i].length; ++j) {
          double d = betaNorm[i][j];
          magnitudes[j] += d < 0 ? -d : d;
        }
      }
      sort(indices, magnitudes, -1, -1);
    }

    public void revertCoeffNames(String[] colNames, int nclass, TwoDimTableV3 coeffs_table) {
      String newName = coeffs_table.name+" with class names";
      coeffs_table.name = newName;
      boolean bothCoeffStd = colNames.length==(2*nclass);
      for (int tableIndex = 1; tableIndex <= nclass;  tableIndex++) {
        coeffs_table.columns[tableIndex].name = colNames[tableIndex-1];
        if (bothCoeffStd)
          coeffs_table.columns[tableIndex+nclass].name = colNames[tableIndex-1+nclass];
      }
    }
    
    public TwoDimTable buildRandomCoefficients2DTable(double[] ubeta, String[] randomColNames) {
      String [] colTypes = new String[]{"double"};
      String [] colFormats = new String[]{"%5f"};
      String [] colnames = new String[]{"Random Coefficients"};
      TwoDimTable tdt = new TwoDimTable("HGLM Random Coefficients",
              "HGLM random coefficients", randomColNames, colnames, colTypes, colFormats,
              "names");
      // fill in coefficients
      for (int i = 0; i < ubeta.length; ++i) {
        tdt.set(i, 0, ubeta[i]);
      }
      return tdt;
    }

    @Override
    public GLMModelOutputV3 fillFromImpl(GLMModel.GLMOutput impl) {
      super.fillFromImpl(impl);
      lambda_1se = impl.lambda_1se();
      lambda_best = impl.lambda_best();
      alpha_best = impl.alpha_best();
      best_submodel_index = impl.bestSubmodelIndex();
      dispersion = impl.dispersion();
      coefficient_names = impl.coefficientNames().clone();
      if (impl._linear_constraint_states != null) // pass constraint conditions
        linear_constraint_states = impl._linear_constraint_states.clone();
      variable_inflation_factors = impl.getVariableInflationFactors();
      vif_predictor_names = impl.hasVIF() ? impl.getVIFPredictorNames() : null;
      List<String> validVIFNames = impl.hasVIF() ? Stream.of(vif_predictor_names).collect(Collectors.toList()) : null;
      if(impl._multinomial || impl._ordinal)
        return fillMultinomial(impl);
      String [] names = impl.coefficientNames().clone();
      // put intercept as the first
      String [] ns = ArrayUtils.append(new String[]{"Intercept"},Arrays.copyOf(names,names.length-1));
      coefficients_table = new TwoDimTableV3();
      if ((impl.ubeta() != null) && (impl.randomcoefficientNames()!= null)) {
        random_coefficients_table = new TwoDimTableV3();
        random_coefficients_table.fillFromImpl(buildRandomCoefficients2DTable(impl.ubeta(), impl.randomcoefficientNames()));
      }
      double [] beta = impl.beta();
      final double [] magnitudes = beta==null?null:new double[beta.length];
      int len = beta==null?0:magnitudes.length - 1;
      int[] indices = beta==null?null:new int[len];
      if (beta != null) {
        for (int i = 0; i < indices.length; ++i)
          indices[i] = i;
      }

      if(beta == null) beta = MemoryManager.malloc8d(names.length);
      String [] colTypes = new String[]{"double"};
      String [] colFormats = new String[]{"%5f"};
      String [] colnames = new String[]{"Coefficients"};

      if(impl.hasPValues()){
        if (impl.hasVIF()) {
          colTypes = new String[]{"double", "double", "double", "double","double"};
          colFormats = new String[]{"%5f", "%5f", "%5f", "%5f", "%5f"};
          colnames = new String[]{"Coefficients", "Std. Error", "z value", "p value", "variable_inflation_factor"};
        } else {
          colTypes = new String[]{"double", "double", "double", "double"};
          colFormats = new String[]{"%5f", "%5f", "%5f", "%5f"};
          colnames = new String[]{"Coefficients", "Std. Error", "z value", "p value"};
        }
      } else if (impl.hasVIF()) {
        colTypes = new String[]{"double", "double"};
        colFormats = new String[]{"%5f", "%5f"};
        colnames = new String[]{"Coefficients", "variable_inflation_factor"};
      }

      int stdOff = colnames.length;
      colTypes = ArrayUtils.append(colTypes,"double");
      colFormats = ArrayUtils.append(colFormats,"%5f");
      colnames = ArrayUtils.append(colnames,"Standardized Coefficients"); // as last column
      TwoDimTable tdt = new TwoDimTable("Coefficients","glm coefficients", ns, colnames, colTypes, colFormats, "names");
      tdt.set(0, 0, beta[beta.length - 1]);
      for (int i = 0; i < beta.length - 1; ++i) {
        tdt.set(i + 1, 0, beta[i]);
      }
      double[] norm_beta = null;
      if(impl.beta() != null) {
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
        if (impl.hasVIF()) {
          for (int i=0; i < stdErr.length; i++)
            if (validVIFNames.contains(ns[i])) {
              int index = validVIFNames.indexOf(ns[i]);
              tdt.set(i, 4, variable_inflation_factors[index]);
            } else {
              tdt.set(i, 4, Double.NaN);
            }
        }
      } else if (impl.hasVIF()) { // has VIF but without p-values and stuff
        for (int i=0; i<ns.length; i++) {
          if (validVIFNames.contains(ns[i])) {
            int index = validVIFNames.indexOf(ns[i]);
            tdt.set(i, 1, variable_inflation_factors[index]);
          } else {
            tdt.set(i, 1, Double.NaN);
          }
        }
      }
      coefficients_table.fillFromImpl(tdt);
      if(impl.beta() != null) { // get varImp
        calculateVarimpBase(magnitudes, indices, impl.getNormBeta());

        String[] names2 = new String[len];
        for (int i = 0; i < len; ++i)
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

  public static void calculateVarimpBase(double[] magnitudes, int[] indices, double[] betaNorm) {
    for (int i = 0; i < magnitudes.length; ++i) {
      magnitudes[i] = (float) betaNorm[i];
      if (magnitudes[i] < 0) magnitudes[i] *= -1;
    }
    sort(indices, magnitudes, -1, -1);
  }

  public GLMV3.GLMParametersV3 createParametersSchema() { return new GLMV3.GLMParametersV3(); }
  public GLMModelOutputV3 createOutputSchema() { return new GLMModelOutputV3(); }

  @Override public GLMModel createImpl() {
    GLMModel.GLMParameters parms = parameters.createImpl();
    return new GLMModel( model_id.key(), parms, null, new double[]{0.0}, 0.0, 0.0, 0);
  }
}
