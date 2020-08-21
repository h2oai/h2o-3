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
    // non-categorical
    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      if (origDomainValues[i] != null)
        continue;
      columnMapping.put(origNames[i], pos);
      pos++;
    }
    // categorical
    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      String[] domainValues = origDomainValues[i];
      if (domainValues == null)
        continue;
      columnMapping.put(origNames[i], pos);
      pos += domainValues.length + 1;
    }
    return columnMapping;
  }
}
