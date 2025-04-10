package hex.genmodel.algos.coxph;

import hex.genmodel.MojoModel;

import java.io.Serializable;
import java.util.*;

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
  
  enum InteractionTypes {ENUM_TO_ENUM, ENUM_TO_NUM, NUM_TO_NUM};
  double[] _coef;
  HashMap<Strata, Integer> _strata; // HashMap to make sure the implementation is Serializable
  int _strata_len;
  double[][] _x_mean_cat;
  double[][] _x_mean_num;
  int[] _cat_offsets;
  int _cats;
  double[] _lpBase;
  boolean _useAllFactorLevels;
  int _nums;

  int[] _interactions_1;
  int[] _interactions_2;
  int[] _interaction_targets;
  boolean[] _is_enum_1; // check interaction column1 column type
  HashSet<Integer> _interaction_column_index;
  HashMap<Integer, List<String>> _interaction_column_domains;
  InteractionTypes[] _interaction_types;
  int[] _num_offsets;
 
  CoxPHMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] predictions) {
    return score0(row, 0, predictions);
  }

  @Override
  public double[] score0(double[] row, double offset, double[] predictions) {
    int[] enumOffset = null;
    
    if (_nums == -1) {
      predictions[0] = forCategories(row) + forOtherColumns(row) - forStrata(row) + offset;
    } else {
      if (_interaction_targets != null) {
        enumOffset = evaluateInteractions(row);
      }
      predictions[0] = forCategories(row) + forOtherColumns(row, enumOffset) - forStrata(row) + offset; 
    }
    return predictions;
  }

  private double forOtherColumns(double[] row) {
    double result = 0.0;
    int catOffsetDiff = _cat_offsets[_cats] - _cats;
    for(int i = _cats ; i + catOffsetDiff < _coef.length; i++) {
      result += _coef[catOffsetDiff + i] * featureValue(row, i);
    }
    return result;
  }
  
  private double forOtherColumns(double[] row, int[] enumOffset) {
    double result = 0.0;
    int coefLen = _coef.length;
    
    for(int i = 0 ; i < _nums; i++) {
      if (enumOffset == null || enumOffset[i] < 0) {
        if (_num_offsets[i] >= coefLen)
          break;
        result += _coef[_num_offsets[i]] * featureValue(row, i + _cats);
      } else {
        if (enumOffset[i] >= coefLen)
          break;
        result += _coef[enumOffset[i]] * featureValue(row, i + _cats);
      }
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
    for(int category = 0; category < _cats; ++category) {
      double val = featureValue(row, category);
      if (Double.isNaN(val)) {
        result = Double.NaN;
      } else if (val >= 0) {
          if (_interaction_column_index.contains(category))
            result += forOneCategory(row, category, 0); // already taken into account the useAllFactorLevels
          else
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

  double forOneCategory(double[] row, int category, int lowestFactorValue) {
    final int value = (int) featureValue(row, category) - lowestFactorValue;
    if (value != featureValue(row, category) - lowestFactorValue) {
      throw new IllegalArgumentException("categorical value out of range");
    }
    final int x = value + _cat_offsets[category]; // value will be < 0 if cat value is not within domain
    if (value >= 0 && x < _cat_offsets[category + 1]) {
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

  double featureValue(double[] row, int featureIdx) {
    return row[featureIdx + _strata_len];
  }

  private int strataForRow(double[] row) {
    if (0 == _strata.size()) {
      return 0;
    } else {
      final Strata o = new Strata(row, _strata_len);
      return _strata.get(o);
    }
  }

  private int[] evaluateInteractions(double[] row) {
    int[] enumOffset = new int[_nums];
    Arrays.fill(enumOffset, -1);
    for (int interactionIndex = 0; interactionIndex < _interaction_targets.length; interactionIndex++) {
      final int target = _interaction_targets[interactionIndex];  // index into row
      if (Double.isNaN(row[target])) {
        if (InteractionTypes.ENUM_TO_ENUM.equals(_interaction_types[interactionIndex])) { // enum to enum interaction
          row[target] = enumEnumInteractions(row, interactionIndex);
        } else if (InteractionTypes.NUM_TO_NUM.equals(_interaction_types[interactionIndex])) { // num to num interaction
          row[target] = row[_interactions_1[interactionIndex]] * row[_interactions_2[interactionIndex]];
        } else {  // enum to num interaction
          enumNumInteractions(row, enumOffset, interactionIndex, target);
        }
      }
    }
    return enumOffset;
  }

  /**
   * Again, this method is similar to extractDenseRow method of DatInfo.java.  It stores the interactionOffset (
   * as catLevel here) in enumOffset and store the numerical value back into the row at the correct rowIndex.  If the
   * catlevel is not valid, a value of 0.0 will be store at the row at the rowIndex.
   */
  private void enumNumInteractions(double[] row, int[] enumOffset, int interactionIndex, int rowIndex) {
    int enumPredIndex = _is_enum_1[interactionIndex] ? _interactions_1[interactionIndex] : _interactions_2[interactionIndex];
    int numPredIndex = _is_enum_1[interactionIndex] ? _interactions_2[interactionIndex] : _interactions_1[interactionIndex];
    int offset = _num_offsets[rowIndex - _cats];
    int catLevel = (int) row[enumPredIndex]-(_useAllFactorLevels?0:1);
    row[rowIndex] = catLevel < 0 ? 0 : row[numPredIndex];
    enumOffset[rowIndex-_cats] = catLevel+offset;
  }

  /**
   * This method is similar to extractDenseRow method of DataInfo.java.  Basically, it takes the domain of column 1
   * and domain of column 2 to form the new combined domain: as domain1_domain2.  Then, it will look up the index
   * of this new combination in the combinedDomains.  If it is found, it will return the index.  It not, will return
   * -1.
   */
  private int enumEnumInteractions(double[] row, int interactionIndex) {
    List<String> combinedDomains = _interaction_column_domains.get(_interaction_targets[interactionIndex]);
    int predictor1Index = _interactions_1[interactionIndex];  // original column index into row
    int predictor2Index = _interactions_2[interactionIndex];
    String[] predictor1Domains = _domains[predictor1Index];
    String[] predictor2Domains = _domains[predictor2Index];
    String predictor1Domain = predictor1Domains[(int) row[predictor1Index]];
    String predictor2Domain = predictor2Domains[(int) row[predictor2Index]];
    String combinedEnumDomains = predictor1Domain+"_"+predictor2Domain;
    if (combinedDomains.contains(combinedEnumDomains))
      return combinedDomains.indexOf(combinedEnumDomains);
    else 
      return -1;
  }
}
