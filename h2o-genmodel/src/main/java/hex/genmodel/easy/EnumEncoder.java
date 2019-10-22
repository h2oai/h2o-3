package hex.genmodel.easy;

import java.util.HashMap;
import java.util.Map;

public class EnumEncoder implements CategoricalEncoder {

  private final String columnName;
  private final int targetIndex;
  private final Map<String, Integer> domainMap;

  public EnumEncoder(String columnName, int targetIndex, String[] domainValues) {
    this.columnName = columnName;
    this.targetIndex = targetIndex;
    domainMap = new HashMap<>();
    for (int j = 0; j < domainValues.length; j++) {
      domainMap.put(domainValues[j], j);
    }
    
  }

  @Override
  public boolean encodeCatValue(String levelName, double[] rawData) {
    Integer levelIndex = domainMap.get(levelName);
    if (levelIndex == null) {
      levelIndex = domainMap.get(columnName + "." + levelName);
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

}
