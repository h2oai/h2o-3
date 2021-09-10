package hex.genmodel.algos.rulefit;

import hex.genmodel.MojoModel;
import java.util.Arrays;
import java.util.List;

public class RuleFitMojoModel extends MojoModel {

  public enum ModelType {LINEAR, RULES_AND_LINEAR, RULES}
  
  MojoModel _linearModel;
  MojoRuleEnsemble _ruleEnsemble;
  int _depth;
  int _ntrees;
  ModelType _modelType;
  String[] _dataFromRulesCodes;
  String _weightsColumn;
  String[] _linearNames;
  
  RuleFitMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    double[] linearFromRules = null;
    int testsize = 0;
    if (!_modelType.equals(ModelType.LINEAR)) {
      linearFromRules = _ruleEnsemble.transformRow(row, _depth, _ntrees, _linearModel._names, _linearModel._domains);
      testsize += linearFromRules.length;
      if (_modelType.equals(ModelType.RULES_AND_LINEAR)) {
        testsize += row.length;
      }
    }
    double[] test = new double[testsize];
    if (_modelType.equals(ModelType.RULES_AND_LINEAR) || _modelType.equals(ModelType.RULES)) {
      System.arraycopy(linearFromRules, 0, test, 0, linearFromRules.length);
    }
    if (_modelType.equals(ModelType.RULES_AND_LINEAR)) {
      System.arraycopy(row, 0, test, linearFromRules.length, row.length);
    }
    if (_modelType.equals(ModelType.LINEAR)) {
      test = row;
    }
    double[] linearModelInput = map(test);
    
    _linearModel.score0(linearModelInput, preds);
    
    // if current weight is zero, zero the prediction
    if (_weightsColumn != null) {
      int weightsId = Arrays.asList(_linearNames).indexOf("linear." + _weightsColumn);
      double currWeight = test[weightsId];
      if (currWeight == 0.0) {
        for (int i = 0; i < preds.length; i++)
          preds[i] *= currWeight;
      }
    }
    
    return preds;
  }
  
  double[] map(double[] test) {
    double[] newtest = new double[_linearModel.nfeatures()];
    List<String> list = Arrays.asList(_linearModel._names);
    for (int i = 0; i < _linearModel.nfeatures(); i++) {
      int id = list.indexOf(_linearNames[i]);
      newtest[id] = test[i];
    }
    return newtest;
  }

    @Override public int nfeatures() {
      return _names.length;
    }
}
