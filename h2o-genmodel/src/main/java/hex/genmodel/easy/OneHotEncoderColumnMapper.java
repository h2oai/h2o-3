package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;
import java.util.Map;

public class OneHotEncoderColumnMapper {

  private final GenModel _m;

  public OneHotEncoderColumnMapper(GenModel m) {
    _m = m;
  }

  public Map<String, Integer> create() {
    String[] origNames = _m.getOrigNames();
    String[][] origDomainValues = _m.getOrigDomainValues();
    Map<String, Integer> columnMapping = new HashMap<>(origNames.length);
    int pos = 0;
    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      String colName = origNames[i];
      columnMapping.put(colName, pos);
      String[] domainValues = origDomainValues[i];
      pos += domainValues != null ? domainValues.length + 1 : 1; 
    }
    return columnMapping;
  }
}
