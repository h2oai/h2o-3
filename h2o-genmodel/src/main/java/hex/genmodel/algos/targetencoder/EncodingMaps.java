package hex.genmodel.algos.targetencoder;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// XXX: what's the point of this class?
public class EncodingMaps implements Serializable {

  /**
   * Outer Map stores encoding maps for each categorical column.
   * Inner Map (encoding map) represents mapping from categorical level index to array of `numerator` and `denominator` 
   * which are supposed to be used for calculation of response frequencies.
   * 
   *  Example:
   *    Map( "categorical_col_name_1" ->  EncodingMap ( 
   *                                        0 which represents "A" -> [ 4, 7 ], 
   *                                        1 which represents "B" -> [ 2, 8 ], 
   *                                        2 which represents "C" -> [ 5, 6 ], 
   *                                        3 which represents "categorical_col_name_1_NA" -> [ 4, 5 ], 
   *                                      )
   *    Map( "categorical_col_name_2" ->  EncodingMap ( 
   *                                       0 which represents "red" -> [ 2, 2 ], 
   *                                       1 which represents "green" -> [ 3, 9 ]
   *                                       2 which represents "categorical_col_name_2_NA" -> [ 5, 8 ]
   *                                     )
   */                                       
  private Map<String, EncodingMap> _encodingMaps;
  
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
