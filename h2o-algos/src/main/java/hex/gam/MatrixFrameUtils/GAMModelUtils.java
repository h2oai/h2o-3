package hex.gam.MatrixFrameUtils;

import hex.VarImp;
import hex.gam.GAMModel;
import hex.glm.GLMModel;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.TwoDimTable;

import static hex.ModelMetrics.calcVarImp;
import static hex.gam.GAMModel.GAMParameters;
import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.Family.multinomial;
import static hex.glm.GLMModel.GLMParameters.Family.ordinal;
import static water.util.ArrayUtils.find;

public class GAMModelUtils {
  public static void copyGLMCoeffs(GLMModel glm, GAMModel model, GAMParameters parms, int nclass) {
    boolean multiClass = parms._family == multinomial || parms._family == ordinal;
    int totCoefNumsNoCenter = (multiClass?glm.coefficients().size()/nclass:glm.coefficients().size())
            +gamNoCenterCoeffLength(parms);
    model._output._coefficient_names_no_centering = new String[totCoefNumsNoCenter]; // copy coefficient names from GLM to GAM
    int gamNumStart = copyGLMCoeffNames2GAMCoeffNames(model, glm);
    copyGLMCoeffs2GAMCoeffs(model, glm, parms._family, gamNumStart, nclass); // obtain beta without centering
    // copy over GLM coefficients
    int glmCoeffLen = glm._output._coefficient_names.length;
    model._output._coefficient_names = new String[glmCoeffLen];
    System.arraycopy(glm._output._coefficient_names, 0, model._output._coefficient_names, 0,
            glmCoeffLen);
    if (multiClass) {
      double[][] model_beta_multinomial = glm._output.get_global_beta_multinomial();
      double[][] standardized_model_beta_multinomial = glm._output.getNormBetaMultinomial();
      model._output._model_beta_multinomial = new double[nclass][glmCoeffLen];
      model._output._standardized_model_beta_multinomial = new double[nclass][glmCoeffLen];
      for (int classInd = 0; classInd < nclass; classInd++) {
        System.arraycopy(model_beta_multinomial[classInd], 0, model._output._model_beta_multinomial[classInd],
                0, glmCoeffLen);
        System.arraycopy(standardized_model_beta_multinomial[classInd], 0,
                model._output._standardized_model_beta_multinomial[classInd], 0, glmCoeffLen);
      }
    } else {
      model._output._model_beta = new double[glmCoeffLen];
      model._output._standardized_model_beta = new double[glmCoeffLen];
      System.arraycopy(glm._output.beta(), 0, model._output._model_beta, 0, glmCoeffLen);
      System.arraycopy(glm._output.getNormBeta(), 0, model._output._standardized_model_beta, 0,
              glmCoeffLen);
    }
  }
  
  

  /***
   * Find the number of gamified column coefficients.  This is more difficult with thin plate regression smoothers.
   * For thin plate regression smoothers, each smoother will have columns _numKnots + _M
   * @param parms
   */
  public static int gamNoCenterCoeffLength(GAMParameters parms) {
    int tpCount = 0;
    int numGam = parms._gam_columns.length;
    int gamifiedColCount = 0;
    for (int index = 0; index < numGam; index++) {
      if (parms._bs_sorted[index]==0) { // cubic spline
        gamifiedColCount++;
      } else {
        gamifiedColCount += (1+parms._M[tpCount++]);
      }
    }
    return gamifiedColCount;
  }
  
