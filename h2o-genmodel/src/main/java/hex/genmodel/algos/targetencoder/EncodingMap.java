package hex.genmodel.algos.targetencoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EncodingMap {

  /**
   * Represents mapping from categorical level to  2-elements array of `numerator` and `denominator` 
   * which are supposed to be used for calculation of response frequencies.
   * 
   *  Example:
   *     Map ( 
   *        "A" -> [ 4, 7 ],                         
   *        "B" -> [ 2, 8 ],                         
   *        "C" -> [ 5, 6 ],                         
   *      )                        
   */                                       
  Map<String, int[]> _encodingMap = null;

  public EncodingMap(Map<String, int[]> encodingMap) {
    _encodingMap = encodingMap;
  }

  public EncodingMap() {
    _encodingMap = new HashMap<>();
  }

  public Set<Map.Entry<String, int[]>> entrySet() {
    return _encodingMap.entrySet();
  }

  public int[] get(String categoricalLevel) {
    return _encodingMap.get(categoricalLevel);
  }
  
  public int[] put(String categoricalLevel, int[] encodingComponents) {
    return _encodingMap.put(categoricalLevel, encodingComponents);
  }
}
