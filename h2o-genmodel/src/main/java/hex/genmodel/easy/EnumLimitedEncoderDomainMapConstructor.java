package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;
import java.util.Map;

public class EnumLimitedEncoderDomainMapConstructor extends DomainMapConstructor {

  public EnumLimitedEncoderDomainMapConstructor(GenModel m, Map<String, Integer> columnNameToIndex) {
    super(m, columnNameToIndex);
  }

  @Override
  public Map<Integer, CategoricalEncoder> create() {
    Map<Integer, CategoricalEncoder> domainMap = new HashMap<>();
    String[][] newDomainValues = _m.getDomainValues();
    String[] columnNames = _m.getOrigNames();
    String[][] domainValues = _m.getOrigDomainValues();
    
    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      String[] colDomainValues = domainValues[i];
      int colIndex = _columnNameToIndex.get(columnNames[i]);
      if (colDomainValues != null) {
        domainMap.put(colIndex, new EnumLimitedEncoder(columnNames[i], colIndex, colDomainValues, newDomainValues[i]));
      }
    }
    return domainMap;
  }
}
