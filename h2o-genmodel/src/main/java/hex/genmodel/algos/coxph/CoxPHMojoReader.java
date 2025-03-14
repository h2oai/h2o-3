package hex.genmodel.algos.coxph;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

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
    _model._cat_offsets = readkv("cat_offsets");
    _model._cats = readkv("cats");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");
    _model._lpBase = _model.computeLpBase();
    _model._interaction_targets = readkv("interaction_targets");
    _model._interaction_column_index = new HashSet<>();
    _model._interaction_column_domains = new HashMap<>();
    _model._nums = readkv("num_numerical_columns", -1);
    _model._num_offsets = readkv("num_offsets");
    
    if (_model._interaction_targets != null) {
      _model._interactions_1 = readkv("interactions_1");
      _model._interactions_2 = readkv("interactions_2");

      for (int index : _model._interaction_targets) {
        _model._interaction_column_index.add(index);
        if (_model._domains[index] != null)
          _model._interaction_column_domains.put(index, Arrays.asList(_model._domains[index]));
      }
      createInteractionTypes();
    }
  }

  private void createInteractionTypes() {
    int numInteractions = _model._interaction_targets.length;
    _model._interaction_types = new CoxPHMojoModel.InteractionTypes[numInteractions];
    _model._is_enum_1 = new boolean[numInteractions];
    for (int index=0; index<numInteractions; index++) {
      if (_model._domains[_model._interactions_1[index]] != null && _model._domains[_model._interactions_2[index]] != null) {
        _model._interaction_types[index] = CoxPHMojoModel.InteractionTypes.ENUM_TO_ENUM;
        _model._is_enum_1[index] = true;
      } else if ((_model._domains[_model._interactions_1[index]] == null && _model._domains[_model._interactions_2[index]] == null)) {
        _model._interaction_types[index] = CoxPHMojoModel.InteractionTypes.NUM_TO_NUM;
      } else {
        _model._interaction_types[index] = CoxPHMojoModel.InteractionTypes.ENUM_TO_NUM;
        if (_model._domains[_model._interactions_1[index]] != null)
          _model._is_enum_1[index] = true;
      }
    }
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
