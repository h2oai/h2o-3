package hex.gam;


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
      double[] numNAFills = new double[model._output._dinfo.numNAFill().length+numGamCols];
      System.arraycopy(model._output._dinfo.numNAFill(),0, numNAFills, 0, 
              model._output._dinfo.numNAFill().length);
      int startind = numNAFills.length-model._gamColMeans.length;
      System.arraycopy(model._gamColMeans, 0, numNAFills, startind, model._gamColMeans.length);
      writekv("numNAFills", numNAFills);
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
    writekv("num_knots", model._parms._num_knots); // an array
    writeStringArrays(model._parms._gam_columns, "gam_columns"); // gam_columns specified by users
    int numGamLength = 0;
    int numGamCLength = 0;
    for (int cInd=0; cInd < numGamCols; cInd++)  { // only contains expanded gam column names not centered
      writeStringArrays(model._gamColNames[cInd], "gamColNamesCenter_"+model._parms._gam_columns[cInd]);
      writeStringArrays(model._gamColNamesNoCentering[cInd], "gamColNames_"+model._parms._gam_columns[cInd]);
      numGamLength += model._gamColNamesNoCentering[cInd].length;
      numGamCLength += model._gamColNames[cInd].length;
    }
    String[] trainColGamColNoCenter = genTrainColGamCols(numGamLength, numGamCLength);
    writekv("num_expanded_gam_columns", numGamLength);
    writeStringArrays(trainColGamColNoCenter, "_names_no_centering"); // column names without centering
    writekv("total feature size", trainColGamColNoCenter.length);
    if (model._parms._family==multinomial || model._parms._family==ordinal) {
      writeDoubleArray(model._output._model_beta_multinomial_no_centering, "beta_multinomial");
      writekv("beta length per class", model._output._model_beta_multinomial_no_centering[0].length);
      writeDoubleArray(model._output._model_beta_multinomial, "beta_multinomial_centering");
      writekv("beta center length per class", model._output._model_beta_multinomial[0].length);
    } else {
      writekv("beta", model._output._model_beta_no_centering); // beta without centering
      writekv("beta length per class", model._output._model_beta_no_centering.length);
      writekv("beta_center", model._output._model_beta);
      writekv("beta center length per class", model._output._model_beta.length);
    }
    writekv("bs", model._parms._bs);  // an array of choice of spline functions
    writeDoubleArray(model._output._knots, "knots");
    int countGamCols = 0;
    for (String gamCol : model._parms._gam_columns) {
      writeDoubleArray(model._output._zTranspose[countGamCols], gamCol+"_zTranspose");      // write zTranspose
      writeDoubleArray(model._output._binvD[countGamCols++], gamCol+"_binvD");      // write binvD
    }
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
