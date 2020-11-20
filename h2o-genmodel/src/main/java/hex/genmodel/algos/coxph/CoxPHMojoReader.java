package hex.genmodel.algos.coxph;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

public class CoxPHMojoReader extends ModelMojoReader<CoxPHMojoModel> {

  @Override
  public String getModelName() {
    return "CoxPH";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._x_mean_cat = readkv2D_doubles("x_mean_cat");
    _model._x_mean_num = readkv2D_doubles("x_mean_num");
    _model._coef = readkv("coef");
    int strataCount = readkv("strata_count");
    _model._strataCount = 0 == strataCount ? 1 : strataCount;
    _model._coef_indexes = readkv("coef_indexes");
  }

  private double[][] readkv2D_doubles(String keyPrefix) {
    assert null != keyPrefix;
    
    final int count = readkv(keyPrefix + "_num");
    final double[][] result = new double[count][];
    for (int i = 0; i < count; i++) {
      System.out.println(keyPrefix+"_"+i);
      result[i] = readkv(keyPrefix + "_" + i, new double[0]);
    }
    
    return result;
  }

  @Override
  protected CoxPHMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new CoxPHMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() { return "1.00"; }

}
