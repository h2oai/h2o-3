package hex.genmodel.algos.coxph;

import hex.genmodel.MojoModel;

import java.util.Arrays;

public class CoxPHMojoModel extends MojoModel  {
  public double[] _coef;
  public int[] _coef_indexes;
  public int _strataCount;
  public int _numStart;
  double[][] _x_mean_cat;
  double[][] _x_mean_num;

  CoxPHMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] predictions) {
    double result = 0.0;
    
    double[] lpBase = new double[_strataCount];
    for (int s = 0; s < _strataCount; s++) {
      for (int i = 0; i < _x_mean_cat[s].length; i++)
        lpBase[s] += _x_mean_cat[s][i] * _coef[i];
      for (int i = 0; i < _x_mean_num[s].length; i++)
        lpBase[s] += _x_mean_num[s][i] * _coef[i + _numStart];
    }
    
    for (int i = 0; i < _coef_indexes.length; i++) {
      result += row[i] * _coef[_coef_indexes[i]];
    }
    result -= lpBase[strataForRow(row)];
    predictions[0] = result;
    return predictions;
  }

  private int strataForRow(double[] row) {
    return 0;
  }

}
