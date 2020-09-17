package hex.genmodel.algos.targetencoder;

import java.io.Serializable;
import java.util.*;

public class EncodingMap implements Serializable {
  
  private static final Integer NO_TARGET_CLASS = -1;

  /**
   * Represents mapping from categorical level index to:
   * - a 2-elements array of `numerator` and `denominator` for regression and binary problems.
   * - a 3-elements array of `numerator`, `denominator` and `targetclass` for multiclass problems.
   * Those are then used to calculate the target frequencies.
   * Note that the last (group of) index is reserved for NA level and we rely on this fact.
   * 
   *  Example:
   *     a binary mapping (regression is similar with numerator accepting any nunerical value):
   *     Map ( 
   *        0 = "A" -> [ 4, 7 ],
   *        1 = "B" -> [ 2, 8 ],
   *        2 = "C" -> [ 7, 12 ],
   *        3 = "COL_NAME_NA" -> [ 5, 6 ],
   *      ) 
   *
   *     a multiclass ('y' = 0, 'n' = 1, 'maybe' = 2, 'NA' = 3) mapping:
   *     Map ( 
   *        0 = "A" -> Map (
   *             "y" = 0 -> [ 4, 7 ],
   *             "n" = 1 -> [ 2, 7 ],
   *             "maybe" = 2 -> [ 1, 7 ]
   *             "NA" = 3 -> [ 0, 7 ]
   *       ),
   *       1 = "B" -> Map (
   *             "y" = 0 -> [ 2, 8 ],
   *             "n" = 1 -> [ 3, 8 ],
   *             "maybe" = 2 -> [ 3, 8 ]
   *             "NA" = 3 -> [ 0, 8 ]
   *       ),
   *       ...
   *     )
   */
  private Map<Integer, Map<Integer, double[]>> _encodingMap = new HashMap<>();
  private Map<Integer, Double> priors = new HashMap<>();
  private int _nclasses; // 1: regression, 2: binary, 2+: multiclass

  public EncodingMap(int nclasses) {
    _nclasses = nclasses;
  }

  public double[] getNumDen(int category) {
    Map<Integer, double[]> targetMap = _encodingMap.get(category);
    assert _nclasses == 1 || _nclasses == 2;
    assert targetMap.size() == 1;
    return targetMap.get(NO_TARGET_CLASS);
  }
  
  public double[] getNumDen(int category, int targetClass) {
    Map<Integer, double[]> targetMap = _encodingMap.get(category);
    assert _nclasses > 2;
    assert targetMap.size() > 1;
    return targetMap.get(targetClass);
  }
  
  public int getNACategory() {
    return _encodingMap.size() - 1;
  }
  
  public void add(int categorical, double[] encodingComponents) {
    if (_nclasses <= 2) { // regression + binary
      assert encodingComponents.length == 2;
      _encodingMap.put(categorical, Collections.singletonMap(NO_TARGET_CLASS, encodingComponents));
    } else { // multiclass
      assert encodingComponents.length == 3;
      if (!_encodingMap.containsKey(categorical))
        _encodingMap.put(categorical, new HashMap<Integer, double[]>());
      
      Integer targetClass = (int)encodingComponents[encodingComponents.length-1];
      double[] numDen = Arrays.copyOf(encodingComponents, 2);
      _encodingMap.get(categorical).put(targetClass, numDen);
    }
  }
  
  public double getPriorMean() {
    assert _nclasses == 1 || _nclasses == 2;
    if (!priors.containsKey(NO_TARGET_CLASS)) {
      priors.put(NO_TARGET_CLASS, doComputePriorMean(NO_TARGET_CLASS));
    }
    return priors.get(NO_TARGET_CLASS);
  }
  
  public double getPriorMean(int targetClass) {
    assert _nclasses > 2;
    assert targetClass >= 0 && targetClass < _nclasses;
    if (!priors.containsKey(targetClass)) {
      priors.put(targetClass, doComputePriorMean(targetClass));
    }
    return priors.get(targetClass);
  }
  
  private double doComputePriorMean(int targetClass) {
    double num = 0;
    double den = 0;
    for (Map<Integer, double[]> targetMapping : _encodingMap.values()) {
        double[] numDen = targetMapping.get(targetClass);
        num += numDen[0];
        den += numDen[1];
    }
    return num/den;
  }

}
