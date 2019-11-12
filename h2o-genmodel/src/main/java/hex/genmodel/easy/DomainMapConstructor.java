package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;

/**
 *  Create map from input variable domain information.
 */
public class DomainMapConstructor {

  public final GenModel _m;
  
  public DomainMapConstructor(GenModel m) {
    _m = m;
  }

  public HashMap<Integer, HashMap<String, Integer>> create() {
   
    // This contains the categorical string to numeric mapping.
    HashMap<Integer, HashMap<String, Integer>> domainMap = new HashMap<>();
    for (int i = 0; i < _m.getNumCols(); i++) {
      String[] domainValues = _m.getDomainValues(i);
      if (domainValues != null) {
        HashMap<String, Integer> m = new HashMap<>();
        for (int j = 0; j < domainValues.length; j++) {
          m.put(domainValues[j], j);
        }

        domainMap.put(i, m);
      }
    }
    return domainMap;
  }
}
