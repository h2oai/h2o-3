package hex.genmodel.algos.coxph;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.util.*;

public class CoxPHMojoReader extends ModelMojoReader<CoxPHMojoModel> {

  @Override
  public String getModelName() {
    return "CoxPH";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._x_mean_cat = readRectangularDoubleArray("x_mean_cat");
    _model._x_mean_num = readRectangularDoubleArray("x_mean_num");
    _model._coef = readkv("coef");
    _model._strata = readStrata();
    _model._strata_len = readStrataLen();
    _model._coef_indexes = readkv("coef_indexes");
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


  @Override
  protected CoxPHMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new CoxPHMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() { return "1.00"; }

}
