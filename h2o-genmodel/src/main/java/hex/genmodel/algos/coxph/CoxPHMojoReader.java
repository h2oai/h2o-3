package hex.genmodel.algos.coxph;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
    _model._coef_names = readCoefNames();
    _model._strata = readStrata();
    _model._strata_len = readStrataLen();
  }

  private String[] readCoefNames() {
    final int count = readkv("coef_names_num");
    final String[] result = new String[count];
    for (int i = 0; i < count; i++) {
      result[i] = readkv("coef_names_" + i);
    }
    return result;
  }

  private Map<List<Integer>, Integer> readStrata() {
    final int count = readkv("strata_count");
    
    final Map<List<Integer>, Integer> result = new HashMap<List<Integer>, Integer>(count);

    for (int i = 0; i < count; i++) {
      final double[] strata = readkv("strata_" + i);
      final List<Integer> strataAsList = new ArrayList<>(strata.length);
      for (int j = 0; j < strata.length; j++) {
        strataAsList.add(j, (int) strata[j]);
      }
      result.put(strataAsList, i);
    }
    return result;
  }
  
  private int readStrataLen() {
    final int count = readkv("strata_count");
    
    if (0 == count) {
      return 0;
    } else {
      final double[] strata = readkv("strata_0");
      return strata.length;
    }
  }

  private double[][] readkv2D_doubles(String keyPrefix) {
    assert null != keyPrefix;
    
    final int count = readkv(keyPrefix + "_num");
    final double[][] result = new double[count][];
    for (int i = 0; i < count; i++) {
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
