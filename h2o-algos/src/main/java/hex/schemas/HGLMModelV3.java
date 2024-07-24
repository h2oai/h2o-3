package hex.schemas;

import hex.hglm.HGLMModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;
import water.util.TwoDimTable;

import java.util.Arrays;

import static water.util.ArrayUtils.flattenArray;

public class HGLMModelV3 extends ModelSchemaV3<HGLMModel, 
        HGLMModelV3, 
        HGLMModel.HGLMParameters, 
        HGLMV3.HGLMParametersV3, 
        HGLMModel.HGLMModelOutput, 
        HGLMModelV3.HGLMModelOutputV3> {
  public static final class HGLMModelOutputV3 extends ModelOutputSchemaV3<HGLMModel.HGLMModelOutput, HGLMModelOutputV3> {
    // the doc == document described our HGLM implementation attached to issue: https://github.com/h2oai/h2o-3/issues/8487
    @API(help="Table of Fixed Coefficients")
    TwoDimTableV3 coefficients_table;
    
    @API(help="Table of Random Coefficients")
    TwoDimTableV3 random_coefficients_table;
    
    @API(help="Table of Scoring History for Validation Dataset")
    TwoDimTableV3 scoring_history_valid;
    
    @API(help="Fixed Effects Coefficient Names")
    public String[] coefficient_names; // include intercept only if _parms._intercept is true
    
    @API(help="Random Effects Coefficient Names")
    public String[] random_coefficient_names;  // include intercept only if _parms._random_intercept = true
    
    @API(help="Random Effects Coefficient Names for when standardize=true")
    public String[] random_coefficient_names_normalized;
    
    @API(help="Level 2 Indice Names")
    public String[] group_column_names;
    
    @API(help="Fixed Effects Coefficients")
    public double[] beta;   // fixed coefficients, not normalized
    
    @API(help="Random Effects Coefficients")
    public double[][] ubeta;  // random coefficients, not normalized
    
    @API(help="Fixed Effects Coefficients for when standardize=true")
    double[] beta_normalized;
    
    @API(help="Random Effects Coefficients for when standardize=true")
    double[][] ubeta_normalized;
    
    @API(help="Covariance Matrix for Random Effects (= Tj in section II.I of the doc")
    public double[][] tmat;

    @API(help="ratio of each random effect variance and (sum of all random effect variances plus the residual noise" +
            " variance).")
    double[] icc;
    
    @API(help="residual noise variance")
    double residual_variance;
    
    @API(help="mean residual error with fixed effect coefficients only")
    double mean_residual_fixed;

    @API(help="mean residual error with fixed effect coefficients only")
    double mean_residual_fixed_valid;
    
    @Override
    public HGLMModelOutputV3 fillFromImpl(HGLMModel.HGLMModelOutput impl) {
      super.fillFromImpl(impl);
      coefficient_names = impl._fixed_coefficient_names;
      random_coefficient_names = impl._random_coefficient_names;
      random_coefficient_names_normalized = impl._random_coefficient_names_normalized;
      group_column_names = impl._group_column_names;
      beta = impl._beta;
      beta_normalized = impl._beta_normalized;
      ubeta = impl._ubeta;
      ubeta_normalized = impl._ubeta_normalized;
      coefficients_table = new TwoDimTableV3();
      coefficients_table.fillFromImpl(generateCoeffTable("fixed effect oefficients", 
              "HGLM fixed effect coefficients", beta, beta_normalized, coefficient_names));
      random_coefficients_table = new TwoDimTableV3();
      random_coefficients_table.fillFromImpl(generate2DCoeffTable("random effect coefficients", 
              "HGLM random effect coefficients", ubeta, ubeta_normalized, random_coefficient_names, 
              random_coefficient_names_normalized, impl._group_column_names));
      icc = impl._icc;
      residual_variance = impl._tau_e_var;
      mean_residual_fixed = impl._yMinusfixPredSquare/impl._nobs;
      if (impl._nobs_valid > 0)
        mean_residual_fixed_valid = impl._yMinusfixPredSquare_valid/impl._nobs_valid;
      return this;
    }
  }
  
  public static TwoDimTable generateCoeffTable(String title1, String title2, double[] coeffs, double[] coeffs_normalized, 
                                 String[] coeffNames) {
    String[] colnames = new String[] {"coefficients", "standardized_coefficients"};
    String[] colFormats = new String[] {"%.5f", "%.5f"};
    String[] colTypes = new String[] {"double", "double"};
    TwoDimTable tdt = new TwoDimTable(title1, title2, coeffNames, colnames, colTypes, colFormats, "names");
    int tableLen = coeffs.length;
    for (int index=0; index<tableLen; index++) {
      tdt.set(index, 0, coeffs[index]);
      tdt.set(index, 1, coeffs_normalized[index]);
    }
    return tdt;
  }

  public static TwoDimTable generate2DCoeffTable(String title1, String title2, double[][] coeffs, double[][] coeffs_normalized,
                                 String[] coeffNames, String[] coeffNamesNormalized, String[] level2Domain) {
    int randomCoeffNamesLen = coeffNames.length;
    int randomCoefNameLenNorm = coeffNamesNormalized.length;
    int numLevel2Index = level2Domain.length;
    String[] coeffNamesused;
    double[][] coeffsUsed;
    double[][] coeffsNormalizedUsed;
    if (randomCoefNameLenNorm > randomCoeffNamesLen) {  // model building, added intercept to coeffs_normalized, extend coeffs
      coeffNamesused = coeffNamesNormalized;
      coeffsUsed = addInterceptValue(coeffs); 
      coeffsNormalizedUsed = coeffs_normalized;
    } else if (randomCoefNameLenNorm < randomCoeffNamesLen) { // model building with standardization, added intercept to coeffs, extend coeffsNormalized
      coeffNamesused = coeffNames;
      coeffsUsed = coeffs;
      coeffsNormalizedUsed = addInterceptValue(coeffs_normalized);
    } else {
      coeffNamesused = coeffNames;
      coeffsUsed = coeffs;
      coeffsNormalizedUsed = coeffs_normalized;
    }
    double[] fCoeffValues = flattenArray(coeffsUsed);
    double[] fCoeffValuesNormalized = flattenArray(coeffsNormalizedUsed);
    String[] fCoeffNames = extendCoeffNames(coeffNamesused, numLevel2Index);
    String[] fLevel2Vals = extendLevel2Ind(level2Domain, coeffsUsed[0].length);
    
    String[] colnames = new String[] {"coefficient names", "coefficients", "standardized_coefficients"};
    String[] colFormats = new String[] {"%s", "%.5f", "%.5f"};
    String[] colTypes = new String[] {"string", "double", "double"};
    TwoDimTable tdt = new TwoDimTable(title1, title2, fLevel2Vals, colnames, colTypes, colFormats, "names");
    int tableLen = fCoeffNames.length;
    for (int index=0; index<tableLen; index++) {
      tdt.set(index, 0, fCoeffNames[index]);
      tdt.set(index, 1, fCoeffValues[index]);
      tdt.set(index, 2, fCoeffValuesNormalized[index]);
    }
    return tdt;
  }
  
  public static String[] extendLevel2Ind(String[] level2Domain, int numCoeff) {
    int levelIndNum = level2Domain.length;
    String[][] extendedDomain = new String[levelIndNum][numCoeff];
    int extendLen = extendedDomain.length;
    for (int index=0; index<extendLen; index++) {
      Arrays.fill(extendedDomain[index], level2Domain[index]);
    }
    return flattenArray(extendedDomain);
  }
  
  public static String[] extendCoeffNames(String[] coeffNames, int numLevel2Ind) {
    int numCoeff = coeffNames.length;
    String[] extendedCoeffNames = new String[numCoeff*numLevel2Ind];
    int indexStart;
    for (int index=0; index<numLevel2Ind; index++) {
      indexStart = index*numCoeff;
      System.arraycopy(coeffNames, 0, extendedCoeffNames, indexStart, numCoeff);
    }
      return extendedCoeffNames;
  }
  
  public static double[][] addInterceptValue(double[][] coeffs) {
    int coefLen = coeffs[0].length;
    int coefLenNew = coeffs[0].length+1;
    int numLevel2Index = coeffs.length;
    double[][] coeffsExt = new double[coeffs.length][coefLenNew];
    for (int index=0; index<numLevel2Index; index++) {
      System.arraycopy(coeffs[index], 0, coeffsExt[index], 0, coefLen);
      coeffsExt[index][coefLen] = 0.0;
    }
    return coeffsExt;
  }
  
  public HGLMV3.HGLMParametersV3 createParametersSchema() { return new HGLMV3.HGLMParametersV3(); }
  public HGLMModelOutputV3 createOutputSchema() { return new HGLMModelOutputV3(); }
  
  @Override
  public HGLMModel createImpl() {
    HGLMModel.HGLMParameters parms = parameters.createImpl();
    return new HGLMModel(model_id.key(), parms, null);
  }
}
