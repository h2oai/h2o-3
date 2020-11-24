package hex.genmodel.algos.gam;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.utils.DistributionFamily;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    _model._family = DistributionFamily.valueOf((String)readkv("family"));
    _model._cats = readkv("cats", -1);
    _model._nums = readkv("num");
    _model._numsCenter = readkv("numsCenter");
    _model._catNAFills = readkv("catNAFills", new int[0]);
    _model._numNAFills = readkv("numNAFills", new double[0]);
    _model._numNAFillsCenter = readkv("numNAFillsCenter", new double[0]);;
    _model._meanImputation = readkv("mean_imputation", false);
    _model._betaSizePerClass = readkv("beta length per class",0);
    _model._catOffsets = readkv("cat_offsets", new int[0]);
    if (!_model._family.equals(DistributionFamily.multinomial))  // multinomial or ordinal have specific link functions not included in general link functions
      _model._link_function = readLinkFunction((String) readkv("link"), _model._family);
    _model._tweedieLinkPower = readkv("tweedie_link_power", 0.0);
    _model._betaCenterSizePerClass = readkv("beta center length per class", 0);
    if (_model._family.equals(DistributionFamily.multinomial) || _model._family.equals(ordinal)) {
      _model._beta_multinomial_no_center = read2DArray("beta_multinomial", _model._nclasses, _model._betaSizePerClass);
      _model._beta_multinomial_center = read2DArray("beta_multinomial_centering", _model._nclasses, 
              _model._betaCenterSizePerClass);
    } else {
      _model._beta_no_center = readkv("beta");
      _model._beta_center = readkv("beta_center");
    }
    // read in GAM specific parameters
    _model._num_knots = readkv("num_knots");
    int num_gam_columns = _model._num_knots.length;
    _model._gam_columns = readStringArrays(num_gam_columns,"gam_columns");
    _model._num_gam_columns = _model._gam_columns.length;
    _model._totFeatureSize = readkv("total feature size");
    _model._names_no_centering = readStringArrays(_model._totFeatureSize, "_names_no_centering");
    _model._bs = readkv("bs");
    _model._knots = new double[num_gam_columns][];
    _model._binvD = new double[num_gam_columns][][];
    _model._zTranspose = new double[num_gam_columns][][];
    _model._knots = read2DArrayDiffLength("knots", _model._knots, _model._num_knots);
    _model._gamColNames = new String[num_gam_columns][];
    _model._gamColNamesCenter = new String[num_gam_columns][];
    for (int gInd = 0; gInd < num_gam_columns; gInd++) {
      int num_knots = _model._num_knots[gInd];
      _model._binvD[gInd] = new double[num_knots-2][num_knots];
      _model._binvD[gInd] = read2DArray(_model._gam_columns[gInd]+"_binvD", _model._binvD[gInd].length, 
              _model._binvD[gInd][0].length);
      _model._zTranspose[gInd] = new double[num_knots-1][num_knots];
      _model._zTranspose[gInd] = read2DArray(_model._gam_columns[gInd]+"_zTranspose", 
              _model._zTranspose[gInd].length, _model._zTranspose[gInd][0].length);
      _model._gamColNames[gInd] = readStringArrays(num_knots,"gamColNames_"+_model._gam_columns[gInd]);
      _model._gamColNamesCenter[gInd] = readStringArrays(num_knots-1,"gamColNamesCenter_"+_model._gam_columns[gInd]);
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
