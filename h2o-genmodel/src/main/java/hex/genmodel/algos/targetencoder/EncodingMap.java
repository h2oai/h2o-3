package hex.genmodel.algos.targetencoder;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EncodingMap implements Serializable {

  /**
   * Represents mapping from categorical level index to  2-elements array of `numerator` and `denominator` 
   * which are supposed to be used for calculation of response frequencies.
   * Note that last index is reserved for NA level and we rely on this fact.
   * 
   *  Example:
   *     Map ( 
   *        0 which represents "A" -> [ 4, 7 ],                         
   *        1 which represents "B" -> [ 2, 8 ],                         
   *        2 which represents "C" -> [ 7, 12 ],                         
   *        3 which represents "COL_NAME_NA" -> [ 5, 6 ],                         
   *      )                        
   */                                       
  Map<Integer, double[]> _encodingMap;

  public EncodingMap(Map<Integer, double[]> encodingMap) {
    _encodingMap = encodingMap;
  }

  public EncodingMap() {
    _encodingMap = new HashMap<>();
  }

  public Set<Map.Entry<Integer, double[]>> entrySet() {
    return _encodingMap.entrySet();
  }

  public double[] get(int categoricalFactor) {
    return _encodingMap.get(categoricalFactor);
  }
  
  public double[] put(int categoricalFactor, double[] encodingComponents) {
    return _encodingMap.put(categoricalFactor, encodingComponents);
  }
}
