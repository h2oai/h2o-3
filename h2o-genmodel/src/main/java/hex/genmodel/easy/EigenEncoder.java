package hex.genmodel.easy;

import java.util.HashMap;
import java.util.Map;

public class EigenEncoder implements CategoricalEncoder {

  private final String columnName;
  private final int targetIndex;
  private final Map<String, Integer> domainMap;
  private final double[] projectionEigenVec;
  
  public EigenEncoder(String columnName, int targetIndex, String[] domainValues, double[] projectionEigenVec) {
    this.columnName = columnName;
    this.targetIndex = targetIndex;
    domainMap = new HashMap<>();
    for (int j = 0; j < domainValues.length; j++) {
      domainMap.put(domainValues[j], j);
    }
    this.projectionEigenVec = projectionEigenVec;
  }
  
  @Override
  public boolean encodeCatValue(String levelName, double[] rawData) {
    Integer levelIndex = domainMap.get(levelName);
    if (levelIndex == null)
      return false;
    rawData[targetIndex] = (float) this.projectionEigenVec[levelIndex]; //make it more reproducible by casting to float
    return true;
  }

  @Override
  public void encodeNA(double[] rawData) {
    rawData[targetIndex] = Double.NaN;
  }
}
