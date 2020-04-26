package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;
import java.util.Map;

public class EigenEncoderDomainMapConstructor extends DomainMapConstructor {
  
  public EigenEncoderDomainMapConstructor(GenModel m, Map<String, Integer> columnNameToIndex) {
    super(m, columnNameToIndex);
  }

  @Override
  public Map<Integer, CategoricalEncoder> create() {
    Map<Integer, CategoricalEncoder> domainMap = new HashMap<>();
    String[] columnNames = _m.getOrigNames();
    int pos = 0;
    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      String colName = columnNames[i];
      Integer colIndex = _columnNameToIndex.get(colName);
      String[] domainValues = _m.getOrigDomainValues()[i];
      if (domainValues != null) {
        double[] targetProjectionArray = new double[domainValues.length];
        System.arraycopy(_m.getOrigProjectionArray() , pos, targetProjectionArray, 0, domainValues.length);
        pos += domainValues.length;
        domainMap.put(colIndex, new EigenEncoder(colName, colIndex, domainValues, targetProjectionArray));
      }
    }
    return domainMap;
  }
}
