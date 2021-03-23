package hex.genmodel.algos.gam;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.utils.DistributionFamily;

import java.io.IOException;
import java.nio.ByteBuffer;

import static hex.genmodel.utils.ArrayUtils.subtract;
import static hex.genmodel.utils.DistributionFamily.ordinal;

public class GamMojoReader extends ModelMojoReader<GamMojoModelBase> {

  @Override
  public String getModelName() {
    return "Generalized Additive Model";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._useAllFactorLevels = readkv("use_all_factor_levels", false);
    _model._numExpandedGamCols = readkv("num_expanded_gam_columns",0);
    _model._numExpandedGamColsCenter = readkv("num_expanded_gam_columns_center",0);
    _model._family = DistributionFamily.valueOf((String)readkv("family"));
    _model._cats = readkv("cats", -1);
    _model._nums = readkv("num");
    _model._numsCenter = readkv("numsCenter");
    _model._catNAFills = readkv("catNAFills", new int[0]);
    _model._numNAFillsCenter = readkv("numNAFillsCenter", new double[0]);;
    _model._meanImputation = readkv("mean_imputation", false);
    _model._betaSizePerClass = readkv("beta length per class",0);
    _model._catOffsets = readkv("cat_offsets", new int[0]);
    if (!_model._family.equals(DistributionFamily.multinomial))  // multinomial or ordinal have specific link functions not included in general link functions
      _model._link_function = readLinkFunction((String) readkv("link"), _model._family);
    _model._tweedieLinkPower = readkv("tweedie_link_power", 0.0);
    _model._betaCenterSizePerClass = readkv("beta center length per class", 0);
    if (_model._family.equals(DistributionFamily.multinomial) || _model._family.equals(ordinal)) {
      _model._beta_multinomial_no_center = readRectangularDoubleArray("beta_multinomial", _model._nclasses, _model._betaSizePerClass);
      _model._beta_multinomial_center = readRectangularDoubleArray("beta_multinomial_centering", _model._nclasses, 
              _model._betaCenterSizePerClass);
    } else {
      _model._beta_no_center = readkv("beta");
      _model._beta_center = readkv("beta_center");
    }
    // read in GAM specific parameters
    _model._num_knots = readkv("num_knots");
    _model._num_knots_sorted = readkv("num_knots_sorted");
    int[] gamColumnDim = readkv("gam_column_dim");
    _model._gam_columns = read2DStringArrays(gamColumnDim,"gam_columns");
    int[] gamColumnDimSorted = readkv("gam_column_dim_sorted");
    _model._gam_columns_sorted = read2DStringArrays(gamColumnDimSorted,"gam_columns_sorted");
    _model._num_gam_columns = _model._gam_columns.length;
    _model._num_TP_col = readkv("num_TP_col");
    _model._num_CS_col = _model._num_gam_columns-_model._num_TP_col;
    _model._totFeatureSize = readkv("total feature size");
    _model._names_no_centering = readStringArrays(_model._totFeatureSize, "_names_no_centering");
    _model._bs = readkv("bs");
    _model._bs_sorted = readkv("bs_sorted");
    _model._zTranspose = new double[_model._num_gam_columns][][];
    int[] gamColName_dim = readkv("gamColName_dim");
    _model._gamColNames = read2DStringArrays(gamColName_dim, "gamColNames");
    _model._gamColNames = new String[_model._num_gam_columns][];
    _model._gamColNamesCenter = new String[_model._num_gam_columns][];
    _model._gamPredSize = readkv("_d");
    if (_model._num_TP_col > 0) {
      _model._standardize = readkv("standardize");
      _model._zTransposeCS = new double[_model._num_TP_col][][];
      _model._num_knots_TP = readkv("num_knots_TP");
      _model._d = readkv("_d");
      _model._m = readkv("_m");
      _model._M = readkv("_M");
      int[] predSize = new int[_model._num_TP_col];
      System.arraycopy(predSize, predSize.length-_model._num_TP_col, predSize, 0, _model._num_TP_col);
      _model._gamColMeansRaw = read2DDoubleArrays(predSize, "gamColMeansRaw");
      _model._oneOGamColStd = read2DDoubleArrays(predSize, "gamColStdRaw");
      int[] numKnotsMM = subtract(_model._num_knots_TP, _model._M);
      _model._zTransposeCS = read3DArray("zTransposeCS", _model._num_TP_col, numKnotsMM, _model._num_knots_TP);
      int[] predNum = new int[_model._num_TP_col];
      System.arraycopy(_model._d, _model._num_CS_col, predNum, 0, _model._num_TP_col);
      _model._allPolyBasisList = read3DIntArray("polynomialBasisList", _model._num_TP_col, _model._M, predNum);
    }
    int[] numKnotsM1 = subtract(_model._num_knots_sorted, 1);
    _model._gamColNamesCenter = read2DStringArrays(numKnotsM1, "gamColNamesCenter");
    _model._zTranspose = read3DArray("zTranspose", _model._num_gam_columns, numKnotsM1, _model._num_knots_sorted);
    _model._knots = read3DArray("knots", _model._num_gam_columns, _model._gamPredSize, _model._num_knots_sorted);
    if (_model._num_CS_col > 0) {
      int[] numKnotsM2 = subtract(_model._num_knots_sorted, 2);
      _model._binvD = read3DArray("_binvD", _model._num_CS_col, numKnotsM2, _model._num_knots_sorted);
    }
    _model.init();
  }
  
