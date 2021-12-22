package hex.genmodel.algos.coxph;

import hex.genmodel.MojoModel;

import java.io.Serializable;
import java.util.HashMap;

public class CoxPHMojoModel extends MojoModel  {

  static class Strata implements Serializable {
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
  HashMap<Strata, Integer> _strata; // HashMap to make sure the implementation is Serializable
  int _strata_len;
  double[][] _x_mean_cat;
  double[][] _x_mean_num;
  int[] _cat_offsets;
  int _cats;
  double[] _lpBase;
  boolean _useAllFactorLevels;

  CoxPHMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);

  }

  @Override
  public double[] score0(double[] row, double[] predictions) {
    predictions[0] = forCategories(row) + forOtherColumns(row) - forStrata(row);
    return predictions;
  }

  private double forOtherColumns(double[] row) {
    double result = 0.0;

    int catOffsetDiff = _cat_offsets[_cats] - _cats;
    for(int i = _cats ; i + catOffsetDiff < _coef.length; i++) {
      result += _coef[catOffsetDiff + i] * row[i + _strata_len];
    }
    
    return result;
  }

  private double forStrata(double[] row) {
    final int strata = strataForRow(row);
    return _lpBase[strata];
  }

  private double forCategories(double[] row) {
    double result = 0.0;

    if (!_useAllFactorLevels) {
    for(int category = 0; category < _cat_offsets.length - 1; ++category) {
        if (row[category] != 0) {
          result += forOneCategory(row, category, 1);
        }
      }
    } else {
      for(int category = 0; category < _cat_offsets.length - 1; ++category) {
        result += forOneCategory(row, category, 0);
      }
    }
    return result;
  }

  private double forOneCategory(double[] row, int category, int lowestFactorValue) {
    final int value = (int) row[category] - lowestFactorValue;
    if (value != row[category] - lowestFactorValue) {
      throw new IllegalArgumentException("categorical value out of range");
    }
    final int x = value + _cat_offsets[category];
    if (x < _cat_offsets[category + 1]) {
      return _coef[x];
    } else {
      return 0;
    }
  }

  double[] computeLpBase() {
    final int _numStart = _x_mean_cat.length >= 1 ?  _x_mean_cat[0].length : 0;
    final int size = 0 < _strata.size() ? _strata.size() : 1;
    double[] lpBase = new double[size];
    for (int s = 0; s < size; s++) {
      for (int i = 0; i < _x_mean_cat[s].length; i++)
        lpBase[s] += _x_mean_cat[s][i] * _coef[i];
      for (int i = 0; i < _x_mean_num[s].length; i++)
        lpBase[s] += _x_mean_num[s][i] * _coef[i + _numStart];
    }
    return lpBase;
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
