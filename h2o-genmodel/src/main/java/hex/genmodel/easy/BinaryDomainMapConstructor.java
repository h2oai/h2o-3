package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;
import java.util.Map;

public class BinaryDomainMapConstructor extends DomainMapConstructor {

  public BinaryDomainMapConstructor(GenModel m, Map<String, Integer> columnNameToIndex) {
    super(m, columnNameToIndex);
  }

  @Override
  public Map<Integer, CategoricalEncoder> create() {
    Map<Integer, CategoricalEncoder> domainMap = new HashMap<>();

    String[] columnNames = _m.getOrigNames();
    String[][] domainValues = _m.getOrigDomainValues();

    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      String[] colDomainValues = domainValues[i];
      if (colDomainValues != null) {
        int targetOffsetIndex = _columnNameToIndex.get(columnNames[i]);
        domainMap.put(targetOffsetIndex, new BinaryEncoder(columnNames[i], targetOffsetIndex, colDomainValues));
      }
    }
    return domainMap;
  }
}
