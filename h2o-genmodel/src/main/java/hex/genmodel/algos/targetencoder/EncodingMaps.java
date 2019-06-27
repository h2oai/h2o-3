package hex.genmodel.algos.targetencoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EncodingMaps {

  /**
   * Outer Map stores encoding maps for each categorical column.
   * Inner Map (encoding map) represents mapping from categorical level to array of `numerator` and `denominator` 
   * which are supposed to be used for calculation of response frequencies.
   * 
   *  Example:
   *    Map( "categorical_col_name_1" ->  EncodingMap ( 
   *                                        "A" -> [ 4, 7 ], 
   *                                        "B" -> [ 2, 8 ], 
   *                                        "C" -> [ 5, 6 ], 
   *                                      )
   *    Map( "categorical_col_name_2" ->  EncodingMap ( 
   *                                       "red" -> [ 2, 2 ], 
   *                                       "green" -> [ 3, 9 ]
   *                                     )
   */                                       
  private Map<String, EncodingMap> _encodingMaps = null;
  
  public EncodingMaps(Map<String, EncodingMap> encodingMaps) {
    _encodingMaps = encodingMaps;
  }

  public EncodingMaps() {
    _encodingMaps = new HashMap<>();
  }

  public EncodingMap get(String categoricalColumnName) {
    return _encodingMaps.get(categoricalColumnName);
  } 
  
  public EncodingMap put(String categoricalColName, EncodingMap em) {
    return _encodingMaps.put(categoricalColName, em);
  } 
  
  public Set<Map.Entry<String, EncodingMap>> entrySet() {
    return _encodingMaps.entrySet();
  }
  
  public Map<String, EncodingMap> encodingMap() {
    return _encodingMaps;
  }
  
  public boolean containsKey(String key) {
    return _encodingMaps.containsKey(key);
  }
  
}
