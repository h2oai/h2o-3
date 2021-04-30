package hex.genmodel.algos.rulefit;

import hex.genmodel.MojoModel;
import java.util.Arrays;
import java.util.List;

public class RuleFitMojoModel extends MojoModel {

  MojoModel _linearModel;
  MojoRuleEnsemble _ruleEnsemble;
  int depth;
  int ntrees;
  int model_type; // 0 = LINEAR, 1 = RULES_AND_LINEAR, 2 = RULES
  String[] dataFromRulesCodes;
  
  RuleFitMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    double[] linearFromRules = null;
    int testsize = 0;
    if (model_type != 0) {
      linearFromRules = _ruleEnsemble.transformRow(row, depth, ntrees, _linearModel._names, _linearModel._domains);
      testsize += linearFromRules.length;
      if (model_type == 1) {
        testsize += row.length;
      }
    }
    double[] test = new double[testsize];
    if (model_type == 1 || model_type == 2) {
      System.arraycopy(linearFromRules, 0, test, 0, linearFromRules.length);
    }
    if (model_type == 1) {
      System.arraycopy(row, 0, test, linearFromRules.length, row.length);
    }
    if (model_type == 0) {
      test = row;
    }
    _linearModel.score0(map(test), preds);
    return preds;
  }
  
  double[] map(double[] test) {
    double[] newtest = test.clone();
    List list = Arrays.asList(_linearModel._names);
    for (int i = 0; i < dataFromRulesCodes.length; i++) {
      int id = list.indexOf(dataFromRulesCodes[i]);
      newtest[id] = test[i];
    }
    return newtest;
  }
}
