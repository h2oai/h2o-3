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
  protected String getModelMojoReaderClassName() { return "hex.coxph.CoxPHMojoWriter"; }

  @Override
  protected void readModelData(final boolean readModelMetadata) throws IOException {
    _model._x_mean_cat = readRectangularDoubleArray("x_mean_cat");
    _model._x_mean_num = readRectangularDoubleArray("x_mean_num");
    _model._coef = readkv("coef");
    _model._strata = readStrata();
    _model._strata_len = readStrataLen();
    _model._cat_offsets = readkv("cat_offsets");
    _model._cats = readkv("cats");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");
    _model._lpBase = _model.computeLpBase();
  }

  

  private HashMap<CoxPHMojoModel.Strata, Integer> readStrata() {
    final int count = readkv("strata_count");
    final HashMap<CoxPHMojoModel.Strata, Integer> result = new HashMap<>(count);
    for (int i = 0; i < count; i++) {
      final double[] strata = readkv("strata_" + i);
      result.put(new CoxPHMojoModel.Strata(strata, strata.length), i);
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
