package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;
import java.util.Map;

/**
 *  Create map from input variable domain information.
 */
public class DomainMapConstructor {

  private final GenModel _m;
  
  public DomainMapConstructor(GenModel m) {
    _m = m;
  }

  public Map<Integer, CategoricalEncoder> create() {
    Map<Integer, CategoricalEncoder> domainMap = new HashMap<>();
    String[] columnNames = _m.getNames();
    for (int i = 0; i < _m.getNumCols(); i++) {
      String[] domainValues = _m.getDomainValues(i);
      if (domainValues != null) {
        domainMap.put(i, new EnumEncoder(columnNames[i], i, domainValues));
      }
    }
    return domainMap;
  }

}
