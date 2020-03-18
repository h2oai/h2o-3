package hex.genmodel.easy;

import java.util.HashMap;
import java.util.Map;

public class EnumLimitedEncoder implements CategoricalEncoder {

  private final String columnName;
  private final int targetIndex;
  private final Map<String, Integer> domainMap = new HashMap<>();
  
  EnumLimitedEncoder(String columnName, int targetIndex, String[] domainValues, String[] newDomainValues) {
    this.columnName = columnName;
    this.targetIndex = targetIndex;
    
    for (int j = 0; j < newDomainValues.length; j++) {
      domainMap.put(newDomainValues[j],j);
    }
    if (domainMap.containsKey("other")) {
      Integer otherIndex = domainMap.get("other");
      for (int j = 0; j < domainValues.length; j++) {
        if (!domainMap.containsKey(domainValues[j])) {
          domainMap.put(domainValues[j], otherIndex);
        }
      }
      domainMap.remove("other");
    }
  }

  @Override 
  public boolean encodeCatValue(String levelName, double[] rawData) {
    Integer levelIndex = domainMap.get(levelName);
    if (levelIndex == null) {
      levelIndex = domainMap.get(columnName + "." + "top_" + levelName + "_levels");
    }
    if (levelIndex == null)
      return false;
    
    rawData[targetIndex] = levelIndex;
    return true;
  }

  @Override
  public void encodeNA(double[] rawData) {
    rawData[targetIndex] = Double.NaN;
  }

  @Override
  public String toString() {
    return "EnumLimited{" +
            "columnName='" + columnName + '\'' +
            ", targetIndex=" + targetIndex +
            ", domainMap=" + domainMap +
            '}';
  }
}