  public static void copyGLMtoGAMModel(GAMModel model, GLMModel glmModel, GAMParameters parms, boolean validNotNull) {
    model._output._glm_best_lamda_value = glmModel._output.bestSubmodel().lambda_value; // exposed best lambda used
    model._output._glm_training_metrics = glmModel._output._training_metrics;
    if (validNotNull)
      model._output._glm_validation_metrics = glmModel._output._validation_metrics;
    model._output._glm_model_summary = copyTwoDimTable(glmModel._output._model_summary, "glm model summary");
    model._output._glm_scoring_history = copyTwoDimTable(glmModel._output._scoring_history, "glm scoring history");
    if (parms._family == multinomial || parms._family == ordinal) {
      model._output._coefficients_table = genCoefficientTableMultinomial(new String[]{"Coefficients",
                      "Standardized Coefficients"}, model._output._model_beta_multinomial,
              model._output._standardized_model_beta_multinomial, model._output._coefficient_names,"GAM Coefficients");
      model._output._coefficients_table_no_centering = genCoefficientTableMultinomial(new String[]{"coefficients " +
                      "no centering", "standardized coefficients no centering"},
              model._output._model_beta_multinomial_no_centering, model._output._standardized_model_beta_multinomial_no_centering,
              model._output._coefficient_names_no_centering,"GAM Coefficients No Centering");
      model._output._standardized_coefficient_magnitudes = model.genCoefficientMagTableMultinomial(new String[]{"coefficients", "signs"},
              model._output._standardized_model_beta_multinomial, model._output._coefficient_names, "standardized coefficients magnitude");
    } else{
      model._output._coefficients_table = genCoefficientTable(new String[]{"coefficients", "standardized coefficients"}, model._output._model_beta,
              model._output._standardized_model_beta, model._output._coefficient_names, "GAM Coefficients");
      model._output._coefficients_table_no_centering = genCoefficientTable(new String[]{"coefficients no centering",
                      "standardized coefficients no centering"}, model._output._model_beta_no_centering,
              model._output._standardized_model_beta_no_centering,
              model._output._coefficient_names_no_centering,
              "GAM Coefficients No Centering");
      model._output._standardized_coefficient_magnitudes = model.genCoefficientMagTable(new String[]{"coefficients", "signs"},
              model._output._standardized_model_beta, model._output._coefficient_names, "standardized coefficients magnitude");
    }

    if (parms._compute_p_values) {
      model._output._glm_zvalues = glmModel._output.zValues().clone();
      model._output._glm_pvalues = glmModel._output.pValues().clone();
      model._output._glm_stdErr = glmModel._output.stdErr().clone();
      model._output._glm_vcov = glmModel._output.vcov().clone();
    }
    model._output._glm_dispersion = glmModel._output.dispersion();
    model._nobs = glmModel._nobs;
    model._nullDOF = glmModel._nullDOF;
    model._rank = glmModel._output.bestSubmodel().rank();
    model._ymu = new double[glmModel._ymu.length];
    System.arraycopy(glmModel._ymu, 0, model._ymu, 0, glmModel._ymu.length);
    // pass GLM _solver value to GAM so that GAM effective _solver value can be set
    if (model.evalAutoParamsEnabled && model._parms._solver == GLMParameters.Solver.AUTO) {
      model._parms._solver = glmModel._parms._solver;
    }
    model._output._varimp = new VarImp(glmModel._output._varimp._varimp, glmModel._output._varimp._names);
    model._output._variable_importances = calcVarImp(model._output._varimp);
  }

  public static TwoDimTable copyTwoDimTable(TwoDimTable table, String tableHeader) {
    String[] rowHeaders = table.getRowHeaders();
    String[] colTypes = table.getColTypes();
    int tableSize = rowHeaders.length;
    int colSize = colTypes.length;
    TwoDimTable tableCopy = new TwoDimTable(tableHeader, "",
            rowHeaders, table.getColHeaders(), colTypes, table.getColFormats(),
            "names");
    for (int rowIndex = 0; rowIndex < tableSize; rowIndex++)  {
      for (int colIndex = 0; colIndex < colSize; colIndex++) {
        tableCopy.set(rowIndex, colIndex,table.get(rowIndex, colIndex));
      }
    }
    return tableCopy;
  }
  
  public static TwoDimTable genCoefficientTable(String[] colHeaders, double[] coefficients, double[] coefficientsStand,
                                                String[] coefficientNames, String tableHeader) {
    String[] colTypes = new String[]{ "double", "double"};
    String[] colFormat = new String[]{"%5f", "%5f"};
    int nCoeff = coefficients.length;
    String[] coeffNames = new String[nCoeff];
    System.arraycopy(coefficientNames, 0, coeffNames, 0, nCoeff);

    Log.info("genCoefficientMagTable", String.format("coemffNames length: %d.  coefficients " +
            "length: %d, coeffSigns length: %d", coeffNames.length, coefficients.length, coefficientsStand.length));

    TwoDimTable table = new TwoDimTable(tableHeader, "", coeffNames, colHeaders, colTypes, colFormat,
            "names");
    fillUpCoeffs(coefficients, coefficientsStand, table, 0);
    return table;
  }

  public static TwoDimTable genCoefficientTableMultinomial(String[] colHeaders, double[][] coefficients, double[][] coefficients_stand,
                                                           String[] coefficientNames, String tableHeader) {
    String[] colTypes = new String[]{"double", "double"};
    String[] colFormat = new String[]{"%5f", "%5f"};
    int nCoeff = coefficients[0].length;
    int nclass = coefficients.length;
    int totCoeff = nCoeff*nclass;
    String[] coeffNames = new String[totCoeff];

    int coeffCounter=0;
    for (int classInd=0; classInd < nclass; classInd++){
      for (int ind=0; ind < nCoeff; ind++) {
        coeffNames[coeffCounter++] = coefficientNames[ind]+"_class_"+classInd;
      }
    }
    TwoDimTable table = new TwoDimTable(tableHeader, "", coeffNames, colHeaders, colTypes, colFormat,
            "names");
    for (int classInd=0; classInd<nclass; classInd++)
      fillUpCoeffs(coefficients[classInd], coefficients_stand[classInd], table, classInd*nCoeff);
    return table;
  }
  
