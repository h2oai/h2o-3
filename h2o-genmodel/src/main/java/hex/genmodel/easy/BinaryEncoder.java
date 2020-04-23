package hex.genmodel.easy;

import hex.genmodel.utils.MathUtils;

import java.util.HashMap;
import java.util.Map;

public class BinaryEncoder implements CategoricalEncoder {

  private final String columnName;
  private final int targetIndex;
  private final Map<String, Integer> domainMap;
  private final int binaryCategorySizes;

  BinaryEncoder(String columnName, int targetIndex, String[] domainValues) {
    this.columnName = columnName;
    this.targetIndex = targetIndex;
    this.binaryCategorySizes = 1 + MathUtils.log2(domainValues.length - 1 + 1/* for NAs */);
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
    makeBinary(levelIndex, rawData);
    return true;
  }

  @Override
  public void encodeNA(double[] rawData) {
    makeBinary(-1, rawData);
  }

  private void makeBinary(int index, double[] rawData) {
    long val = index + 1; //0 is used for NA
    for (int i = 0; i < binaryCategorySizes; i++) {
      rawData[targetIndex + i] = val & 1;
      val >>>= 1;
    }
  }

  @Override
  public String toString() {
    return "BinaryEncoder{" +
            "columnName='" + columnName + '\'' +
            ", targetIndex=" + targetIndex +
            ", domainMap=" + domainMap +
            '}';
  }

}
