package hex.genmodel;

import hex.genmodel.easy.RowData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TETransformer extends GenTransformer{

  public Map<String, Map<String, int[]>> encodingMap;
  public Map<String, Map<String, int[]>> transformerDescriptor; // TODO all meta information about column names and indexes could come from Descriptor

  public void setTeColumnIdxs(int[] teColumnIdxs) {
    this.teColumnIdxs = teColumnIdxs;
  }

  public int[] teColumnIdxs;
  public String[] teColumnNamesOrder;


  public TETransformer(Map<String, Map<String, int[]>> encodingMap) {
    this.encodingMap = encodingMap;
    this.teColumnNamesOrder = encodingMap.keySet().toArray(new String[0]);
  }
  

  @Override
  public double[] transform(double[] row, double[] transformedData) {
    int indexOfTEColumn = 0;
    for (String teColumnName : teColumnNamesOrder) {
      int positionOfTEValueInRow = teColumnIdxs[indexOfTEColumn];
//      Map<String, int[]> encodings = columnToEncodingsMap.getValue();
      int[] correspondingNumAndDen = encodings.get(originalValue);
      double calculatedFrequency = (double) correspondingNumAndDen[0] / correspondingNumAndDen[1];
      transformedData[indexOfTEColumn] = calculatedFrequency;
      indexOfTEColumn ++;
    }
  }


  void transformWithTargetEncoding(RowData data) {
    Map<String, Map<String, int[]>> targetEncodingMap = encodingMap;
    if(targetEncodingMap != null) {
      for (Map.Entry<String, Map<String, int[]>> columnToEncodingsMap : targetEncodingMap.entrySet()) {
        String columnName = columnToEncodingsMap.getKey();
        String originalValue = (String) data.get(columnName);
        Map<String, int[]> encodings = columnToEncodingsMap.getValue();
        int[] correspondingNumAndDen = encodings.get(originalValue);
        double calculatedFrequency = (double) correspondingNumAndDen[0] / correspondingNumAndDen[1];
        data.put(columnName + "_te", calculatedFrequency);
      }
    }

  }

}