  String[] readStringArrays(int aSize, String title) throws IOException {
    String[] stringArrays = new String[aSize];
    int counter = 0;
    for (String line : readtext(title)) {
      stringArrays[counter++] = line;
    }
    return stringArrays;
  }

  String[][] read2DStringArrays(int[] arrayDim, String title) throws IOException {
    int firstDim = arrayDim.length;
    String[][] stringArrays = new String[firstDim][];
    int indexDim1 = 0;
    int indexDim2 = 0;
    for (int index = 0; index < firstDim; index++)
      stringArrays[index] = new String[arrayDim[index]];
    for (String line : readtext(title)) {
      if (indexDim2 >= stringArrays[indexDim1].length) { // go to next dim
        indexDim1++;
        indexDim2 = 0;
      }
      stringArrays[indexDim1][indexDim2] = line;
      indexDim2++;
    }
    return stringArrays;
  }

  double[][] read2DDoubleArrays(int[] arrayDim, String title) throws IOException {
    int firstDim = arrayDim.length;
    double[][] doubleArrays = new double[firstDim][];
    ByteBuffer bb = ByteBuffer.wrap(readblob(title));
    for (int index = 0; index < firstDim; index++) {
      doubleArrays[index] = new double[arrayDim[index]];
      for (int index2nd = 0; index2nd < arrayDim[index]; index2nd++) {
        doubleArrays[index][index2nd] = bb.getDouble();
      }
    }
    return doubleArrays;
  }
  
  double[][] read2DArray(String title, int firstDSize, int secondDSize) throws IOException {
    double [][] row = new double[firstDSize][secondDSize];
    ByteBuffer bb = ByteBuffer.wrap(readblob(title));
    for (int i = 0; i < firstDSize; i++) {
      for (int j = 0; j < secondDSize; j++)
        row[i][j] = bb.getDouble();
    }
    return row;
  }

  int[][][] read3DIntArray(String title, int firstDimSize, int[] secondDim, int[] thirdDim) throws IOException {
    int [][][] row = new int[firstDimSize][][];
    ByteBuffer bb = ByteBuffer.wrap(readblob(title));
    for (int i = 0; i < firstDimSize; i++) {
      row[i] = new int[secondDim[i]][thirdDim[i]];
      for (int j = 0; j < secondDim[i]; j++) {
        for (int k = 0; k < thirdDim[i]; k++)
          row[i][j][k] = bb.getInt();
      }
    }
    return row;
  }

  double[][][] read3DArray(String title, int firstDimSize, int[] secondDim, int[] thirdDim) throws IOException {
    double [][][] row = new double[firstDimSize][][];
    ByteBuffer bb = ByteBuffer.wrap(readblob(title));
    for (int i = 0; i < firstDimSize; i++) {
      row[i] = new double[secondDim[i]][thirdDim[i]];
      for (int j = 0; j < secondDim[i]; j++) {
        for (int k = 0; k < thirdDim[i]; k++)
          row[i][j][k] = bb.getDouble();
      }
    }
    return row;
  }
  
  double[][] read2DArrayDiffLength(String title, double[][] row, int[] num_knots) throws IOException {
    int numGamColumns = num_knots.length;
    ByteBuffer bb = ByteBuffer.wrap(readblob(title));
    for (int i = 0; i < numGamColumns; i++) {
      row[i] = new double[num_knots[i]];
      for (int j = 0; j < row[i].length; j++)
      row[i][j] = bb.getDouble();
    }
    return row;
  }

  @Override
  protected GamMojoModelBase makeModel(String[] columns, String[][] domains, String responseColumn) {
    String family = readkv("family");
    if ("multinomial".equals(family) || "ordinal".equals(family))
      return new GamMojoMultinomialModel(columns, domains, responseColumn);
    else
      return new GamMojoModel(columns, domains, responseColumn);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }
}
