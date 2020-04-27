package hex.genmodel.easy;

import java.util.HashMap;
import java.util.Map;

public class LabelEncoder implements CategoricalEncoder {
  
  private final int targetIndex;
  private final Map<String, Integer> domainMap;

  public LabelEncoder(int targetIndex, String[] domainValues) {
    this.targetIndex = targetIndex;
    domainMap = new HashMap<>();
    for (int j = 0; j < domainValues.length; j++) {
      domainMap.put(domainValues[j], j);
    }
  }

  @Override
  public boolean encodeCatValue(String levelName, double[] rawData) {
    Integer levelIndex = domainMap.get(levelName);
    if (levelIndex == null)
      return false;
    // check if the 1st lvl of the domain can be parsed as int
    boolean useDomain = false;
    try {
      Integer.parseInt(levelName);
      useDomain = true;
    } catch (NumberFormatException ex) {
    }
    if (useDomain) {
      rawData[targetIndex] = Integer.parseInt(levelName);
    } else {
      rawData[targetIndex] = levelIndex;
    }
    return true;
  }

  @Override
  public void encodeNA(double[] rawData) {
    rawData[targetIndex] = Double.NaN;
  }

}
