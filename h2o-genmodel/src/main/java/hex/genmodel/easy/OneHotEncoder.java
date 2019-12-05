package hex.genmodel.easy;

import java.util.HashMap;
import java.util.Map;

public class OneHotEncoder implements CategoricalEncoder {

  private final String columnName;
  private final int targetIndex;
  private final Map<String, Integer> domainMap;

  OneHotEncoder(String columnName, int targetIndex, String[] domainValues) {
    this.columnName = columnName;
    this.targetIndex = targetIndex;
    domainMap = new HashMap<>(domainValues.length);
    for (int j = 0; j < domainValues.length; j++) {
      domainMap.put(domainValues[j], j);
    }
  }

  @Override
  public boolean encodeCatValue(String levelName, double[] rawData) {
    Integer levelIndex = domainMap.get(levelName);
    if (levelIndex == null)
      return false;
    makeHot(levelIndex, rawData);
    return true;
  }

  @Override
  public void encodeNA(double[] rawData) {
    makeHot(domainMap.size(), rawData);
  }

  private void makeHot(int index, double[] rawData) {
    for (int i = 0; i < domainMap.size() + 1; i++) {
      rawData[targetIndex + i] = index == i ? 1 : 0;
    }
  }

  @Override
  public String toString() {
    return "OneHotEncoder{" +
            "columnName='" + columnName + '\'' +
            ", targetIndex=" + targetIndex +
            ", domainMap=" + domainMap +
            '}';
  }

}