  public static void fillUpCoeffs(double[] coeffValues, double[] coeffValuesStand, TwoDimTable tdt, int rowStart) {
    int arrLength = coeffValues.length+rowStart;
    int arrCounter=0;
    for (int i=rowStart; i<arrLength; i++) {
      tdt.set(i, 0, coeffValues[arrCounter]);
      tdt.set(i, 1, coeffValuesStand[arrCounter]);
      arrCounter++;
    }
  }

  public static int copyGLMCoeffNames2GAMCoeffNames(GAMModel model, GLMModel glm) {
    int numGamCols = model._gamColNamesNoCentering.length;
    String[] glmColNames = glm._output.coefficientNames();
    int lastGLMCoeffIndex = glmColNames.length-1;
    int lastGAMCoeffIndex = lastGLMCoeffIndex+gamNoCenterCoeffLength(model._parms);
    int gamNumColStart = find(glmColNames, model._gamColNames[0][0]);
    int gamLengthCopied = gamNumColStart;
    System.arraycopy(glmColNames, 0, model._output._coefficient_names_no_centering, 0, gamLengthCopied); // copy coeff names before gam columns
    for (int gamColInd = 0; gamColInd < numGamCols; gamColInd++) {
      System.arraycopy(
              model._gamColNamesNoCentering[gamColInd], 0,
              model._output._coefficient_names_no_centering, gamLengthCopied,
              model._gamColNamesNoCentering[gamColInd].length
      );
      gamLengthCopied += model._gamColNamesNoCentering[gamColInd].length;
    }
    model._output._coefficient_names_no_centering[lastGAMCoeffIndex] = glmColNames[lastGLMCoeffIndex];// copy intercept
    return gamNumColStart;
  }

  public static void copyGLMCoeffs2GAMCoeffs(GAMModel model, GLMModel glm, GLMParameters.Family family,
                                             int gamNumStart, int nclass) {
    int numCoeffPerClass = model._output._coefficient_names_no_centering.length;
    if (family.equals(GLMParameters.Family.multinomial) || family.equals(GLMParameters.Family.ordinal)) {
      double[][] model_beta_multinomial = glm._output.get_global_beta_multinomial();
      double[][] standardized_model_beta_multinomial = glm._output.getNormBetaMultinomial();
      model._output._model_beta_multinomial_no_centering = new double[nclass][];
      model._output._standardized_model_beta_multinomial_no_centering = new double[nclass][];
      for (int classInd = 0; classInd < nclass; classInd++) {
        model._output._model_beta_multinomial_no_centering[classInd] = convertCenterBeta2Beta(model._output._zTranspose,
                gamNumStart, model_beta_multinomial[classInd], numCoeffPerClass);
        model._output._standardized_model_beta_multinomial_no_centering[classInd] = convertCenterBeta2Beta(model._output._zTranspose,
                gamNumStart, standardized_model_beta_multinomial[classInd], numCoeffPerClass);
      }
    } else {  // other families
      model._output._model_beta_no_centering = convertCenterBeta2Beta(model._output._zTranspose, gamNumStart,
              glm.beta(), numCoeffPerClass);
      model._output._standardized_model_beta_no_centering = convertCenterBeta2Beta(model._output._zTranspose, gamNumStart,
              glm._output.getNormBeta(), numCoeffPerClass);
    }
  }

  // This method carries out the evaluation of beta = Z betaCenter as explained in documentation 7.2
  public static double[] convertCenterBeta2Beta(double[][][] ztranspose, int gamNumStart, double[] centerBeta,
                                                int betaSize) {
    double[] originalBeta = new double[betaSize];
    if (ztranspose!=null) { // centering is performed
      int numGamCols = ztranspose.length;
      int gamColStart = gamNumStart;
      int origGamColStart = gamNumStart;
      System.arraycopy(centerBeta,0, originalBeta, 0, gamColStart);   // copy everything before gamCols
      for (int colInd=0; colInd < numGamCols; colInd++) {
        double[] tempCbeta = new double[ztranspose[colInd].length];
        System.arraycopy(centerBeta, gamColStart, tempCbeta, 0, tempCbeta.length);
        double[] tempBeta = ArrayUtils.multVecArr(tempCbeta, ztranspose[colInd]);
        System.arraycopy(tempBeta, 0, originalBeta, origGamColStart, tempBeta.length);
        gamColStart += tempCbeta.length;
        origGamColStart += tempBeta.length;
      }
      originalBeta[betaSize-1]=centerBeta[centerBeta.length-1];
    } else
      System.arraycopy(centerBeta, 0, originalBeta, 0, betaSize); // no change needed, just copy over

    return originalBeta;
  }
}
