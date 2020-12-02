package hex.genmodel.algos.coxph;

import hex.genmodel.MojoModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CoxPHMojoModel extends MojoModel  {
  
  static class Strata {
    final double[] strata;
    final int strataLen;
    final int hashCode;

    public Strata(double[] strata, int strataLen) {
      this.strata = strata;
      
      int hash = 11;
      for (int i = 0; i < strataLen; i++) {
        hash *= 13;
        hash += 17 * (int) strata[i];
      }
      hashCode = hash;

      this.strataLen = strataLen;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Strata that = (Strata) o;
      if (this.hashCode != that.hashCode) return false;
      if (this.strataLen != that.strataLen) return false;

      for (int i = 0; i < strataLen; i++) {
        if ((int) strata[i] != (int) that.strata[i]) return false;
      } 
      return true;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }
  
  double[] _coef;
  int _numStart;
  Map<Strata, Integer> _strata;
  int _strata_len;
  double[][] _x_mean_cat;
  double[][] _x_mean_num;
  int[] _coef_indexes;

  CoxPHMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);

  }

  @Override
  public double[] score0(double[] row, double[] predictions) {
    double result = 0.0;

    final int size = 0 < _strata.size() ? _strata.size() : 1;
    double[] lpBase = new double[size];
    for (int s = 0; s < size; s++) {
      for (int i = 0; i < _x_mean_cat[s].length; i++)
        lpBase[s] += _x_mean_cat[s][i] * _coef[i];
      for (int i = 0; i < _x_mean_num[s].length; i++)
        lpBase[s] += _x_mean_num[s][i] * _coef[i + _numStart];
    }
    
    for (int i = 0; i < _coef_indexes.length; i++) {
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
      final Strata o = new Strata(row, _strata_len);
      return _strata.get(o);
    }
  }

}
