package hex.genmodel.algos.coxph;

import hex.genmodel.MojoModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CoxPHMojoModel extends MojoModel  {
  double[] _coef;
  String[] _coef_names;
  int _numStart;
  Map<List<Integer>, Integer> _strata;
  int _strata_len;
  double[][] _x_mean_cat;
  double[][] _x_mean_num;
  private int[] _coef_indexes;

  CoxPHMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);

  }

  void prepareCoefIndexes() {
    final List<String> namesInList = Arrays.asList(_modelDescriptor.columnNames());
    _coef_indexes = new int[_coef_names.length];
    int i = 0;
    for (String coefName : _coef_names) {
      Integer index = namesInList.indexOf(coefName);
      _coef_indexes[i++] = index;
    }
  }

  @Override
  public double[] score0(double[] row, double[] predictions) {
    if (null == _coef_indexes) {
      prepareCoefIndexes();
    }
    
    double result = 0.0;

    final int size = 0 < _strata.size() ? _strata.size() : 1;
    double[] lpBase = new double[size];
    for (int s = 0; s < size; s++) {
      for (int i = 0; i < _x_mean_cat[s].length; i++)
        lpBase[s] += _x_mean_cat[s][i] * _coef[i];
      for (int i = 0; i < _x_mean_num[s].length; i++)
        lpBase[s] += _x_mean_num[s][i] * _coef[i + _numStart];
    }
    
    for (int i = 0; i < _coef_names.length; i++) {
      final int coefIndex = _coef_indexes[i];
      result += row[coefIndex] * _coef[i];
    }
    
    result -= lpBase[strataForRow(row)];
    
    predictions[0] = result;
    return predictions;
  }
  
  private int strataForRow(double[] row) {
    if (0 == _strata.size()) {
      return 0;
    } else {
      List<Integer> a = new ArrayList<>(_strata_len);
      for (int i = 0; i < _strata_len; i++) {
        a.add(i, (int) row[i]);
      }
      return _strata.get(a);
    }
  }

}
