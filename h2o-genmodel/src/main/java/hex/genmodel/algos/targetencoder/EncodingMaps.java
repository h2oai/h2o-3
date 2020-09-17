package hex.genmodel.algos.targetencoder;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

public class EncodingMaps implements Iterable<Entry<String, EncodingMap>>, Serializable {

  /**
   * Outer Map stores encoding maps for each categorical column.
   * 
   *  Example:
   *    Map( "categorical_col_name_1" ->  EncodingMap ( ... )
   *    Map( "categorical_col_name_2" ->  EncodingMap ( ... )
   */                                       
  private Map<String, EncodingMap> _encodingMaps;
  
  public EncodingMaps(Map<String, EncodingMap> encodingMaps) {
    _encodingMaps = encodingMaps;
  }

  public EncodingMaps() {
    _encodingMaps = new HashMap<>();
  }

  public EncodingMap get(String categoricalCol) {
    return _encodingMaps.get(categoricalCol);
  } 
  
  public EncodingMap put(String categoricalCol, EncodingMap encodingMap) {
    return _encodingMaps.put(categoricalCol, encodingMap);
  } 
  
  public Map<String, EncodingMap> encodingMap() {
    return _encodingMaps;
  }
  
  public Set<String> getColumns() {
    return Collections.unmodifiableSet(_encodingMaps.keySet());
  }

  @Override
  public Iterator<Entry<String, EncodingMap>> iterator() {
    return _encodingMaps.entrySet().iterator();
  }
}
