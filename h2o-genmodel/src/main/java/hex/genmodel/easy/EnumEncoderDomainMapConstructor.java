package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;
import java.util.Map;

public class EnumEncoderDomainMapConstructor extends DomainMapConstructor {
  
  public EnumEncoderDomainMapConstructor(GenModel m, Map<String, Integer> columnNameToIndex) {
    super(m, columnNameToIndex);
  }

  @Override
  public Map<Integer, CategoricalEncoder> create() {
    Map<Integer, CategoricalEncoder> domainMap = new HashMap<>();
    String[] columnNames = _m.getNames();
    for (int i = 0; i < columnNames.length; i++) {
      String colName = columnNames[i];
      Integer colIndex = _columnNameToIndex.get(colName);
      String[] domainValues = _m.getDomainValues(i);
      if (domainValues != null) {
        domainMap.put(colIndex, new EnumEncoder(colName, colIndex, domainValues));
      }
    }
    return domainMap;
  }
}
