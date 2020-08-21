package hex.genmodel.easy;

import hex.genmodel.GenModel;
import hex.genmodel.utils.MathUtils;

import java.util.HashMap;
import java.util.Map;

public class BinaryColumnMapper {

  private final GenModel _m;

  public BinaryColumnMapper(GenModel m) {
    _m = m;
  }

  public Map<String, Integer> create() {
    String[] origNames = _m.getOrigNames();
    String[][] origDomainValues = _m.getOrigDomainValues();
    Map<String, Integer> columnMapping = new HashMap<>(origNames.length);
    int pos = 0;
    // non-categorical
    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      if (origDomainValues[i] == null) {
        columnMapping.put(origNames[i], pos);
        pos++;
      }
    }
    // categorical
    for (int i = 0; i < _m.getOrigNumCols(); i++) {
      String[] domainValues = origDomainValues[i];
      if (domainValues != null) {
        columnMapping.put(origNames[i], pos);
        pos += 1 + MathUtils.log2(domainValues.length - 1 + 1/* for NAs */);
      }
    }
    return columnMapping;
  }
}
