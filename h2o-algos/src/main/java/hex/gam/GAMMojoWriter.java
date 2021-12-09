package hex.gam;


import hex.ModelMetrics;
import hex.ModelMojoWriter;
import hex.glm.GLMModel;

import java.io.IOException;

import static hex.glm.GLMModel.GLMParameters.Family.*;

public class GAMMojoWriter extends ModelMojoWriter<GAMModel, GAMModel.GAMParameters, GAMModel.GAMModelOutput> {
  @Override
  public String mojoVersion() {
    return "1.00";
  }
  
  @SuppressWarnings("unused")
  public GAMMojoWriter(){}
  
  public GAMMojoWriter(GAMModel model) {
    super(model);
  }

  @Override
  protected void writeModelData() throws IOException {
    int numGamCols = model._parms._gam_columns.length;
    writekv("use_all_factor_levels", model._parms._use_all_factor_levels);
    writekv("cats", model._output._dinfo._cats);
    writekv("cat_offsets", model._output._dinfo._catOffsets);
    writekv("numsCenter", model._output._dinfo._nums);
    writekv("num", model._output._dinfo._nums+numGamCols);

    boolean imputeMeans = model._parms.missingValuesHandling().equals(GLMModel.GLMParameters.MissingValuesHandling.MeanImputation);
    writekv("mean_imputation", imputeMeans);
    if (imputeMeans) {
      writekv("numNAFillsCenter", model._output._dinfo.numNAFill());
      writekv("catNAFills", model._output._dinfo.catNAFill());
    }
    if (model._parms._family.equals(binomial))
      writekv("family", "bernoulli");
    else
      writekv("family", model._parms._family);
    writekv("link", model._parms._link);
    if (model._parms._family.equals(GLMModel.GLMParameters.Family.tweedie))
      writekv("tweedie_link_power", model._parms._tweedie_link_power);
    // add GAM specific parameters
    writekv("num_knots", model._parms._num_knots);                // an array
    writekv("num_knots_sorted", model._parms._num_knots_sorted);  // an array
    write2DStringArrays(model._parms._gam_columns, "gam_columns"); // gam_columns specified by users
    write2DStringArrays(model._parms._gam_columns_sorted, "gam_columns_sorted"); // gam_columns specified by users
    int numGamLength = 0;
    int numGamCLength = 0;
    for (int cInd=0; cInd < numGamCols; cInd++)  { // contains expanded gam column names center and not centered
      numGamLength += model._gamColNamesNoCentering[cInd].length;
      numGamCLength += model._gamColNames[cInd].length;
    }
    int[] gamColumnDim = genGamColumnDim(model._parms._gam_columns);
    writekv("gam_column_dim", gamColumnDim);              // an array indicating array size of parms._gam_columns
    int[] gamColumnDimSorted = genGamColumnDim(model._parms._gam_columns_sorted);
    writekv("gam_column_dim_sorted", gamColumnDimSorted); // an array
    String[] trainColGamColNoCenter = genTrainColGamCols(numGamLength, numGamCLength);
    writekv("num_expanded_gam_columns", numGamLength);
    writekv("num_expanded_gam_columns_center", numGamCLength);
    writeStringArrays(trainColGamColNoCenter, "_names_no_centering"); // column names without centering
    writekv("total feature size", trainColGamColNoCenter.length);
    int[] gamColNamesDim = genGamColumnDim(model._gamColNamesNoCentering);
    writekv("gamColName_dim", gamColNamesDim);
    write2DStringArrays(model._gamColNames, "gamColNamesCenter");// numGamCol by numKnots for CS, by numKnots+M for TP
    write2DStringArrays(model._gamColNamesNoCentering,"gamColNames"); // numGamCol by numKnots-1
    if (model._parms._family==multinomial || model._parms._family==ordinal) {
      write2DArray(model._output._model_beta_multinomial_no_centering, "beta_multinomial");
      writekv("beta length per class", model._output._model_beta_multinomial_no_centering[0].length);
      write2DArray(model._output._model_beta_multinomial, "beta_multinomial_centering");
      writekv("beta center length per class", model._output._model_beta_multinomial[0].length);
    } else {
      writekv("beta", model._output._model_beta_no_centering); // beta without centering
      writekv("beta length per class", model._output._model_beta_no_centering.length);
      writekv("beta_center", model._output._model_beta);
      writekv("beta center length per class", model._output._model_beta.length);
    }
    writekv("bs", model._parms._bs);                // an array of choice of spline function types
    writekv("bs_sorted", model._parms._bs_sorted);  // an array of choice of spline functions
    write3DArray(model._output._knots, "knots");
    write3DArray(model._output._zTranspose, "zTranspose");
    writekv("_d", model._parms._gamPredSize);
    if (model._output._zTransposeCS != null) {  // only for thin plate regression splines
      write3DIntArray(model._output._allPolyBasisList, "polynomialBasisList");
      write3DArray(model._output._zTransposeCS, "zTransposeCS");
      write2DArray(model._output._gamColMeansRaw, "gamColMeansRaw");
      write2DArray(model._output._oneOGamColStd, "gamColStdRaw");
      writekv("_M", model._parms._M);
      writekv("_m", model._parms._m);
      writekv("num_knots_TP", model._parms._num_knots_tp); // an array
      writekv("num_TP_col", model._parms._M.length);
      writekv("standardize", model._parms._standardize);
    } else {
      writekv("num_TP_col", 0);
    }
    if (model._cubicSplineNum > 0)
      write3DArray(model._output._binvD, "_binvD");
  }

  @Override
  public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() {
    return new GAMMetricBuilderFactory();
  }

  public int[] genGamColumnDim(String[][] gamColumnNames) {
    int numGamCols = gamColumnNames.length;
    int[] gamColDim = new int[numGamCols];
    for (int index = 0; index < numGamCols; index++)
      gamColDim[index] = gamColumnNames[index].length;
    return gamColDim;
  }
  
  public String[] genTrainColGamCols(int gamColLength, int gamCColLength) {
    int colLength = model._output._names.length-gamCColLength+gamColLength-1;// to exclude response
    int normalColLength = model._output._names.length-gamCColLength-1;
    String[] trainNamesNGamNames = new String[colLength];
    System.arraycopy(model._output._names, 0, trainNamesNGamNames, 0, normalColLength);
    int startInd = normalColLength;
    for (int gind = 0; gind < model._gamColNamesNoCentering.length; gind++) {
      int copyLen = model._gamColNamesNoCentering[gind].length;
      System.arraycopy(model._gamColNamesNoCentering[gind], 0, trainNamesNGamNames, startInd, copyLen);
      startInd += copyLen;
    }
    return trainNamesNGamNames;
  }
}
